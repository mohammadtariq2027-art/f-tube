package com.example.data.remote

import android.util.Log
import com.example.util.DurationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class FetchedVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val description: String = "",
    val duration: String = "",
    val thumbnailUrl: String = "",
    val uploadDate: String = "",
    val publishedTimestamp: Long = 0L
)

data class FetchedPlaylistResult(
    val playlistId: String,
    val title: String,
    val videos: List<FetchedVideo>
)

data class FetchedChannelResult(
    val channelId: String,
    val channelName: String,
    val handle: String = "",
    val description: String = "",
    val videos: List<FetchedVideo>
)

object YouTubeDataFetcher {

    private const val TAG = "YouTubeDataFetcher"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val DEFAULT_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val DEFAULT_CLIENT_VERSION = "2.20261013.00.00"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Fetch playlist details and ALL videos from a YouTube Playlist link or ID.
     * Extracts initial batch from ytInitialData and paginates through all continuation tokens
     * so that entire playlists (100, 200, 500+ videos) are fully retrieved.
     */
    suspend fun fetchPlaylist(rawInput: String): FetchedPlaylistResult = withContext(Dispatchers.IO) {
        val cleanPlaylistId = extractPlaylistId(rawInput)
        var playlistTitle = "Playlist"
        val videos = mutableListOf<FetchedVideo>()
        val seenVideoIds = mutableSetOf<String>()

        // If input is a channel handle, URL, or UC channel ID, resolve via channel fetcher / uploads playlist
        if (cleanPlaylistId.contains("/@") || cleanPlaylistId.startsWith("@") || cleanPlaylistId.contains("/channel/")) {
            val chResult = fetchChannel(cleanPlaylistId, maxContinuationPages = 35)
            val plId = if (chResult.channelId.startsWith("UC")) "UU" + chResult.channelId.removePrefix("UC") else "ch_" + System.currentTimeMillis()
            return@withContext FetchedPlaylistResult(
                playlistId = plId,
                title = chResult.channelName.ifBlank { "YouTube Playlist" },
                videos = chResult.videos
            )
        }

        val actualPlaylistId = if (cleanPlaylistId.startsWith("UC") && cleanPlaylistId.length == 24) {
            "UU" + cleanPlaylistId.removePrefix("UC")
        } else {
            cleanPlaylistId
        }

        // 1. Scrape YouTube Playlist Webpage
        try {
            val playlistUrl = "https://www.youtube.com/playlist?list=$actualPlaylistId"
            val request = Request.Builder()
                .url(playlistUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string().orEmpty()

                // Extract Playlist Title
                val titleMatcher = Pattern.compile("<meta property=\"og:title\" content=\"(.*?)\">").matcher(html)
                if (titleMatcher.find()) {
                    val candidate = unescapeHtml(titleMatcher.group(1)).trim()
                    if (candidate.isNotBlank() && !candidate.equals("undefined", ignoreCase = true)) {
                        playlistTitle = candidate
                    }
                }
                if (playlistTitle == "Playlist" || playlistTitle.equals("undefined", ignoreCase = true)) {
                    val simpleTitle = Pattern.compile("<title>(.*?)</title>").matcher(html)
                    if (simpleTitle.find()) {
                        val candidate = unescapeHtml(simpleTitle.group(1)).replace(" - YouTube", "").trim()
                        if (candidate.isNotBlank() && !candidate.equals("undefined", ignoreCase = true)) {
                            playlistTitle = candidate
                        }
                    }
                }

                val apiKey = extractInnertubeApiKey(html).ifBlank { DEFAULT_API_KEY }
                val clientVersion = extractClientVersion(html).ifBlank { DEFAULT_CLIENT_VERSION }

                // Extract initial batch of videos & continuation token from ytInitialData
                var nextContinuationToken: String? = null
                val ytJsonStr = extractYtInitialDataJson(html)
                if (!ytJsonStr.isNullOrBlank()) {
                    try {
                        val initJson = JSONObject(ytJsonStr)
                        nextContinuationToken = parseVideosAndNextToken(initJson, playlistTitle, seenVideoIds, videos)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse playlist ytInitialData JSON: ${e.message}")
                    }
                }

                // If ytInitialData JSON extraction didn't populate videos, try direct block parser
                if (videos.isEmpty()) {
                    parseBlocksFromHtml(html, playlistTitle, seenVideoIds, videos)
                    if (nextContinuationToken.isNullOrBlank()) {
                        nextContinuationToken = extractContinuationTokenFallback(html)
                    }
                }

                // Follow continuation tokens to fetch all remaining pages (100 videos per page)
                var continuationPageCount = 0
                val maxContinuationPages = 35 // up to 3500+ videos
                var consecutiveZeroPages = 0

                while (!nextContinuationToken.isNullOrBlank() && continuationPageCount < maxContinuationPages) {
                    continuationPageCount++
                    val countBefore = videos.size
                    try {
                        val contResult = fetchBrowseContinuation(nextContinuationToken, apiKey, clientVersion)
                        if (contResult.isNotBlank()) {
                            val contJson = JSONObject(contResult)
                            val tokenBefore = nextContinuationToken
                            nextContinuationToken = parseVideosAndNextToken(contJson, playlistTitle, seenVideoIds, videos)
                            val added = videos.size - countBefore
                            if (added == 0) {
                                consecutiveZeroPages++
                                if (consecutiveZeroPages >= 2 || nextContinuationToken.isNullOrBlank() || nextContinuationToken == tokenBefore) {
                                    break
                                }
                            } else {
                                consecutiveZeroPages = 0
                            }
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Playlist continuation page $continuationPageCount failed: ${e.message}")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Web scrape failed for playlist $actualPlaylistId: ${e.message}")
        }

        // 2. Fallback: If web scraping yielded 0 videos, fallback to Atom RSS Feed (returns 15)
        if (videos.isEmpty()) {
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
                        if (playlistTitle == "Playlist") {
                            playlistTitle = extractTag(xml, "title")?.replace(" - YouTube", "") ?: "Playlist"
                        }
                        val parsed = parseXmlEntries(xml)
                        for (v in parsed) {
                            if (seenVideoIds.add(v.videoId)) {
                                videos.add(v)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "RSS fallback failed for playlist: ${e.message}")
            }
        }

        FetchedPlaylistResult(
            playlistId = cleanPlaylistId,
            title = if (playlistTitle.isBlank() || playlistTitle.equals("undefined", ignoreCase = true)) "YouTube Playlist" else playlistTitle,
            videos = videos
        )
    }

    /**
     * Fetch channel details and ALL videos from Channel URL, @handle, or Channel ID.
     * Parses the channel's `/videos` tab and follows continuation tokens
     * so that all uploaded videos are loaded into the app.
     */
    suspend fun fetchChannel(rawInput: String, maxContinuationPages: Int = 35): FetchedChannelResult = withContext(Dispatchers.IO) {
        val input = rawInput.trim()
        var channelId = ""
        var channelName = ""
        var handle = ""
        var description = ""
        val videos = mutableListOf<FetchedVideo>()
        val seenVideoIds = mutableSetOf<String>()

        // 1. Determine input format
        if (input.matches(Regex("^UC[a-zA-Z0-9_-]{22}$"))) {
            channelId = input
        } else if (input.contains("/channel/UC")) {
            val m = Pattern.compile("channel\\/(UC[a-zA-Z0-9_-]{22})").matcher(input)
            if (m.find()) {
                channelId = m.group(1) ?: ""
            }
        }

        if (input.contains("@")) {
            val h = input.substringAfterLast("/").substringBefore("?").substringBefore("/")
            handle = if (h.startsWith("@")) h else "@$h"
        }

        // If input is a search term or plain channel name with spaces or not a direct link
        if (channelId.isBlank() && handle.isBlank() && !input.startsWith("http")) {
            val searched = searchChannel(input)
            if (searched != null) {
                if (searched.first.isNotBlank()) channelId = searched.first
                if (searched.second.isNotBlank()) handle = searched.second
            }
        }

        // If channelId is already known, load the full uploads playlist (UU...) directly!
        if (channelId.isNotBlank() && channelId.startsWith("UC")) {
            val uploadsPlaylistId = "UU" + channelId.removePrefix("UC")
            try {
                val uploadsResult = fetchPlaylist(uploadsPlaylistId)
                if (uploadsResult.videos.isNotEmpty()) {
                    for (v in uploadsResult.videos) {
                        if (seenVideoIds.add(v.videoId)) {
                            videos.add(v)
                        }
                    }
                    if (channelName.isBlank()) {
                        channelName = uploadsResult.title
                            .replace(" - YouTube", "")
                            .replace("Uploads from ", "")
                            .trim()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fast uploads playlist fetch failed: ${e.message}")
            }
        }

        // Construct channel /videos tab URL
        val channelVideosUrl = when {
            channelId.isNotBlank() -> "https://www.youtube.com/channel/$channelId/videos"
            handle.isNotBlank() -> "https://www.youtube.com/@${handle.removePrefix("@")}/videos"
            input.startsWith("http") -> {
                val cleanUrl = input.substringBefore("?")
                if (cleanUrl.endsWith("/videos")) cleanUrl else "$cleanUrl/videos"
            }
            else -> "https://www.youtube.com/@${input.replace(" ", "")}/videos"
        }

        // 2. Real-time RSS Feed: If channelId is known and videos is empty, fetch instant upload feed first
        if (channelId.isNotBlank() && videos.isEmpty()) {
            fetchRssVideos(channelId, seenVideoIds, videos).let {
                if (channelName.isBlank() && it.isNotBlank()) channelName = it
            }
        }

        // 3. Scrape channel /videos page to resolve metadata and fetch/paginate full catalog
        try {
            val request = Request.Builder()
                .url(channelVideosUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Cache-Control", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string().orEmpty()

                // Extract channelId if not known
                if (channelId.isBlank()) {
                    val idMatcher = Pattern.compile("(?:channel_id=|\"channelId\":\")(UC[a-zA-Z0-9_-]{22})").matcher(html)
                    if (idMatcher.find()) {
                        channelId = idMatcher.group(1) ?: ""
                    } else {
                        val extIdMatcher = Pattern.compile("\"externalId\":\"(UC[a-zA-Z0-9_-]{22})\"").matcher(html)
                        if (extIdMatcher.find()) {
                            channelId = extIdMatcher.group(1) ?: ""
                        }
                    }
                }

                // Extract canonical handle if not known
                if (handle.isBlank()) {
                    val handleMatcher = Pattern.compile("\"canonicalBaseUrl\":\"(/@[a-zA-Z0-9_.-]+)\"").matcher(html)
                    if (handleMatcher.find()) {
                        handle = handleMatcher.group(1).orEmpty().removePrefix("/")
                    }
                }

                // Extract Channel Name
                if (channelName.isBlank() || channelName.equals("undefined", ignoreCase = true)) {
                    val nameMatcher = Pattern.compile("<meta property=\"og:title\" content=\"(.*?)\">").matcher(html)
                    if (nameMatcher.find()) {
                        channelName = unescapeHtml(nameMatcher.group(1))
                    }
                }

                // Extract Channel Description
                if (description.isBlank()) {
                    val descMatcher = Pattern.compile("<meta (?:property=\"og:description\"|name=\"description\") content=\"(.*?)\">").matcher(html)
                    if (descMatcher.find()) {
                        description = unescapeHtml(descMatcher.group(1))
                    }
                }

                // If channelId was just resolved and we don't have catalog yet, fetch via uploads playlist (100 videos/page)
                if (channelId.isNotBlank() && channelId.startsWith("UC") && videos.size < 30) {
                    try {
                        val uploadsPlaylistId = "UU" + channelId.removePrefix("UC")
                        val uploadsResult = fetchPlaylist(uploadsPlaylistId)
                        if (uploadsResult.videos.isNotEmpty()) {
                            for (v in uploadsResult.videos) {
                                if (seenVideoIds.add(v.videoId)) {
                                    videos.add(v.copy(channelName = v.channelName.ifBlank { channelName }))
                                }
                            }
                            if (channelName.isBlank() || channelName.equals("undefined", ignoreCase = true)) {
                                channelName = uploadsResult.title
                                    .replace(" - YouTube", "")
                                    .replace("Uploads from ", "")
                                    .trim()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Secondary uploads playlist fetch failed: ${e.message}")
                    }
                }

                // If uploads playlist did not return videos, fall back to /videos tab pagination
                if (videos.size < 30) {
                    val apiKey = extractInnertubeApiKey(html).ifBlank { DEFAULT_API_KEY }
                    val clientVersion = extractClientVersion(html).ifBlank { DEFAULT_CLIENT_VERSION }

                    // Extract initial batch of videos & continuation token from ytInitialData
                    var nextContinuationToken: String? = null
                    val ytJsonStr = extractYtInitialDataJson(html)
                    if (!ytJsonStr.isNullOrBlank()) {
                        try {
                            val initJson = JSONObject(ytJsonStr)
                            nextContinuationToken = parseVideosAndNextToken(initJson, channelName, seenVideoIds, videos)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse channel ytInitialData JSON: ${e.message}")
                        }
                    }

                    // Fallback block parser if needed
                    if (videos.isEmpty()) {
                        parseBlocksFromHtml(html, channelName, seenVideoIds, videos)
                        if (nextContinuationToken.isNullOrBlank()) {
                            nextContinuationToken = extractContinuationTokenFallback(html)
                        }
                    }

                    // Follow continuation tokens for channel /videos tab (30 videos per page)
                    var continuationPageCount = 0
                    var consecutiveZeroPages = 0

                    while (!nextContinuationToken.isNullOrBlank() && continuationPageCount < maxContinuationPages) {
                        continuationPageCount++
                        val countBefore = videos.size
                        try {
                            val contResult = fetchBrowseContinuation(nextContinuationToken, apiKey, clientVersion)
                            if (contResult.isNotBlank()) {
                                val contJson = JSONObject(contResult)
                                val tokenBefore = nextContinuationToken
                                nextContinuationToken = parseVideosAndNextToken(contJson, channelName, seenVideoIds, videos)
                                val added = videos.size - countBefore
                                if (added == 0) {
                                    consecutiveZeroPages++
                                    if (consecutiveZeroPages >= 2 || nextContinuationToken.isNullOrBlank() || nextContinuationToken == tokenBefore) {
                                        break
                                    }
                                } else {
                                    consecutiveZeroPages = 0
                                }
                            } else {
                                break
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Channel continuation page $continuationPageCount failed: ${e.message}")
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Channel /videos scrape failed: ${e.message}")
        }

        // 4. Fallback: If 0 videos and channelId is known, try RSS feed
        if (videos.isEmpty() && channelId.isNotBlank()) {
            fetchRssVideos(channelId, seenVideoIds, videos).let {
                if (channelName.isBlank() && it.isNotBlank()) channelName = it
            }
        }

        if (channelName.isBlank()) {
            channelName = if (handle.isNotBlank()) handle else if (channelId.isNotBlank()) "Channel $channelId" else "YouTube Channel"
        }

        // Ensure returned videos are strictly in newest-to-oldest upload date order
        videos.sortByDescending { it.publishedTimestamp }

        FetchedChannelResult(
            channelId = channelId.ifBlank { "ch_" + System.currentTimeMillis() },
            channelName = channelName,
            handle = if (handle.isNotBlank()) (if (handle.startsWith("@")) handle else "@$handle") else "",
            description = description,
            videos = videos
        )
    }

    private fun fetchRssVideos(channelId: String, seenVideoIds: MutableSet<String>, videos: MutableList<FetchedVideo>): String {
        try {
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", USER_AGENT)
                .header("Cache-Control", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string().orEmpty()
                if (xml.contains("<entry>")) {
                    val name = extractTag(xml, "title")?.replace(" - YouTube", "").orEmpty()
                    val parsed = parseXmlEntries(xml)
                    for (v in parsed) {
                        if (seenVideoIds.add(v.videoId)) {
                            videos.add(v)
                        }
                    }
                    return name
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRssVideos failed for $channelId: ${e.message}")
        }
        return ""
    }

    private fun searchChannel(query: String): Pair<String, String>? {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encoded&sp=EgIQAg%253D%253D"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string().orEmpty()
                val mId = Pattern.compile("\"channelRenderer\":\\{\"channelId\":\"(UC[a-zA-Z0-9_-]{22})\"").matcher(html)
                val channelId = if (mId.find()) mId.group(1) ?: "" else ""
                val mHandle = Pattern.compile("\"canonicalBaseUrl\":\"(/@[a-zA-Z0-9_.-]+)\"").matcher(html)
                val handle = if (mHandle.find()) mHandle.group(1).orEmpty().removePrefix("/") else ""
                if (channelId.isNotBlank() || handle.isNotBlank()) {
                    return Pair(channelId, handle)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchChannel failed for '$query': ${e.message}")
        }
        return null
    }

    /**
     * Executes an Innertube browse API POST request with a continuation token
     */
    private fun fetchBrowseContinuation(continuationToken: String, apiKey: String, clientVersion: String): String {
        val decodedToken = decodeToken(continuationToken)
        val payloadObj = JSONObject().apply {
            val contextObj = JSONObject().apply {
                val clientObj = JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", clientVersion)
                    put("hl", "en")
                    put("gl", "US")
                }
                put("client", clientObj)
            }
            put("context", contextObj)
            put("continuation", decodedToken)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payloadObj.toString().toRequestBody(mediaType)

        val url = "https://www.youtube.com/youtubei/v1/browse?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val response = client.newCall(request).execute()
        return if (response.isSuccessful) {
            response.body?.string().orEmpty()
        } else {
            Log.w(TAG, "Browse continuation HTTP error: ${response.code}")
            ""
        }
    }

    /**
     * Recursively traverses a YouTube JSON object (ytInitialData or continuation response),
     * extracting all videos (lockupViewModel, playlistVideoRenderer, videoRenderer)
     * and the next continuation token intended for video pagination.
     */
    private fun parseVideosAndNextToken(
        jsonObj: JSONObject,
        fallbackChannel: String,
        seenVideoIds: MutableSet<String>,
        videos: MutableList<FetchedVideo>
    ): String? {
        var nextToken: String? = null

        fun inspect(obj: Any?) {
            when (obj) {
                is JSONObject -> {
                    // Do not traverse into non-video headers, sidebars, or engagement panels
                    // which contain non-video continuation tokens that overwrite video pagination!
                    if (obj.has("engagementPanel") ||
                        obj.has("showEngagementPanelEndpoint") ||
                        obj.has("pageHeaderRenderer") ||
                        obj.has("commentsHeaderRenderer")
                    ) {
                        return
                    }

                    // 1. Check lockupViewModel (modern YouTube video component)
                    val vm = when {
                        obj.has("lockupViewModel") -> obj.optJSONObject("lockupViewModel")
                        obj.has("richItemRenderer") -> {
                            obj.optJSONObject("richItemRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("lockupViewModel")
                        }
                        else -> null
                    }
                    if (vm != null) {
                        var vid = vm.optString("contentId")
                        if (vid.isBlank()) {
                            vid = vm.optJSONObject("rendererContext")
                                ?.optJSONObject("commandContext")
                                ?.optJSONObject("onTap")
                                ?.optJSONObject("innertubeCommand")
                                ?.optJSONObject("watchEndpoint")
                                ?.optString("videoId").orEmpty()
                        }

                        val accessibilityLabel = vm.optJSONObject("rendererContext")
                            ?.optJSONObject("accessibilityContext")
                            ?.optString("label").orEmpty()

                        var rawTitle = vm.optJSONObject("metadata")
                            ?.optJSONObject("lockupMetadataViewModel")
                            ?.optJSONObject("title")
                            ?.optString("content").orEmpty()
                        if (rawTitle.isBlank()) {
                            rawTitle = accessibilityLabel
                        }

                        // Clean title so duration is NEVER part of the title
                        val title = DurationUtils.cleanVideoTitle(rawTitle)

                        // Accurate Duration Extraction
                        var dur = ""
                        val overlays = vm.optJSONObject("contentImage")
                            ?.optJSONObject("thumbnailViewModel")
                            ?.optJSONArray("overlays")
                        if (overlays != null) {
                            for (i in 0 until overlays.length()) {
                                val overlay = overlays.optJSONObject(i)
                                val badges = overlay?.optJSONObject("thumbnailBottomOverlayViewModel")
                                    ?.optJSONArray("badges")
                                if (badges != null) {
                                    for (j in 0 until badges.length()) {
                                        val badge = badges.optJSONObject(j)
                                        val t = badge?.optJSONObject("thumbnailBadgeViewModel")?.optString("text")
                                        if (!t.isNullOrBlank()) {
                                            dur = t
                                            break
                                        }
                                    }
                                }
                                if (dur.isNotBlank()) break
                            }
                        }
                        // Fallback: parse spoken duration from accessibility label (e.g. "7 minutes, 36 seconds")
                        if (dur.isBlank() && accessibilityLabel.isNotBlank()) {
                            dur = DurationUtils.parseSpokenDuration(accessibilityLabel)
                        }
                        dur = DurationUtils.formatCleanDuration(dur, vid)

                        if (seenVideoIds.contains(vid)) {
                            val idx = videos.indexOfFirst { it.videoId == vid }
                            if (idx != -1 && videos[idx].duration.isBlank() && dur.isNotBlank()) {
                                videos[idx] = videos[idx].copy(duration = dur)
                            }
                        } else if (vid.isNotBlank() && title.isNotBlank() && seenVideoIds.add(vid)) {
                            var uploadDate = ""
                            var publishedTs = 0L
                            val contentMetadata = vm.optJSONObject("metadata")
                                ?.optJSONObject("lockupMetadataViewModel")
                                ?.optJSONObject("metadata")
                                ?.optJSONObject("contentMetadataViewModel")
                            val rows = contentMetadata?.optJSONArray("metadataRows")
                            if (rows != null && rows.length() > 0) {
                                val parts = rows.optJSONObject(0)?.optJSONArray("metadataParts")
                                if (parts != null) {
                                    for (p in 0 until parts.length()) {
                                        val part = parts.optJSONObject(p)
                                        val acc = part?.optString("accessibilityLabel").orEmpty()
                                        val txt = part?.optJSONObject("text")?.optString("content").orEmpty()
                                        val candidate = if (DurationUtils.isDateString(acc)) acc else if (DurationUtils.isDateString(txt)) txt else ""
                                        if (candidate.isNotBlank()) {
                                            val parsed = DurationUtils.parseRelativeTime(candidate)
                                            uploadDate = parsed.first
                                            publishedTs = parsed.second
                                            break
                                        }
                                    }
                                }
                            }
                            if (publishedTs <= 0L) {
                                publishedTs = System.currentTimeMillis() - (videos.size * 3600_000L * 24L)
                            }

                            videos.add(
                                FetchedVideo(
                                    videoId = vid,
                                    title = unescapeHtml(title),
                                    channelName = fallbackChannel,
                                    description = "",
                                    duration = dur,
                                    thumbnailUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                                    uploadDate = uploadDate,
                                    publishedTimestamp = publishedTs
                                )
                            )
                        }
                    }

                    // 2. Check playlistVideoRenderer or videoRenderer (traditional format)
                    val vr = when {
                        obj.has("playlistVideoRenderer") -> obj.optJSONObject("playlistVideoRenderer")
                        obj.has("videoRenderer") -> obj.optJSONObject("videoRenderer")
                        obj.has("richItemRenderer") -> {
                            obj.optJSONObject("richItemRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("videoRenderer")
                        }
                        else -> null
                    }
                    if (vr != null) {
                        val vid = vr.optString("videoId")
                        var rawTitle = ""
                        val titleObj = vr.optJSONObject("title")
                        if (titleObj != null) {
                            val runs = titleObj.optJSONArray("runs")
                            if (runs != null && runs.length() > 0) {
                                rawTitle = runs.optJSONObject(0)?.optString("text").orEmpty()
                            }
                            if (rawTitle.isBlank()) {
                                rawTitle = titleObj.optString("simpleText")
                            }
                        }
                        val title = DurationUtils.cleanVideoTitle(rawTitle)

                        var dur = vr.optJSONObject("lengthText")?.optString("simpleText").orEmpty()
                        if (dur.isBlank()) {
                            val sec = vr.optString("lengthSeconds")
                            sec.toLongOrNull()?.let {
                                dur = DurationUtils.formatSeconds(it)
                            }
                        }
                        dur = DurationUtils.formatCleanDuration(dur, vid)

                        var channel = fallbackChannel
                        val bylineRuns = vr.optJSONObject("shortBylineText")?.optJSONArray("runs")
                        if (bylineRuns != null && bylineRuns.length() > 0) {
                            val ch = bylineRuns.optJSONObject(0)?.optString("text").orEmpty()
                            if (ch.isNotBlank()) channel = unescapeHtml(ch)
                        }

                        if (seenVideoIds.contains(vid)) {
                            val idx = videos.indexOfFirst { it.videoId == vid }
                            if (idx != -1 && videos[idx].duration.isBlank() && dur.isNotBlank()) {
                                videos[idx] = videos[idx].copy(duration = dur)
                            }
                        } else if (vid.isNotBlank() && title.isNotBlank() && seenVideoIds.add(vid)) {
                            val pubSimple = vr.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty()
                            val pubRuns = vr.optJSONObject("publishedTimeText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                            val rawDate = pubSimple.ifBlank { pubRuns }
                            var uploadDate = ""
                            var publishedTs = 0L
                            if (rawDate.isNotBlank()) {
                                val parsed = DurationUtils.parseRelativeTime(rawDate)
                                uploadDate = parsed.first
                                publishedTs = parsed.second
                            }
                            if (publishedTs <= 0L) {
                                publishedTs = System.currentTimeMillis() - (videos.size * 3600_000L * 24L)
                            }

                            videos.add(
                                FetchedVideo(
                                    videoId = vid,
                                    title = unescapeHtml(title),
                                    channelName = channel,
                                    description = "",
                                    duration = dur,
                                    thumbnailUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                                    uploadDate = uploadDate,
                                    publishedTimestamp = publishedTs
                                )
                            )
                        }
                    }

                    // 3. Check continuation tokens for video list pagination
                    // A) continuationItemRenderer
                    if (obj.has("continuationItemRenderer")) {
                        val cir = obj.optJSONObject("continuationItemRenderer")
                        val token = cir?.optJSONObject("continuationEndpoint")
                            ?.optJSONObject("continuationCommand")
                            ?.optString("token")
                        if (!token.isNullOrBlank() && token.length > 20) {
                            nextToken = token
                        }
                    }

                    // B) continuationItemViewModel (modern playlist pagination)
                    if (obj.has("continuationItemViewModel")) {
                        val civm = obj.optJSONObject("continuationItemViewModel")
                        val cmd = civm?.optJSONObject("continuationCommand")
                        val token = cmd?.optJSONObject("innertubeCommand")?.optString("token")
                            ?: cmd?.optString("token")
                        if (!token.isNullOrBlank() && token.length > 20) {
                            nextToken = token
                        }
                    }

                    // C) continuationCommand direct
                    if (obj.has("continuationCommand")) {
                        val cmd = obj.optJSONObject("continuationCommand")
                        val token = cmd?.optString("token")
                            ?: cmd?.optJSONObject("innertubeCommand")?.optString("token")
                        if (!token.isNullOrBlank() && token.length > 20) {
                            nextToken = token
                        }
                    }

                    // D) continuationEndpoint direct
                    if (obj.has("continuationEndpoint")) {
                        val ep = obj.optJSONObject("continuationEndpoint")
                        val token = ep?.optJSONObject("continuationCommand")?.optString("token")
                        if (!token.isNullOrBlank() && token.length > 20) {
                            nextToken = token
                        }
                    }

                    // Recurse children, explicitly skipping non-content trees that contain extraneous tokens
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k == "header" || k == "topbar" || k == "frameworkUpdates" ||
                            k == "engagementPanels" || k == "pageHeaderRenderer" ||
                            k == "commentsHeaderRenderer" || k == "adSlotRenderer"
                        ) {
                            continue
                        }
                        inspect(obj.opt(k))
                    }
                }
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        inspect(obj.opt(i))
                    }
                }
            }
        }

        // Inspect continuation payloads first before static content skeletons
        var inspectedTarget = false
        if (jsonObj.has("onResponseReceivedActions")) {
            inspect(jsonObj.opt("onResponseReceivedActions"))
            inspectedTarget = true
        }
        if (jsonObj.has("onResponseReceivedEndpoints")) {
            inspect(jsonObj.opt("onResponseReceivedEndpoints"))
            inspectedTarget = true
        }
        if (jsonObj.has("continuationContents")) {
            inspect(jsonObj.opt("continuationContents"))
            inspectedTarget = true
        }
        if (jsonObj.has("contents")) {
            inspect(jsonObj.opt("contents"))
            inspectedTarget = true
        }
        if (!inspectedTarget) {
            inspect(jsonObj)
        }

        return nextToken
    }

    /**
     * Fast, complete extraction of ytInitialData JSON substring from HTML
     */
    private fun extractYtInitialDataJson(html: String): String? {
        val markers = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = ")
        for (m in markers) {
            val idx = html.indexOf(m)
            if (idx != -1) {
                val start = idx + m.length
                val scriptEnd = html.indexOf("</script>", start)
                if (scriptEnd != -1) {
                    var candidate = html.substring(start, scriptEnd).trim()
                    if (candidate.endsWith(";")) {
                        candidate = candidate.substring(0, candidate.length - 1).trim()
                    }
                    if (candidate.startsWith("{") && candidate.endsWith("}")) {
                        return candidate
                    }
                }
            }
        }
        return null
    }

    /**
     * Fallback block scanner when JSON parsing isn't applicable
     */
    private fun parseBlocksFromHtml(
        html: String,
        fallbackChannel: String,
        seenVideoIds: MutableSet<String>,
        videos: MutableList<FetchedVideo>
    ) {
        val pvrMatcher = Pattern.compile("\"playlistVideoRenderer\":\\{").matcher(html)
        while (pvrMatcher.find()) {
            val start = pvrMatcher.start()
            val end = kotlin.math.min(html.length, start + 3000)
            val block = html.substring(start, end)

            val vidM = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").matcher(block)
            if (!vidM.find()) continue
            val vid = vidM.group(1) ?: continue
            if (!seenVideoIds.add(vid)) continue

            val titleM = Pattern.compile("\"title\":\\{(?:\"runs\":\\[\\{\"text\":\"(.*?)\"\\}\\]|\"simpleText\":\"(.*?)\")").matcher(block)
            val rawTitle = if (titleM.find()) titleM.group(1) ?: titleM.group(2) ?: "" else ""
            val title = DurationUtils.cleanVideoTitle(rawTitle)

            var dur = ""
            val timeM = Pattern.compile("\"simpleText\":\"(\\d{1,2}:\\d{2}(?::\\d{2})?)\"").matcher(block)
            if (timeM.find()) {
                dur = timeM.group(1).orEmpty()
            }
            dur = DurationUtils.formatCleanDuration(dur, vid)

            if (title.isNotBlank()) {
                val ts = System.currentTimeMillis() - (videos.size * 3600_000L * 24L)
                videos.add(
                    FetchedVideo(
                        videoId = vid,
                        title = unescapeHtml(title),
                        channelName = fallbackChannel,
                        description = "",
                        duration = dur,
                        thumbnailUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                        uploadDate = "",
                        publishedTimestamp = ts
                    )
                )
            }
        }
    }

    private fun extractContinuationTokenFallback(text: String): String? {
        val endpointM = Pattern.compile("\"continuationEndpoint\":\\{.*?\"continuationCommand\":\\{\"token\":\"([^\"]+)\"", Pattern.DOTALL).matcher(text)
        if (endpointM.find()) {
            return decodeToken(endpointM.group(1).orEmpty())
        }

        val cmdM = Pattern.compile("\"continuationCommand\":\\{\"token\":\"([^\"]+)\"").matcher(text)
        if (cmdM.find()) {
            return decodeToken(cmdM.group(1).orEmpty())
        }

        return null
    }

    private fun decodeToken(token: String): String {
        return try {
            URLDecoder.decode(token, "UTF-8")
        } catch (_: Exception) {
            token
        }
    }

    private fun extractInnertubeApiKey(html: String): String {
        val m = Pattern.compile("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").matcher(html)
        if (m.find()) return m.group(1).orEmpty()
        val m2 = Pattern.compile("\"innertubeApiKey\":\"([^\"]+)\"").matcher(html)
        if (m2.find()) return m2.group(1).orEmpty()
        return ""
    }

    private fun extractClientVersion(html: String): String {
        val m = Pattern.compile("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").matcher(html)
        if (m.find()) return m.group(1).orEmpty()
        val m2 = Pattern.compile("\"clientVersion\":\"([^\"]+)\"").matcher(html)
        if (m2.find()) return m2.group(1).orEmpty()
        return ""
    }

    data class SingleVideoInfo(
        val videoId: String,
        val title: String,
        val channelName: String
    )

    suspend fun fetchSingleVideoInfo(videoId: String): SingleVideoInfo? = withContext(Dispatchers.IO) {
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
                var title = ""
                var channel = ""

                val ogTitle = Pattern.compile("<meta property=\"og:title\" content=\"(.*?)\">").matcher(html)
                if (ogTitle.find()) {
                    title = unescapeHtml(ogTitle.group(1)).trim()
                } else {
                    val t = Pattern.compile("<title>(.*?)</title>").matcher(html)
                    if (t.find()) {
                        title = unescapeHtml(t.group(1)).replace(" - YouTube", "").trim()
                    }
                }

                val authorMatcher = Pattern.compile("<link itemprop=\"name\" content=\"(.*?)\">").matcher(html)
                if (authorMatcher.find()) {
                    channel = unescapeHtml(authorMatcher.group(1)).trim()
                } else {
                    val mChan = Pattern.compile("\"ownerChannelName\":\"(.*?)\"").matcher(html)
                    if (mChan.find()) {
                        channel = unescapeJson(mChan.group(1)).trim()
                    }
                }

                if (title.isNotBlank()) {
                    return@withContext SingleVideoInfo(
                        videoId = videoId,
                        title = title,
                        channelName = channel.ifBlank { "YouTube" }
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch info for $videoId: ${e.message}")
        }
        null
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

                val shortDescPattern = Pattern.compile("\"shortDescription\":\"(.*?)\"", Pattern.DOTALL)
                val m1 = shortDescPattern.matcher(html)
                if (m1.find()) {
                    val raw = m1.group(1).orEmpty()
                    return@withContext unescapeJson(raw)
                }

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
            val rawTitle = unescapeHtml(extractTag(entryXml, "title").orEmpty())
            val title = DurationUtils.cleanVideoTitle(rawTitle)
            val author = unescapeHtml(extractTag(entryXml, "name").orEmpty())
            val desc = unescapeHtml(extractTag(entryXml, "media:description").orEmpty())
            val thumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

            var durationStr = ""
            val durPattern = Pattern.compile("(?:seconds=[\"'](\\d+)[\"']|duration=[\"'](\\d+)[\"'])")
            val durMatcher = durPattern.matcher(entryXml)
            if (durMatcher.find()) {
                val sec = durMatcher.group(1) ?: durMatcher.group(2)
                sec?.toLongOrNull()?.let {
                    durationStr = DurationUtils.formatSeconds(it)
                }
            }
            durationStr = DurationUtils.formatCleanDuration(durationStr, videoId)
            val publishedIso = extractTag(entryXml, "published").orEmpty()
            val (uploadDate, publishedTimestamp) = DurationUtils.parseIsoPublishedDate(publishedIso)

            result.add(
                FetchedVideo(
                    videoId = videoId,
                    title = title.ifBlank { "YouTube Video" },
                    channelName = author,
                    description = desc,
                    duration = durationStr,
                    thumbnailUrl = thumbnail,
                    uploadDate = uploadDate,
                    publishedTimestamp = publishedTimestamp
                )
            )
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
