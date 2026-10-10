package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class FetchedVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val description: String = "",
    val duration: String = "Video",
    val thumbnailUrl: String = ""
)

data class FetchedPlaylistResult(
    val playlistId: String,
    val title: String,
    val videos: List<FetchedVideo>
)

data class FetchedChannelResult(
    val channelId: String,
    val channelName: String,
    val handle: String,
    val videos: List<FetchedVideo>
)

object YouTubeDataFetcher {

    private const val TAG = "YouTubeDataFetcher"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Fetch all videos and title from a YouTube playlist (public or unlisted)
     */
    suspend fun fetchPlaylist(rawInput: String): FetchedPlaylistResult = withContext(Dispatchers.IO) {
        val cleanPlaylistId = extractPlaylistId(rawInput)
        val videos = mutableListOf<FetchedVideo>()
        var playlistTitle = "Playlist"

        // 1. Try Atom RSS Feed first
        try {
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=$cleanPlaylistId"
            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string().orEmpty()
                if (xml.contains("<entry>")) {
                    playlistTitle = extractTag(xml, "title")?.replace(" - YouTube", "") ?: "Playlist"
                    val parsed = parseXmlEntries(xml)
                    if (parsed.isNotEmpty()) {
                        videos.addAll(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "RSS fetch failed for playlist: ${e.message}")
        }

        // 2. If RSS didn't return videos (e.g. unlisted or restricted RSS), scrape playlist webpage HTML
        if (videos.isEmpty()) {
            try {
                val webUrl = "https://www.youtube.com/playlist?list=$cleanPlaylistId"
                val request = Request.Builder()
                    .url(webUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()

                    // Extract Playlist Title
                    val titleMatcher = Pattern.compile("<meta property=\"og:title\" content=\"(.*?)\">").matcher(html)
                    if (titleMatcher.find()) {
                        playlistTitle = unescapeHtml(titleMatcher.group(1))
                    } else {
                        val simpleTitle = Pattern.compile("<title>(.*?)</title>").matcher(html)
                        if (simpleTitle.find()) {
                            playlistTitle = unescapeHtml(simpleTitle.group(1)).replace(" - YouTube", "").trim()
                        }
                    }

                    // Extract videos from ytInitialData
                    val scrapedVideos = scrapeVideosFromHtml(html, playlistTitle)
                    videos.addAll(scrapedVideos)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Web scrape failed for playlist: ${e.message}")
            }
        }

        FetchedPlaylistResult(
            playlistId = cleanPlaylistId,
            title = playlistTitle.ifBlank { "YouTube Playlist" },
            videos = videos
        )
    }

    /**
     * Fetch channel details and recent videos from Channel URL, @handle, or Channel ID
     */
    suspend fun fetchChannel(rawInput: String): FetchedChannelResult = withContext(Dispatchers.IO) {
        val input = rawInput.trim()
        var channelId = ""
        var channelName = ""
        var handle = ""
        val videos = mutableListOf<FetchedVideo>()

        // 1. Determine input format
        if (input.matches(Regex("^UC[a-zA-Z0-9_-]{22}$"))) {
            channelId = input
        } else if (input.contains("/channel/UC")) {
            val m = Pattern.compile("channel\\/(UC[a-zA-Z0-9_-]{22})").matcher(input)
            if (m.find()) {
                channelId = m.group(1) ?: ""
            }
        }

        // If not direct channel ID, resolve handle or channel page
        if (channelId.isBlank()) {
            val channelUrl = when {
                input.startsWith("http") -> input
                input.startsWith("@") -> "https://www.youtube.com/$input"
                else -> "https://www.youtube.com/@$input"
            }

            try {
                val request = Request.Builder()
                    .url(channelUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()

                    // Extract channelId
                    val idMatcher = Pattern.compile("(?:channel_id=|\"channelId\":\")(UC[a-zA-Z0-9_-]{22})").matcher(html)
                    if (idMatcher.find()) {
                        channelId = idMatcher.group(1) ?: ""
                    } else {
                        val extIdMatcher = Pattern.compile("\"externalId\":\"(UC[a-zA-Z0-9_-]{22})\"").matcher(html)
                        if (extIdMatcher.find()) {
                            channelId = extIdMatcher.group(1) ?: ""
                        }
                    }

                    // Extract Channel Name
                    val nameMatcher = Pattern.compile("<meta property=\"og:title\" content=\"(.*?)\">").matcher(html)
                    if (nameMatcher.find()) {
                        channelName = unescapeHtml(nameMatcher.group(1))
                    }

                    // Extract handle
                    if (input.contains("@")) {
                        handle = input.substringAfterLast("/").substringBefore("?")
                    }

                    // Scrape direct videos if available on page
                    val directVideos = scrapeVideosFromHtml(html, channelName)
                    videos.addAll(directVideos)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Channel resolution failed: ${e.message}")
            }
        }

        // 2. Fetch official RSS feed with the resolved channelId
        if (channelId.isNotBlank()) {
            try {
                val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
                val request = Request.Builder()
                    .url(rssUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val xml = response.body?.string().orEmpty()
                    if (xml.contains("<entry>")) {
                        if (channelName.isBlank()) {
                            channelName = extractTag(xml, "title")?.replace(" - YouTube", "") ?: "YouTube Channel"
                        }
                        val parsed = parseXmlEntries(xml)
                        if (parsed.isNotEmpty()) {
                            val existingIds = videos.map { it.videoId }.toSet()
                            for (v in parsed) {
                                if (!existingIds.contains(v.videoId)) {
                                    videos.add(v)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Channel RSS fetch failed: ${e.message}")
            }
        }

        if (channelName.isBlank()) {
            channelName = if (handle.isNotBlank()) handle else "Channel $channelId"
        }

        FetchedChannelResult(
            channelId = channelId.ifBlank { "ch_" + System.currentTimeMillis() },
            channelName = channelName,
            handle = handle,
            videos = videos
        )
    }

    /**
     * Fetch video description on demand for any video
     */
    suspend fun fetchVideoDescription(videoId: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string().orEmpty()

                // Try shortDescription in ytInitialPlayerResponse
                val shortDescPattern = Pattern.compile("\"shortDescription\":\"(.*?)\"", Pattern.DOTALL)
                val m1 = shortDescPattern.matcher(html)
                if (m1.find()) {
                    val raw = m1.group(1).orEmpty()
                    return@withContext unescapeJson(raw)
                }

                // Try meta description
                val metaDescPattern = Pattern.compile("<meta property=\"og:description\" content=\"(.*?)\">")
                val m2 = metaDescPattern.matcher(html)
                if (m2.find()) {
                    return@withContext unescapeHtml(m2.group(1))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch description for $videoId: ${e.message}")
        }
        ""
    }

    private fun parseXmlEntries(xml: String): List<FetchedVideo> {
        val result = mutableListOf<FetchedVideo>()
        val entryPattern = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL)
        val matcher = entryPattern.matcher(xml)

        while (matcher.find()) {
            val entryXml = matcher.group(1) ?: continue
            val videoId = extractTag(entryXml, "yt:videoId") ?: continue
            val title = unescapeHtml(extractTag(entryXml, "title").orEmpty())
            val author = unescapeHtml(extractTag(entryXml, "name").orEmpty())
            val desc = unescapeHtml(extractTag(entryXml, "media:description").orEmpty())
            val thumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

            result.add(
                FetchedVideo(
                    videoId = videoId,
                    title = title.ifBlank { "YouTube Video" },
                    channelName = author,
                    description = desc,
                    duration = "Video",
                    thumbnailUrl = thumbnail
                )
            )
        }
        return result
    }

    private fun scrapeVideosFromHtml(html: String, fallbackChannel: String): List<FetchedVideo> {
        val result = mutableListOf<FetchedVideo>()
        val seenIds = mutableSetOf<String>()

        val pattern = Pattern.compile(
            "\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{(?:\"runs\":\\[\\{\"text\":\"(.*?)\"\\}|\"simpleText\":\"(.*?)\")",
            Pattern.DOTALL
        )
        val matcher = pattern.matcher(html)

        while (matcher.find() && result.size < 50) {
            val videoId = matcher.group(1) ?: continue
            if (seenIds.add(videoId)) {
                val title = matcher.group(2) ?: matcher.group(3) ?: "Video #$videoId"
                result.add(
                    FetchedVideo(
                        videoId = videoId,
                        title = unescapeHtml(title),
                        channelName = fallbackChannel,
                        description = "",
                        duration = "Video",
                        thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    )
                )
            }
        }
        return result
    }

    private fun extractTag(xml: String, tagName: String): String? {
        val pattern = Pattern.compile("<$tagName.*?>(.*?)</$tagName>", Pattern.DOTALL)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else {
            null
        }
    }

    private fun extractPlaylistId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("PL") || trimmed.startsWith("UU") || trimmed.startsWith("RD") || trimmed.startsWith("OL")) {
            return trimmed
        }
        val pattern = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)")
        val matcher = pattern.matcher(trimmed)
        return if (matcher.find()) {
            matcher.group(1) ?: trimmed
        } else {
            trimmed
        }
    }

    private fun unescapeHtml(text: String?): String {
        if (text == null) return ""
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("\\u0026", "&")
            .replace("\\\"", "\"")
            .trim()
    }

    private fun unescapeJson(text: String): String {
        return text
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\u0026", "&")
            .trim()
    }
}
