package com.example.util

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import com.example.data.local.entity.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class DiscoveredYouTubeChannel(
    val channelId: String,
    val name: String,
    val handleOrUrl: String
)

data class DiscoveredYouTubePlaylist(
    val playlistId: String,
    val title: String
)

object YouTubeSyncHelper {
    private const val TAG = "YouTubeSyncHelper"
    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    private const val YOUTUBE_OAUTH_SCOPE = "oauth2:https://www.googleapis.com/auth/youtube"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cookieManagerReady: Boolean = false

    fun markCookieManagerReady() {
        cookieManagerReady = true
    }

    /**
     * Checks if the user has an active authenticated YouTube WebView session (SAPISID cookie).
     * Avoids cold-initializing Chromium/WebView on app startup unless [allowColdInit] is true.
     */
    fun hasYouTubeWebSession(allowColdInit: Boolean = false): Boolean {
        if (!cookieManagerReady && !allowColdInit) return false
        return try {
            cookieManagerReady = true
            val (_, sapisid) = getCombinedCookiesAndSapisid()
            sapisid.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun getCombinedCookiesAndSapisid(): Pair<String, String> {
        val cookieManager = CookieManager.getInstance()
        val ytCookies = cookieManager.getCookie("https://www.youtube.com").orEmpty()
        val mCookies = cookieManager.getCookie("https://m.youtube.com").orEmpty()
        val combined = when {
            ytCookies.isNotBlank() && mCookies.isNotBlank() -> "$ytCookies; $mCookies"
            ytCookies.isNotBlank() -> ytCookies
            else -> mCookies
        }
        return Pair(combined, extractSapisid(combined))
    }

    private fun extractSapisid(cookies: String): String {
        if (cookies.isBlank()) return ""
        val parts = cookies.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            when {
                trimmed.startsWith("SAPISID=") -> return trimmed.removePrefix("SAPISID=").trim()
                trimmed.startsWith("__Secure-3PAPISID=") -> return trimmed.removePrefix("__Secure-3PAPISID=").trim()
                trimmed.startsWith("__Secure-1PAPISID=") -> return trimmed.removePrefix("__Secure-1PAPISID=").trim()
            }
        }
        return ""
    }

    private fun generateSapisidHash(sapisid: String, origin: String = YOUTUBE_ORIGIN): String {
        val timestamp = System.currentTimeMillis() / 1000L
        val input = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val sha1Hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_${sha1Hex}"
    }

    /**
     * Fetches ALL Playlists owned/saved by the logged-in user on YouTube (A to Z).
     * Uses:
     * 1. `youtubei/v1/playlist/get_add_to_playlist` (returns all playlists in the user's "Save to playlist" menu!)
     * 2. `youtubei/v1/browse` (`FEplaylist_aggregation` & `FElibrary`)
     * 3. `youtube/v3/playlists?part=snippet&mine=true` (if OAuth2 token is available)
     */
    suspend fun fetchAllUserPlaylistsFromYouTube(
        context: Context,
        email: String = "",
        oauthToken: String = ""
    ): List<DiscoveredYouTubePlaylist> = withContext(Dispatchers.IO) {
        val discovered = LinkedHashMap<String, DiscoveredYouTubePlaylist>()
        val (cookies, sapisid) = getCombinedCookiesAndSapisid()

        if (sapisid.isNotBlank()) {
            // 1. Call `playlist/get_add_to_playlist` - returns the complete list of user playlists for saving videos
            try {
                val authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN)
                val payload = JSONObject().apply {
                    put("context", buildWebClientContext())
                    put("videoIds", JSONArray().apply { put("M7lc1UVf-VE") })
                    put("excludeWatchLater", false)
                }
                val request = buildAuthenticatedInnerTubeRequest(
                    url = "https://www.youtube.com/youtubei/v1/playlist/get_add_to_playlist?prettyPrint=false",
                    cookies = cookies,
                    authHeader = authHeader,
                    payload = payload
                )
                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.isNotBlank()) {
                        val json = JSONObject(bodyStr)
                        extractPlaylistsFromJson(json, discovered)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "get_add_to_playlist fetch note: ${e.message}")
            }

            // 2. Call `browse` on `FEplaylist_aggregation` and `FElibrary` with continuation support
            for (browseId in listOf("FEplaylist_aggregation", "FElibrary")) {
                try {
                    var nextContinuation: String? = null
                    val firstPayload = JSONObject().apply {
                        put("context", buildWebClientContext())
                        put("browseId", browseId)
                    }
                    val firstReq = buildAuthenticatedInnerTubeRequest(
                        url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                        cookies = cookies,
                        authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN),
                        payload = firstPayload
                    )
                    httpClient.newCall(firstReq).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful && bodyStr.isNotBlank()) {
                            val json = JSONObject(bodyStr)
                            extractPlaylistsFromJson(json, discovered)
                            nextContinuation = extractNextContinuationToken(json)
                        }
                    }

                    var page = 0
                    while (!nextContinuation.isNullOrBlank() && page < 10) {
                        page++
                        val contPayload = JSONObject().apply {
                            put("context", buildWebClientContext())
                            put("continuation", nextContinuation)
                        }
                        val contReq = buildAuthenticatedInnerTubeRequest(
                            url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                            cookies = cookies,
                            authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN),
                            payload = contPayload
                        )
                        var newToken: String? = null
                        httpClient.newCall(contReq).execute().use { response ->
                            val bodyStr = response.body?.string().orEmpty()
                            if (response.isSuccessful && bodyStr.isNotBlank()) {
                                val json = JSONObject(bodyStr)
                                extractPlaylistsFromJson(json, discovered)
                                newToken = extractNextContinuationToken(json)
                            }
                        }
                        if (newToken == nextContinuation) break
                        nextContinuation = newToken
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Browse $browseId playlists note: ${e.message}")
                }
            }

            // 3. Also check `guide` for any playlists listed in the user's sidebar guide
            try {
                val guidePayload = JSONObject().apply {
                    put("context", buildWebClientContext())
                }
                val guideReq = buildAuthenticatedInnerTubeRequest(
                    url = "https://www.youtube.com/youtubei/v1/guide?prettyPrint=false",
                    cookies = cookies,
                    authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN),
                    payload = guidePayload
                )
                httpClient.newCall(guideReq).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.isNotBlank()) {
                        extractPlaylistsFromJson(JSONObject(bodyStr), discovered)
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Also fetch via YouTube Data API v3 if OAuth2 token is available
        val activeToken = oauthToken.ifBlank {
            if (email.isNotBlank()) tryGetAccountManagerTokenSilently(context, email) else ""
        }
        if (activeToken.isNotBlank()) {
            try {
                var pageToken = ""
                var pageCount = 0
                while (pageCount < 15) {
                    pageCount++
                    val url = "https://www.googleapis.com/youtube/v3/playlists?part=snippet&mine=true&maxResults=50" +
                        if (pageToken.isNotBlank()) "&pageToken=$pageToken" else ""
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $activeToken")
                        .get()
                        .build()
                    var nextToken = ""
                    httpClient.newCall(req).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful && bodyStr.isNotBlank()) {
                            val json = JSONObject(bodyStr)
                            val items = json.optJSONArray("items")
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val item = items.optJSONObject(i) ?: continue
                                    val plId = item.optString("id", "").trim()
                                    val title = item.optJSONObject("snippet")?.optString("title", "")?.trim().orEmpty()
                                    if (plId.isNotBlank() && title.isNotBlank()) {
                                        discovered[plId] = DiscoveredYouTubePlaylist(plId, title)
                                    }
                                }
                            }
                            nextToken = json.optString("nextPageToken", "")
                        }
                    }
                    if (nextToken.isBlank()) break
                    pageToken = nextToken
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube Data API v3 playlists fetch note: ${e.message}")
            }
        }

        discovered.values.sortedBy { it.title.lowercase() }
    }

    /**
     * Fetches ALL Subscribed Channels from the user's YouTube account (A to Z) — PocketTube style!
     * Uses:
     * 1. `youtubei/v1/browse` (`FEchannels` - https://www.youtube.com/feed/channels) with continuation pagination
     * 2. `youtubei/v1/guide` (left navigation sidebar subscriptions list)
     * 3. `youtube/v3/subscriptions?part=snippet&mine=true` (if OAuth2 token is available)
     */
    suspend fun fetchAllSubscribedChannelsFromYouTube(
        context: Context,
        email: String = "",
        oauthToken: String = ""
    ): List<DiscoveredYouTubeChannel> = withContext(Dispatchers.IO) {
        val discovered = LinkedHashMap<String, DiscoveredYouTubeChannel>()
        val (cookies, sapisid) = getCombinedCookiesAndSapisid()

        if (sapisid.isNotBlank()) {
            // 1. Fetch `FEchannels` (All Subscriptions page) + paginate through continuations
            try {
                var nextContinuation: String? = null
                val firstPayload = JSONObject().apply {
                    put("context", buildWebClientContext())
                    put("browseId", "FEchannels")
                }
                val firstReq = buildAuthenticatedInnerTubeRequest(
                    url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                    cookies = cookies,
                    authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN),
                    payload = firstPayload
                )
                httpClient.newCall(firstReq).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.isNotBlank()) {
                        val json = JSONObject(bodyStr)
                        extractChannelsFromJson(json, discovered)
                        nextContinuation = extractNextContinuationToken(json)
                    }
                }

                var page = 0
                while (!nextContinuation.isNullOrBlank() && page < 20) {
                    page++
                    val contPayload = JSONObject().apply {
                        put("context", buildWebClientContext())
                        put("continuation", nextContinuation)
                    }
                    val contReq = buildAuthenticatedInnerTubeRequest(
                        url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                        cookies = cookies,
                        authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN),
                        payload = contPayload
                    )
                    var newToken: String? = null
                    httpClient.newCall(contReq).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful && bodyStr.isNotBlank()) {
                            val json = JSONObject(bodyStr)
                            extractChannelsFromJson(json, discovered)
                            newToken = extractNextContinuationToken(json)
                        }
                    }
                    if (newToken == nextContinuation) break
                    nextContinuation = newToken
                }
            } catch (e: Exception) {
                Log.w(TAG, "FEchannels fetch note: ${e.message}")
            }

            // 2. Fetch `guide` endpoint (Subscriptions section in YouTube guide)
            try {
                val guidePayload = JSONObject().apply {
                    put("context", buildWebClientContext())
                }
                val guideReq = buildAuthenticatedInnerTubeRequest(
                    url = "https://www.youtube.com/youtubei/v1/guide?prettyPrint=false",
                    cookies = cookies,
                    authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN),
                    payload = guidePayload
                )
                httpClient.newCall(guideReq).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.isNotBlank()) {
                        extractChannelsFromJson(JSONObject(bodyStr), discovered)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Guide channels fetch note: ${e.message}")
            }
        }

        // 3. Also fetch via YouTube Data API v3 if OAuth2 token is available
        val activeToken = oauthToken.ifBlank {
            if (email.isNotBlank()) tryGetAccountManagerTokenSilently(context, email) else ""
        }
        if (activeToken.isNotBlank()) {
            try {
                var pageToken = ""
                var pageCount = 0
                while (pageCount < 20) {
                    pageCount++
                    val url = "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50" +
                        if (pageToken.isNotBlank()) "&pageToken=$pageToken" else ""
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $activeToken")
                        .get()
                        .build()
                    var nextToken = ""
                    httpClient.newCall(req).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful && bodyStr.isNotBlank()) {
                            val json = JSONObject(bodyStr)
                            val items = json.optJSONArray("items")
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val snippet = items.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                                    val title = snippet.optString("title", "").trim()
                                    val chId = snippet.optJSONObject("resourceId")?.optString("channelId", "")?.trim().orEmpty()
                                    if (chId.startsWith("UC") && title.isNotBlank()) {
                                        discovered[chId] = DiscoveredYouTubeChannel(
                                            channelId = chId,
                                            name = title,
                                            handleOrUrl = "https://www.youtube.com/channel/$chId"
                                        )
                                    }
                                }
                            }
                            nextToken = json.optString("nextPageToken", "")
                        }
                    }
                    if (nextToken.isBlank()) break
                    pageToken = nextToken
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube Data API v3 subscriptions note: ${e.message}")
            }
        }

        discovered.values.sortedBy { it.name.lowercase() }
    }

    private fun buildWebClientContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB")
                put("clientVersion", "2.20250101.00.00")
                put("hl", "en")
                put("gl", "IN")
            })
        }
    }

    private fun buildAuthenticatedInnerTubeRequest(
        url: String,
        cookies: String,
        authHeader: String,
        payload: JSONObject
    ): Request {
        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Cookie", cookies)
            .addHeader("Authorization", authHeader)
            .addHeader("X-Origin", YOUTUBE_ORIGIN)
            .addHeader("Origin", YOUTUBE_ORIGIN)
            .addHeader("Referer", "https://www.youtube.com/")
            .addHeader("X-Goog-AuthUser", "0")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            )
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun extractTextFromNode(node: JSONObject?): String {
        if (node == null) return ""
        val simple = node.optString("simpleText", "").trim()
        if (simple.isNotBlank()) return simple
        val content = node.optString("content", "").trim()
        if (content.isNotBlank()) return content
        val runs = node.optJSONArray("runs")
        if (runs != null && runs.length() > 0) {
            val sb = StringBuilder()
            for (i in 0 until runs.length()) {
                sb.append(runs.optJSONObject(i)?.optString("text", "").orEmpty())
            }
            return sb.toString().trim()
        }
        return ""
    }

    private fun extractPlaylistsFromJson(node: Any?, out: MutableMap<String, DiscoveredYouTubePlaylist>) {
        when (node) {
            is JSONObject -> {
                // 1. playlistAddToOptionRenderer (from playlist/get_add_to_playlist)
                val addOpt = node.optJSONObject("playlistAddToOptionRenderer")
                if (addOpt != null) {
                    val plId = addOpt.optString("playlistId", "").trim().removePrefix("VL")
                    val title = extractTextFromNode(addOpt.optJSONObject("title"))
                    if (plId.isNotBlank() && title.isNotBlank() && plId != "WL" && !plId.startsWith("LL")) {
                        out[plId] = DiscoveredYouTubePlaylist(plId, title)
                    }
                }

                // 2. playlistRenderer / gridPlaylistRenderer / compactPlaylistRenderer
                for (rendererKey in listOf("playlistRenderer", "gridPlaylistRenderer", "compactPlaylistRenderer")) {
                    val r = node.optJSONObject(rendererKey)
                    if (r != null) {
                        val plId = r.optString("playlistId", "").trim().removePrefix("VL")
                        val title = extractTextFromNode(r.optJSONObject("title"))
                        if (plId.isNotBlank() && title.isNotBlank() && plId != "WL" && !plId.startsWith("LL")) {
                            out[plId] = DiscoveredYouTubePlaylist(plId, title)
                        }
                    }
                }

                // 3. Modern lockupViewModel (used in newer YouTube FEplaylist_aggregation / FElibrary)
                val lockup = node.optJSONObject("lockupViewModel")
                if (lockup != null) {
                    val contentId = lockup.optString("contentId", "").trim().removePrefix("VL")
                    if (contentId.startsWith("PL") || contentId.startsWith("OL")) {
                        val title = lockup.optJSONObject("metadata")
                            ?.optJSONObject("lockupMetadataViewModel")
                            ?.optJSONObject("title")
                            ?.optString("content", "")
                            ?.trim()
                            .orEmpty()
                        if (title.isNotBlank()) {
                            out[contentId] = DiscoveredYouTubePlaylist(contentId, title)
                        }
                    }
                }

                // 4. guideEntryRenderer for playlists in sidebar guide
                val guideEntry = node.optJSONObject("guideEntryRenderer")
                if (guideEntry != null) {
                    val browseId = guideEntry.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId", "")
                        ?.trim()
                        .orEmpty()
                    if (browseId.startsWith("VLPL")) {
                        val plId = browseId.removePrefix("VL")
                        val title = extractTextFromNode(guideEntry.optJSONObject("formattedTitle"))
                        if (plId.isNotBlank() && title.isNotBlank()) {
                            out[plId] = DiscoveredYouTubePlaylist(plId, title)
                        }
                    }
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    extractPlaylistsFromJson(node.opt(k), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractPlaylistsFromJson(node.opt(i), out)
                }
            }
        }
    }

    private fun extractChannelsFromJson(node: Any?, out: MutableMap<String, DiscoveredYouTubeChannel>) {
        when (node) {
            is JSONObject -> {
                // 1. channelRenderer / gridChannelRenderer / compactChannelRenderer (from FEchannels)
                for (key in listOf("channelRenderer", "gridChannelRenderer", "compactChannelRenderer")) {
                    val r = node.optJSONObject(key)
                    if (r != null) {
                        val chId = r.optString("channelId", "").trim()
                        val title = extractTextFromNode(r.optJSONObject("title"))
                        val canonicalBase = r.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("canonicalBaseUrl", "")
                            ?.trim()
                            .orEmpty()
                        val handleOrUrl = when {
                            canonicalBase.startsWith("/@") -> "https://www.youtube.com$canonicalBase"
                            chId.startsWith("UC") -> "https://www.youtube.com/channel/$chId"
                            else -> ""
                        }
                        if (chId.startsWith("UC") && title.isNotBlank()) {
                            out[chId] = DiscoveredYouTubeChannel(chId, title, handleOrUrl)
                        }
                    }
                }

                // 2. guideSubscriptionEntryRenderer / guideEntryRenderer (from youtubei/v1/guide)
                for (key in listOf("guideSubscriptionEntryRenderer", "guideEntryRenderer")) {
                    val g = node.optJSONObject(key)
                    if (g != null) {
                        val browseId = g.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId", "")
                            ?.trim()
                            .orEmpty()
                        val title = extractTextFromNode(g.optJSONObject("formattedTitle"))
                        if (browseId.startsWith("UC") && browseId.length >= 20 && title.isNotBlank()) {
                            out[browseId] = DiscoveredYouTubeChannel(
                                channelId = browseId,
                                name = title,
                                handleOrUrl = "https://www.youtube.com/channel/$browseId"
                            )
                        }
                    }
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    extractChannelsFromJson(node.opt(k), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractChannelsFromJson(node.opt(i), out)
                }
            }
        }
    }

    private fun extractNextContinuationToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val contCmd = node.optJSONObject("continuationCommand")
                if (contCmd != null) {
                    val token = contCmd.optString("token", "").trim()
                    if (token.isNotBlank()) return token
                }
                val nextCont = node.optJSONObject("nextContinuationData")
                if (nextCont != null) {
                    val token = nextCont.optString("continuation", "").trim()
                    if (token.isNotBlank()) return token
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val found = extractNextContinuationToken(node.opt(k))
                    if (!found.isNullOrBlank()) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = extractNextContinuationToken(node.opt(i))
                    if (!found.isNullOrBlank()) return found
                }
            }
        }
        return null
    }

    /**
     * Directly adds a video to the user's real YouTube playlist using their active
     * YouTube account session (InnerTube `browse/edit_playlist` `ACTION_ADD_VIDEO`)
     * OR their OAuth2 token (`youtube/v3/playlistItems`).
     */
    suspend fun autoAddVideoToYouTubePlaylist(
        context: Context,
        playlistId: String,
        videoId: String,
        email: String = "",
        oauthToken: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanPlaylistId = playlistId.trim().removePrefix("VL")
        val cleanVideoId = videoId.trim()
        if (!isRealYouTubePlaylist(cleanPlaylistId) || cleanVideoId.isBlank()) {
            return@withContext false
        }

        // 1. Try InnerTube Authenticated Web Session first
        if (addViaInnerTubeWebSession(cleanPlaylistId, cleanVideoId)) {
            Log.d(TAG, "Successfully auto-saved $cleanVideoId to YouTube playlist $cleanPlaylistId via InnerTube session")
            return@withContext true
        }

        // 2. Try YouTube Data API v3 with OAuth2 token if available
        val activeToken = oauthToken.ifBlank {
            if (email.isNotBlank()) tryGetAccountManagerTokenSilently(context, email) else ""
        }
        if (activeToken.isNotBlank()) {
            if (addViaYouTubeDataApiV3(activeToken, cleanPlaylistId, cleanVideoId)) {
                Log.d(TAG, "Successfully auto-saved $cleanVideoId to YouTube playlist $cleanPlaylistId via YouTube Data API v3")
                return@withContext true
            }
        }

        false
    }

    /**
     * Adds a video to a YouTube playlist using the signed-in YouTube WebView cookies & SAPISIDHASH.
     */
    fun addViaInnerTubeWebSession(playlistId: String, videoId: String): Boolean {
        return try {
            val (combinedCookies, sapisid) = getCombinedCookiesAndSapisid()
            if (sapisid.isBlank()) return false

            val authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN)
            val cleanPlaylistId = playlistId.trim().removePrefix("VL")

            val payload = JSONObject().apply {
                put("context", buildWebClientContext())
                put("playlistId", cleanPlaylistId)
                put("actions", JSONArray().apply {
                    put(JSONObject().apply {
                        put("addedVideoId", videoId.trim())
                        put("action", "ACTION_ADD_VIDEO")
                    })
                })
            }

            val request = buildAuthenticatedInnerTubeRequest(
                url = "https://www.youtube.com/youtubei/v1/browse/edit_playlist?prettyPrint=false",
                cookies = combinedCookies,
                authHeader = authHeader,
                payload = payload
            )

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful && (bodyStr.contains("STATUS_SUCCEEDED") || bodyStr.contains("playlistEditResults"))) {
                    return true
                }
                Log.w(TAG, "InnerTube edit_playlist HTTP ${response.code}: ${bodyStr.take(250)}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube edit_playlist failed: ${e.message}")
            false
        }
    }

    /**
     * Creates a new playlist on the user's real YouTube account and adds the initial video.
     */
    suspend fun autoCreateYouTubePlaylist(
        context: Context,
        title: String,
        videoId: String,
        email: String = "",
        oauthToken: String = ""
    ): String? = withContext(Dispatchers.IO) {
        try {
            val (combinedCookies, sapisid) = getCombinedCookiesAndSapisid()
            if (sapisid.isNotBlank()) {
                val authHeader = generateSapisidHash(sapisid, YOUTUBE_ORIGIN)
                val payload = JSONObject().apply {
                    put("context", buildWebClientContext())
                    put("title", title.trim())
                    put("privacyStatus", "PRIVATE")
                    if (videoId.isNotBlank()) {
                        put("videoIds", JSONArray().apply { put(videoId.trim()) })
                    }
                }

                val request = buildAuthenticatedInnerTubeRequest(
                    url = "https://www.youtube.com/youtubei/v1/playlist/create?prettyPrint=false",
                    cookies = combinedCookies,
                    authHeader = authHeader,
                    payload = payload
                )

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.contains("playlistId")) {
                        val json = JSONObject(bodyStr)
                        val newPlId = json.optString("playlistId", "")
                        if (newPlId.isNotBlank()) {
                            return@withContext newPlId
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube create playlist failed: ${e.message}")
        }

        val activeToken = oauthToken.ifBlank {
            if (email.isNotBlank()) tryGetAccountManagerTokenSilently(context, email) else ""
        }
        if (activeToken.isNotBlank()) {
            try {
                val payload = JSONObject().apply {
                    put("snippet", JSONObject().apply {
                        put("title", title.trim())
                        put("description", "Created with FocusTube")
                    })
                    put("status", JSONObject().apply {
                        put("privacyStatus", "private")
                    })
                }
                val request = Request.Builder()
                    .url("https://www.googleapis.com/youtube/v3/playlists?part=snippet,status")
                    .addHeader("Authorization", "Bearer $activeToken")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val createdId = httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        JSONObject(bodyStr).optString("id", "").takeIf { it.isNotBlank() }
                    } else null
                }
                if (!createdId.isNullOrBlank()) {
                    if (videoId.isNotBlank()) {
                        addViaYouTubeDataApiV3(activeToken, createdId, videoId)
                    }
                    return@withContext createdId
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube Data API v3 create playlist failed: ${e.message}")
            }
        }

        null
    }

    /**
     * Adds a video to a YouTube playlist using YouTube Data API v3 and an OAuth2 Bearer token.
     */
    fun addViaYouTubeDataApiV3(accessToken: String, playlistId: String, videoId: String): Boolean {
        return try {
            val payload = JSONObject().apply {
                put("snippet", JSONObject().apply {
                    put("playlistId", playlistId.trim().removePrefix("VL"))
                    put("resourceId", JSONObject().apply {
                        put("kind", "youtube#video")
                        put("videoId", videoId.trim())
                    })
                })
            }

            val request = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet")
                .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 409
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube Data API v3 playlistItems.insert failed: ${e.message}")
            false
        }
    }

    @Suppress("DEPRECATION")
    suspend fun requestYouTubeOAuthToken(context: Context, email: String): Pair<String?, Intent?> = withContext(Dispatchers.IO) {
        try {
            if (email.isBlank()) return@withContext Pair(null, null)
            val account = Account(email.trim(), "com.google")
            val am = AccountManager.get(context)
            val future = am.getAuthToken(account, YOUTUBE_OAUTH_SCOPE, null, false, null, null)
            val bundle = future.result
            val token = bundle?.getString(AccountManager.KEY_AUTHTOKEN)
            val intent = bundle?.getParcelable<Intent>(AccountManager.KEY_INTENT)
            Pair(token, intent)
        } catch (e: Exception) {
            Log.w(TAG, "AccountManager OAuth token request note: ${e.message}")
            Pair(null, null)
        }
    }

    private suspend fun tryGetAccountManagerTokenSilently(context: Context, email: String): String {
        return requestYouTubeOAuthToken(context, email).first.orEmpty()
    }

    fun openInYouTubeApp(context: Context, videoId: String, playlistId: String? = null): Boolean {
        val cleanVideoId = videoId.trim()
        val cleanPlaylistId = playlistId?.trim().orEmpty()

        val uriString = if (cleanPlaylistId.isNotBlank() && isRealYouTubePlaylist(cleanPlaylistId)) {
            "https://www.youtube.com/watch?v=$cleanVideoId&list=$cleanPlaylistId"
        } else {
            "https://www.youtube.com/watch?v=$cleanVideoId"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (err: Exception) {
                Toast.makeText(context, "Could not open YouTube: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    fun openPlaylistInYouTubeApp(context: Context, playlistId: String): Boolean {
        val cleanId = playlistId.trim()
        val uriString = if (isRealYouTubePlaylist(cleanId)) {
            "https://www.youtube.com/playlist?list=$cleanId"
        } else {
            "https://www.youtube.com"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (err: Exception) {
                Toast.makeText(context, "Could not open YouTube playlist", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    fun copyAllVideoLinks(context: Context, playlistTitle: String, videos: List<VideoEntity>) {
        if (videos.isEmpty()) {
            Toast.makeText(context, "No videos to copy", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("📋 FocusTube Playlist: $playlistTitle\n")
        sb.append("Total Videos: ${videos.size}\n\n")
        videos.forEachIndexed { index, v ->
            sb.append("${index + 1}. ${v.title}\nhttps://youtu.be/${v.videoId}\n\n")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("FocusTube Playlist Videos", sb.toString().trim())
        clipboard?.setPrimaryClip(clip)

        Toast.makeText(context, "Copied ${videos.size} video link(s) to clipboard", Toast.LENGTH_LONG).show()
    }

    fun sharePlaylistVideos(context: Context, playlistTitle: String, videos: List<VideoEntity>) {
        if (videos.isEmpty()) return

        val sb = StringBuilder()
        sb.append("FocusTube Playlist: $playlistTitle\n\n")
        videos.forEachIndexed { index, v ->
            sb.append("${index + 1}. ${v.title} - https://youtu.be/${v.videoId}\n")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
            putExtra(Intent.EXTRA_TITLE, playlistTitle)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share or Save Playlist Videos").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }

    fun openBatchInYouTubeApp(context: Context, videoIds: List<String>): Boolean {
        val cleanIds = videoIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) {
            Toast.makeText(context, "No videos to sync", Toast.LENGTH_SHORT).show()
            return false
        }

        val batchIds = cleanIds.take(50)
        val url = "https://www.youtube.com/watch_videos?video_ids=" + batchIds.joinToString(",")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (err: Exception) {
                Toast.makeText(context, "Could not open YouTube: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    fun isRealYouTubePlaylist(playlistId: String): Boolean {
        val trimmed = playlistId.trim().removePrefix("VL")
        return trimmed.startsWith("PL") || trimmed.startsWith("UU") || trimmed.startsWith("RD") || trimmed.startsWith("OL")
    }
}
