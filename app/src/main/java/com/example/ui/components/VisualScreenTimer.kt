package com.example.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Color & Safety Zones for the Persistent Visual App Timer:
 * - GREEN_SAFE: 0 to 40 minutes (0 .. 2,399s) -> Glowing Emerald Green
 * - YELLOW_CAUTION: 40 minutes to 1.5 hours / 90 minutes (2,400 .. 5,399s) -> Glowing Golden Yellow
 * - AMBER_WARNING: 1.5 hours to 2 hours (5,400 .. 7,199s) -> Deep Amber-Orange transition
 * - RED_DANGER: 2 hours+ (7,200s+) -> Intense Pulsing Danger Red
 */
enum class TimerVisualZone {
    GREEN_SAFE,
    YELLOW_CAUTION,
    AMBER_WARNING,
    RED_DANGER
}

data class TimerZonePalette(
    val zone: TimerVisualZone,
    val primaryGlowColor: Color,
    val secondaryGlowColor: Color,
    val darkSurfaceColor: Color,
    val badgeBgColor: Color,
    val textColor: Color,
    val zoneLabelHindi: String,
    val zoneShortBadge: String,
    val zoneDescriptionHindi: String
)

enum class WatchEntityType {
    CHANNEL,
    PLAYLIST,
    GROUP
}

data class EntityWatchStat(
    val idKey: String,
    val title: String,
    val subtitle: String,
    val entityType: WatchEntityType,
    val timeSpentSeconds: Long,
    val videoCount: Int,
    val completedCount: Int,
    val notesCount: Int,
    val sharePercent: Int
)

data class CategoryMostLeastSummary(
    val entityType: WatchEntityType,
    val categoryTitleHindi: String,
    val mostSpent: EntityWatchStat?,
    val leastSpent: EntityWatchStat?,
    val rankedAll: List<EntityWatchStat>,
    val totalCategorySeconds: Long
)

/**
 * Represents one day's usage breakdown across Green (Safe), Yellow (Caution), and Red (Danger) zones.
 */
data class DailyZoneUsage(
    val dateKey: String,          // yyyy-MM-dd
    val dayShortLabel: String,    // Mon, Tue, ..., Today
    val dateDisplayLabel: String, // 16 Nov
    val isToday: Boolean,
    val greenSeconds: Long,       // 0 - 40m zone time
    val yellowSeconds: Long,      // 40m - 2h zone time
    val redSeconds: Long          // 2h+ danger zone time
) {
    val totalSeconds: Long get() = greenSeconds + yellowSeconds + redSeconds
}

/**
 * Singleton Manager that tracks and persists cumulative app usage time, 7-day Green/Yellow/Red zone breakdown,
 * and per-Channel, per-Playlist, and per-Group watch-time analytics.
 * Survives app closes, backgrounding, and process death without resetting to 00:00:00.
 * Resumes counting immediately from the exact saved second whenever the app is opened.
 */
object VisualScreenTimerManager {
    private const val PREFS_NAME = "focustube_persistent_visual_timer"
    private const val KEY_ELAPSED_SECONDS = "accumulated_elapsed_seconds"
    private const val KEY_LAST_SAVED_MS = "last_saved_timestamp_ms"

    // Thresholds in seconds:
    const val GREEN_LIMIT_SECONDS = 40 * 60L        // 40 minutes (2,400s)
    const val YELLOW_LIMIT_SECONDS = 90 * 60L       // 1.5 hours / 90 minutes (5,400s)
    const val DANGER_RED_SECONDS = 120 * 60L        // 2 hours / 120 minutes (7,200s)

    private val _elapsedSecondsFlow = MutableStateFlow(0L)
    val elapsedSecondsFlow: StateFlow<Long> = _elapsedSecondsFlow.asStateFlow()

    private val _weeklyUsageFlow = MutableStateFlow<List<DailyZoneUsage>>(emptyList())
    val weeklyUsageFlow: StateFlow<List<DailyZoneUsage>> = _weeklyUsageFlow.asStateFlow()

    private var isInitialized = false

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedSec = prefs.getLong(KEY_ELAPSED_SECONDS, 0L).coerceAtLeast(0L)
            _elapsedSecondsFlow.value = savedSec
            syncTodayZonesFromElapsed(context, savedSec)
            refreshWeeklyUsage(context)
            isInitialized = true
        }
    }

    fun incrementOneSecond(context: Context) {
        ensureInitialized(context)
        val next = _elapsedSecondsFlow.value + 1L
        _elapsedSecondsFlow.value = next

        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tKey = todayDateKey()
        val gKey = "day_${tKey}_green"
        val yKey = "day_${tKey}_yellow"
        val rKey = "day_${tKey}_red"

        val zone = getZoneForSeconds(next)
        val editor = prefs.edit()
            .putLong(KEY_ELAPSED_SECONDS, next)
            .putLong(KEY_LAST_SAVED_MS, System.currentTimeMillis())

        when (zone) {
            TimerVisualZone.GREEN_SAFE -> {
                editor.putLong(gKey, prefs.getLong(gKey, 0L) + 1L)
            }
            TimerVisualZone.YELLOW_CAUTION, TimerVisualZone.AMBER_WARNING -> {
                editor.putLong(yKey, prefs.getLong(yKey, 0L) + 1L)
            }
            TimerVisualZone.RED_DANGER -> {
                editor.putLong(rKey, prefs.getLong(rKey, 0L) + 1L)
            }
        }
        editor.apply()

        // Refresh weekly flow every 5 seconds or on zone transitions so dashboard stays live with zero overhead
        if (next % 5L == 0L || next <= 5L) {
            refreshWeeklyUsage(appCtx)
        }
    }

    /**
     * Records 1 second of active video playback against the playing video's YouTube Channel,
     * saved Playlist(s), and Subscription Group(s).
     */
    fun recordActiveVideoWatchSecond(
        context: Context,
        activeVideo: com.example.data.local.entity.VideoEntity?,
        allSavedVideos: List<com.example.data.local.entity.VideoEntity>,
        allChannels: List<com.example.data.local.entity.ChannelEntity>
    ) {
        if (activeVideo == null) return
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // 1. Channel watch time
            val channelKeyName = activeVideo.channelName.trim().lowercase().ifBlank { "youtube" }
            val cPrefKey = "watch_channel_$channelKeyName"
            editor.putLong(cPrefKey, prefs.getLong(cPrefKey, 0L) + 1L)

            // 2. Playlist watch time (direct playlistId + any playlist where this videoId is saved)
            val playlistIds = mutableSetOf<String>()
            if (!activeVideo.playlistId.isNullOrBlank()) {
                playlistIds.add(activeVideo.playlistId)
            }
            for (v in allSavedVideos) {
                if (v.videoId == activeVideo.videoId && !v.playlistId.isNullOrBlank()) {
                    playlistIds.add(v.playlistId)
                }
            }
            for (plId in playlistIds) {
                val pPrefKey = "watch_playlist_$plId"
                editor.putLong(pPrefKey, prefs.getLong(pPrefKey, 0L) + 1L)
            }

            // 3. Group watch time (direct groupId + any group that owns this channelName)
            val groupIds = mutableSetOf<Long>()
            if (activeVideo.groupId != null && activeVideo.groupId > 0L) {
                groupIds.add(activeVideo.groupId)
            }
            for (ch in allChannels) {
                if (ch.name.trim().equals(activeVideo.channelName.trim(), ignoreCase = true) && ch.groupId > 0L) {
                    groupIds.add(ch.groupId)
                }
            }
            for (gId in groupIds) {
                val gPrefKey = "watch_group_$gId"
                editor.putLong(gPrefKey, prefs.getLong(gPrefKey, 0L) + 1L)
            }

            editor.apply()
        } catch (_: Exception) {}
    }

    private fun parseDurationStringToSeconds(rawDuration: String, videoId: String): Long {
        val cleaned = com.example.util.DurationUtils.formatCleanDuration(rawDuration, videoId).trim()
        if (cleaned.isBlank()) return 480L // 8 min default estimate when duration string is empty
        val parts = cleaned.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            2 -> parts[0] * 60L + parts[1]
            1 -> parts[0]
            else -> 480L
        }.coerceAtLeast(60L)
    }

    /**
     * Estimates engaged watch time for a list of videos when combined with live tracked playback seconds:
     * - Completed videos count 100% of their duration
     * - Videos with scene notes count 65% of their duration + 180s per scene note
     * - Saved/queued videos in the library count a base engagement of 25% of duration
     */
    private fun estimateLibraryEngagementSeconds(
        videos: List<com.example.data.local.entity.VideoEntity>
    ): Long {
        val distinct = videos.distinctBy { it.videoId }
        var total = 0L
        for (v in distinct) {
            val dur = parseDurationStringToSeconds(v.duration, v.videoId)
            val noteLines = v.notes.lines().count { it.isNotBlank() }
            total += when {
                v.isCompleted -> dur + (noteLines * 120L)
                noteLines > 0 -> (dur * 65L) / 100L + (noteLines * 180L)
                else -> (dur * 22L) / 100L
            }
        }
        return total
    }

    /**
     * Computes Most Time Spent vs Least Time Spent analytical rankings for:
     * 1. YouTube Channels
     * 2. User Playlists
     * 3. Subscription Groups
     */
    fun computeEntityWatchAnalytics(
        context: Context,
        allSavedVideos: List<com.example.data.local.entity.VideoEntity>,
        allPlaylists: List<com.example.data.local.entity.PlaylistEntity>,
        allGroups: List<com.example.data.local.entity.SubscriptionGroupEntity>,
        allChannels: List<com.example.data.local.entity.ChannelEntity>
    ): Map<WatchEntityType, CategoryMostLeastSummary> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uniqueVideos = allSavedVideos.distinctBy { it.videoId }

        // 1. CHANNELS ANALYTICS
        val channelNamesMap = linkedMapOf<String, String>() // lowerName -> displayName
        for (ch in allChannels) {
            val clean = ch.name.trim()
            if (clean.isNotBlank()) {
                channelNamesMap.putIfAbsent(clean.lowercase(), clean)
            }
        }
        for (v in uniqueVideos) {
            val clean = v.channelName.trim()
            if (clean.isNotBlank()) {
                channelNamesMap.putIfAbsent(clean.lowercase(), clean)
            }
        }

        val rawChannelStats = channelNamesMap.map { (lowerName, displayName) ->
            val channelVideos = uniqueVideos.filter { it.channelName.trim().lowercase() == lowerName }
            val liveTrackedSec = prefs.getLong("watch_channel_$lowerName", 0L)
            val libraryEngagedSec = estimateLibraryEngagementSeconds(channelVideos)
            val combinedSec = liveTrackedSec + libraryEngagedSec
            val completedCnt = channelVideos.count { it.isCompleted }
            val notesCnt = channelVideos.count { it.notes.isNotBlank() }
            Triple(
                displayName,
                combinedSec,
                EntityWatchStat(
                    idKey = "ch_$lowerName",
                    title = displayName,
                    subtitle = "${channelVideos.size} वीडियो • $completedCnt पूरे • $notesCnt नोट्स",
                    entityType = WatchEntityType.CHANNEL,
                    timeSpentSeconds = combinedSec,
                    videoCount = channelVideos.size,
                    completedCount = completedCnt,
                    notesCount = notesCnt,
                    sharePercent = 0
                )
            )
        }
        val totalChannelSeconds = rawChannelStats.sumOf { it.second }.coerceAtLeast(1L)
        val rankedChannels = rawChannelStats
            .map { (_, sec, stat) ->
                stat.copy(sharePercent = ((sec * 100.0) / totalChannelSeconds).roundToInt().coerceIn(0, 100))
            }
            .sortedWith(compareByDescending<EntityWatchStat> { it.timeSpentSeconds }.thenBy { it.title.lowercase() })

        // 2. PLAYLISTS ANALYTICS
        val rawPlaylistStats = allPlaylists.map { pl ->
            val plTitle = if (pl.title.isBlank() || pl.title.equals("undefined", ignoreCase = true)) "My Playlist" else pl.title
            val plVideos = allSavedVideos
                .filter { it.playlistId == pl.playlistId }
                .distinctBy { it.videoId }
            val liveTrackedSec = prefs.getLong("watch_playlist_${pl.playlistId}", 0L)
            val libraryEngagedSec = estimateLibraryEngagementSeconds(plVideos)
            val combinedSec = liveTrackedSec + libraryEngagedSec
            val completedCnt = plVideos.count { it.isCompleted }
            val notesCnt = plVideos.count { it.notes.isNotBlank() }
            Triple(
                plTitle,
                combinedSec,
                EntityWatchStat(
                    idKey = "pl_${pl.playlistId}",
                    title = plTitle,
                    subtitle = "${plVideos.size} वीडियो • $completedCnt पूरे • $notesCnt नोट्स",
                    entityType = WatchEntityType.PLAYLIST,
                    timeSpentSeconds = combinedSec,
                    videoCount = plVideos.size,
                    completedCount = completedCnt,
                    notesCount = notesCnt,
                    sharePercent = 0
                )
            )
        }
        val totalPlaylistSeconds = rawPlaylistStats.sumOf { it.second }.coerceAtLeast(1L)
        val rankedPlaylists = rawPlaylistStats
            .map { (_, sec, stat) ->
                stat.copy(sharePercent = ((sec * 100.0) / totalPlaylistSeconds).roundToInt().coerceIn(0, 100))
            }
            .sortedWith(compareByDescending<EntityWatchStat> { it.timeSpentSeconds }.thenBy { it.title.lowercase() })

        // 3. GROUPS ANALYTICS
        val rawGroupStats = allGroups.map { grp ->
            val grpChannels = allChannels.filter { it.groupId == grp.id }
            val grpChannelNamesLower = grpChannels.map { it.name.trim().lowercase() }.toSet()
            val grpVideos = allSavedVideos
                .filter { it.groupId == grp.id || it.channelName.trim().lowercase() in grpChannelNamesLower }
                .distinctBy { it.videoId }
            val liveTrackedSec = prefs.getLong("watch_group_${grp.id}", 0L) +
                grpChannelNamesLower.sumOf { chLower -> prefs.getLong("watch_channel_$chLower", 0L) }
            val libraryEngagedSec = estimateLibraryEngagementSeconds(grpVideos)
            // If a group has channels but no synced videos yet, provide a small baseline per channel so groups compare cleanly
            val channelBaselineSec = grpChannels.size * 45L
            val combinedSec = liveTrackedSec + libraryEngagedSec + (if (grpVideos.isEmpty()) channelBaselineSec else 0L)
            val completedCnt = grpVideos.count { it.isCompleted }
            val notesCnt = grpVideos.count { it.notes.isNotBlank() }
            Triple(
                grp.name,
                combinedSec,
                EntityWatchStat(
                    idKey = "grp_${grp.id}",
                    title = grp.name,
                    subtitle = "${grpChannels.size} चैनल्स • ${grpVideos.size} वीडियो • $notesCnt नोट्स",
                    entityType = WatchEntityType.GROUP,
                    timeSpentSeconds = combinedSec,
                    videoCount = grpVideos.size,
                    completedCount = completedCnt,
                    notesCount = notesCnt,
                    sharePercent = 0
                )
            )
        }
        val totalGroupSeconds = rawGroupStats.sumOf { it.second }.coerceAtLeast(1L)
        val rankedGroups = rawGroupStats
            .map { (_, sec, stat) ->
                stat.copy(sharePercent = ((sec * 100.0) / totalGroupSeconds).roundToInt().coerceIn(0, 100))
            }
            .sortedWith(compareByDescending<EntityWatchStat> { it.timeSpentSeconds }.thenBy { it.title.lowercase() })

        return mapOf(
            WatchEntityType.CHANNEL to CategoryMostLeastSummary(
                entityType = WatchEntityType.CHANNEL,
                categoryTitleHindi = "📺 YouTube चैनल्स",
                mostSpent = rankedChannels.firstOrNull(),
                leastSpent = if (rankedChannels.size >= 2) rankedChannels.lastOrNull() else rankedChannels.firstOrNull(),
                rankedAll = rankedChannels,
                totalCategorySeconds = rawChannelStats.sumOf { it.second }
            ),
            WatchEntityType.PLAYLIST to CategoryMostLeastSummary(
                entityType = WatchEntityType.PLAYLIST,
                categoryTitleHindi = "🎵 प्लेलिस्ट (Playlists)",
                mostSpent = rankedPlaylists.firstOrNull(),
                leastSpent = if (rankedPlaylists.size >= 2) rankedPlaylists.lastOrNull() else rankedPlaylists.firstOrNull(),
                rankedAll = rankedPlaylists,
                totalCategorySeconds = rawPlaylistStats.sumOf { it.second }
            ),
            WatchEntityType.GROUP to CategoryMostLeastSummary(
                entityType = WatchEntityType.GROUP,
                categoryTitleHindi = "📁 चैनल ग्रुप्स (Groups)",
                mostSpent = rankedGroups.firstOrNull(),
                leastSpent = if (rankedGroups.size >= 2) rankedGroups.lastOrNull() else rankedGroups.firstOrNull(),
                rankedAll = rankedGroups,
                totalCategorySeconds = rawGroupStats.sumOf { it.second }
            )
        )
    }

    private fun syncTodayZonesFromElapsed(context: Context, totalSeconds: Long) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val tKey = todayDateKey()
            val gKey = "day_${tKey}_green"
            val yKey = "day_${tKey}_yellow"
            val rKey = "day_${tKey}_red"

            val existingSum = prefs.getLong(gKey, 0L) + prefs.getLong(yKey, 0L) + prefs.getLong(rKey, 0L)
            if (existingSum < totalSeconds) {
                val green = min(totalSeconds, GREEN_LIMIT_SECONDS)
                val yellow = (min(totalSeconds, DANGER_RED_SECONDS) - GREEN_LIMIT_SECONDS).coerceAtLeast(0L)
                val red = (totalSeconds - DANGER_RED_SECONDS).coerceAtLeast(0L)
                prefs.edit()
                    .putLong(gKey, green)
                    .putLong(yKey, yellow)
                    .putLong(rKey, red)
                    .apply()
            }
        } catch (_: Exception) {}
    }

    fun setElapsedSecondsForPreviewOrReset(context: Context, seconds: Long) {
        ensureInitialized(context)
        val clean = seconds.coerceAtLeast(0L)
        _elapsedSecondsFlow.value = clean
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tKey = todayDateKey()
        val green = min(clean, GREEN_LIMIT_SECONDS)
        val yellow = (min(clean, DANGER_RED_SECONDS) - GREEN_LIMIT_SECONDS).coerceAtLeast(0L)
        val red = (clean - DANGER_RED_SECONDS).coerceAtLeast(0L)
        prefs.edit()
            .putLong(KEY_ELAPSED_SECONDS, clean)
            .putLong(KEY_LAST_SAVED_MS, System.currentTimeMillis())
            .putLong("day_${tKey}_green", green)
            .putLong("day_${tKey}_yellow", yellow)
            .putLong("day_${tKey}_red", red)
            .apply()
        refreshWeeklyUsage(appCtx)
    }

    fun persistSeconds(context: Context, seconds: Long = _elapsedSecondsFlow.value) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_ELAPSED_SECONDS, seconds)
                .putLong(KEY_LAST_SAVED_MS, System.currentTimeMillis())
                .apply()
            refreshWeeklyUsage(context.applicationContext)
        } catch (_: Exception) {}
    }

    fun refreshWeeklyUsage(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

            val list = mutableListOf<DailyZoneUsage>()
            for (daysAgo in 6 downTo 0) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                val dKey = keyFmt.format(cal.time)
                val isToday = daysAgo == 0

                val gSec = prefs.getLong("day_${dKey}_green", 0L)
                val ySec = prefs.getLong("day_${dKey}_yellow", 0L)
                val rSec = prefs.getLong("day_${dKey}_red", 0L)

                list.add(
                    DailyZoneUsage(
                        dateKey = dKey,
                        dayShortLabel = if (isToday) "आज (${dayFmt.format(cal.time)})" else dayFmt.format(cal.time),
                        dateDisplayLabel = dateFmt.format(cal.time),
                        isToday = isToday,
                        greenSeconds = gSec,
                        yellowSeconds = ySec,
                        redSeconds = rSec
                    )
                )
            }
            _weeklyUsageFlow.value = list
        } catch (_: Exception) {}
    }

    fun formatDurationHms(totalSeconds: Long): String {
        val sec = totalSeconds.coerceAtLeast(0L)
        val hours = sec / 3600L
        val minutes = (sec % 3600L) / 60L
        val seconds = sec % 60L
        return if (hours > 0L) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("00:%02d:%02d", minutes, seconds)
        }
    }

    fun formatDurationCompact(totalSeconds: Long): String {
        val sec = totalSeconds.coerceAtLeast(0L)
        val hours = sec / 3600L
        val minutes = (sec % 3600L) / 60L
        val seconds = sec % 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun getZoneForSeconds(totalSeconds: Long): TimerVisualZone {
        return when {
            totalSeconds < GREEN_LIMIT_SECONDS -> TimerVisualZone.GREEN_SAFE
            totalSeconds < YELLOW_LIMIT_SECONDS -> TimerVisualZone.YELLOW_CAUTION
            totalSeconds < DANGER_RED_SECONDS -> TimerVisualZone.AMBER_WARNING
            else -> TimerVisualZone.RED_DANGER
        }
    }

    fun getPaletteForSeconds(totalSeconds: Long): TimerZonePalette {
        return when (getZoneForSeconds(totalSeconds)) {
            TimerVisualZone.GREEN_SAFE -> TimerZonePalette(
                zone = TimerVisualZone.GREEN_SAFE,
                primaryGlowColor = Color(0xFF00E676),
                secondaryGlowColor = Color(0xFF00C853),
                darkSurfaceColor = Color(0xFF062312),
                badgeBgColor = Color(0xFF00E676).copy(alpha = 0.18f),
                textColor = Color(0xFF69F0AE),
                zoneLabelHindi = "🟢 हरा ज़ोन (0–40m)",
                zoneShortBadge = "🟢 SAFE",
                zoneDescriptionHindi = "आप शुरुआती 40 मिनट के सुरक्षित फोकस ज़ोन में हैं। टाइमर हरा जल रहा है।"
            )
            TimerVisualZone.YELLOW_CAUTION -> TimerZonePalette(
                zone = TimerVisualZone.YELLOW_CAUTION,
                primaryGlowColor = Color(0xFFFFEA00),
                secondaryGlowColor = Color(0xFFFFC400),
                darkSurfaceColor = Color(0xFF2B2103),
                badgeBgColor = Color(0xFFFFEA00).copy(alpha = 0.20f),
                textColor = Color(0xFFFFF176),
                zoneLabelHindi = "🟡 पीला ज़ोन (40m–1.5h)",
                zoneShortBadge = "🟡 CAUTION",
                zoneDescriptionHindi = "40 मिनट पूरे हो चुके हैं। अब टाइमर पीला पड़ गया है (डेढ़ घंटे तक पीला ज़ोन)।"
            )
            TimerVisualZone.AMBER_WARNING -> TimerZonePalette(
                zone = TimerVisualZone.AMBER_WARNING,
                primaryGlowColor = Color(0xFFFF9100),
                secondaryGlowColor = Color(0xFFFF6D00),
                darkSurfaceColor = Color(0xFF331604),
                badgeBgColor = Color(0xFFFF9100).copy(alpha = 0.22f),
                textColor = Color(0xFFFFCC80),
                zoneLabelHindi = "🟠 चेतावनी (1.5h–2h)",
                zoneShortBadge = "🟠 1.5h+ WARN",
                zoneDescriptionHindi = "डेढ़ घंटे से ऊपर समय हो गया है! 2 घंटे होते ही यह खतरनाक लाल (Red) हो जाएगा।"
            )
            TimerVisualZone.RED_DANGER -> TimerZonePalette(
                zone = TimerVisualZone.RED_DANGER,
                primaryGlowColor = Color(0xFFFF1744),
                secondaryGlowColor = Color(0xFFD50000),
                darkSurfaceColor = Color(0xFF3A040C),
                badgeBgColor = Color(0xFFFF1744).copy(alpha = 0.26f),
                textColor = Color(0xFFFF8A80),
                zoneLabelHindi = "🔴 खतरनाक रेड! (2h+)",
                zoneShortBadge = "🔴 DANGER 2h+",
                zoneDescriptionHindi = "⚠️ सावधान! ऐप चलाते हुए 2 घंटे से ज़्यादा हो चुके हैं। यह खतरनाक (Red Alert) ज़ोन है — कृपया ब्रेक लें!"
            )
        }
    }
}

/**
 * Lifecycle-aware ticker composable. Place once at the root of `MainApp` so the timer
 * automatically runs whenever the app is open, and pauses/saves when the app is closed,
 * resuming from the exact second on next launch.
 */
@Composable
fun PersistentVisualTimerTickerEffect(
    activeVideo: com.example.data.local.entity.VideoEntity? = null,
    allSavedVideos: List<com.example.data.local.entity.VideoEntity> = emptyList(),
    allChannels: List<com.example.data.local.entity.ChannelEntity> = emptyList(),
    onAppReturnedToForeground: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppInForeground by remember { mutableStateOf(true) }
    var wasPausedInBackground by remember { mutableStateOf(false) }
    var backgroundEnteredAtMs by remember { mutableStateOf(0L) }

    DisposableEffect(context, lifecycleOwner) {
        VisualScreenTimerManager.ensureInitialized(context)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isAppInForeground = true
                    if (wasPausedInBackground) {
                        wasPausedInBackground = false
                        val awayDurationMs = System.currentTimeMillis() - backgroundEnteredAtMs
                        // Only show startup reminder gate again if app was genuinely closed/backgrounded
                        // for 25+ seconds (prevents false triggers during screen rotation or file picker)
                        if (backgroundEnteredAtMs > 0L && awayDurationMs >= 25_000L) {
                            onAppReturnedToForeground()
                        }
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    isAppInForeground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    isAppInForeground = false
                    wasPausedInBackground = true
                    backgroundEnteredAtMs = System.currentTimeMillis()
                    VisualScreenTimerManager.persistSeconds(context)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    VisualScreenTimerManager.persistSeconds(context)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            VisualScreenTimerManager.persistSeconds(context)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAppInForeground, activeVideo?.videoId) {
        VisualScreenTimerManager.ensureInitialized(context)
        while (isAppInForeground) {
            delay(1000L)
            VisualScreenTimerManager.incrementOneSecond(context)
            if (activeVideo != null) {
                VisualScreenTimerManager.recordActiveVideoWatchSecond(
                    context = context,
                    activeVideo = activeVideo,
                    allSavedVideos = allSavedVideos,
                    allChannels = allChannels
                )
            }
        }
    }
}

/**
 * Prominent Glowing Visual Timer Bar shown in the App Header across all screens.
 * Glows Green (0–40m), turns Yellow (40m–1.5h), Amber (1.5h–2h), and glowing Danger Red (2h+).
 * Includes quick action pills to open the Fullscreen Startup Reminder or Weekly Usage Dashboard.
 */
@Composable
fun AppHeaderVisualTimerBanner(
    onOpenReminderScreen: (() -> Unit)? = null,
    onOpenDashboardTab: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val elapsedSeconds by VisualScreenTimerManager.elapsedSecondsFlow.collectAsState()
    var showTimerInspectorDialog by remember { mutableStateOf(false) }

    val palette = remember(elapsedSeconds) {
        VisualScreenTimerManager.getPaletteForSeconds(elapsedSeconds)
    }

    val animatedPrimaryColor by animateColorAsState(
        targetValue = palette.primaryGlowColor,
        animationSpec = tween(durationMillis = 600),
        label = "timerPrimaryGlow"
    )
    val animatedSurfaceColor by animateColorAsState(
        targetValue = palette.darkSurfaceColor,
        animationSpec = tween(durationMillis = 600),
        label = "timerSurfaceColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "timerPulseTransition")
    val pulseDuration = if (palette.zone == TimerVisualZone.RED_DANGER) 550 else 1300
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timerPulseAlpha"
    )

    // Progress ratio across the 2-hour (7200s) spectrum
    val spectrumProgress = remember(elapsedSeconds) {
        (elapsedSeconds.toFloat() / VisualScreenTimerManager.DANGER_RED_SECONDS.toFloat()).coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showTimerInspectorDialog = true }
            .testTag("app_header_visual_timer_banner"),
        color = animatedSurfaceColor,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (palette.zone == TimerVisualZone.RED_DANGER) 1.5.dp else 1.dp,
                    color = animatedPrimaryColor.copy(
                        alpha = if (palette.zone == TimerVisualZone.RED_DANGER) pulseAlpha else 0.65f
                    )
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Glowing LED Orb + Digital Clock Readout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    // Glowing LED Light Indicator
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(animatedPrimaryColor.copy(alpha = 0.28f * pulseAlpha))
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = animatedPrimaryColor)
                                .clip(CircleShape)
                                .background(animatedPrimaryColor)
                        )
                    }

                    // Digital Time Readout Capsule
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = animatedPrimaryColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .testTag("visual_timer_digital_readout")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = if (palette.zone == TimerVisualZone.RED_DANGER) Icons.Default.Warning else Icons.Default.Timer,
                                contentDescription = null,
                                tint = animatedPrimaryColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = VisualScreenTimerManager.formatDurationHms(elapsedSeconds),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = animatedPrimaryColor,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    // Zone Status Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.badgeBgColor,
                        modifier = Modifier
                            .border(
                                width = 0.8.dp,
                                color = animatedPrimaryColor.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .testTag("visual_timer_zone_badge")
                    ) {
                        Text(
                            text = palette.zoneLabelHindi,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Right: Quick Action Pills for Fullscreen Goal Note & Weekly Dashboard
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (onOpenReminderScreen != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clickable { onOpenReminderScreen() }
                                .testTag("header_open_startup_reminder_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = "Open Fullscreen Goal Reminder",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "📌 लक्ष्य",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (onOpenDashboardTab != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = animatedPrimaryColor.copy(alpha = 0.18f),
                            modifier = Modifier
                                .clickable { onOpenDashboardTab() }
                                .testTag("header_open_dashboard_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = "Open Weekly Usage Dashboard",
                                    tint = palette.textColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "📊 7d",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Bottom: 3-Zone Visual Color Spectrum Bar (Green 0-40m | Yellow 40m-1.5h | Red 2h+)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(5.dp)) {
                    val w = size.width
                    val h = size.height
                    val greenEnd = w * (40f / 120f)   // 33.3% (40m / 120m)
                    val yellowEnd = w * (90f / 120f)  // 75.0% (90m / 120m)

                    // Background track segments (dimmed)
                    drawRoundRect(
                        color = Color(0xFF00E676).copy(alpha = 0.22f),
                        topLeft = Offset(0f, 0f),
                        size = Size(greenEnd, h),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFFFEA00).copy(alpha = 0.22f),
                        topLeft = Offset(greenEnd + 2.dp.toPx(), 0f),
                        size = Size((yellowEnd - greenEnd - 2.dp.toPx()).coerceAtLeast(0f), h),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFFF1744).copy(alpha = 0.25f),
                        topLeft = Offset(yellowEnd + 2.dp.toPx(), 0f),
                        size = Size((w - yellowEnd - 2.dp.toPx()).coerceAtLeast(0f), h),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )

                    // Active illuminated fill up to current progress
                    val activeWidth = (w * spectrumProgress).coerceIn(6.dp.toPx(), w)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = when (palette.zone) {
                                TimerVisualZone.GREEN_SAFE -> listOf(Color(0xFF00C853), Color(0xFF00E676))
                                TimerVisualZone.YELLOW_CAUTION -> listOf(Color(0xFF00E676), Color(0xFFFFEA00))
                                TimerVisualZone.AMBER_WARNING -> listOf(Color(0xFF00E676), Color(0xFFFFEA00), Color(0xFFFF9100))
                                TimerVisualZone.RED_DANGER -> listOf(Color(0xFF00E676), Color(0xFFFFEA00), Color(0xFFFF1744))
                            },
                            startX = 0f,
                            endX = activeWidth
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(activeWidth, h),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )

                    // Glowing current position indicator dot
                    drawCircle(
                        color = Color.White,
                        radius = (h * 0.65f),
                        center = Offset(activeWidth.coerceIn(4.dp.toPx(), w - 4.dp.toPx()), h / 2f)
                    )
                }
            }
        }
    }

    if (showTimerInspectorDialog) {
        VisualTimerInspectorDialog(
            elapsedSeconds = elapsedSeconds,
            palette = palette,
            onDismiss = { showTimerInspectorDialog = false },
            onResetOrPreviewSeconds = { targetSec ->
                VisualScreenTimerManager.setElapsedSecondsForPreviewOrReset(context, targetSec)
            }
        )
    }
}

/**
 * Compact Draggable Glowing Visual Timer Pill for Fullscreen & Video Player Mode.
 * Ensures that even when watching videos in Landscape or Portrait Fullscreen,
 * the glowing Green / Yellow / Red timer remains visible inside the app.
 */
@Composable
fun FloatingPlayerVisualTimerPill(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val elapsedSeconds by VisualScreenTimerManager.elapsedSecondsFlow.collectAsState()
    var showTimerInspectorDialog by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val palette = remember(elapsedSeconds) {
        VisualScreenTimerManager.getPaletteForSeconds(elapsedSeconds)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "floatingTimerPulse")
    val pulseDuration = if (palette.zone == TimerVisualZone.RED_DANGER) 500 else 1200
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingPulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = palette.darkSurfaceColor.copy(alpha = 0.88f),
        shadowElevation = 6.dp,
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .clickable { showTimerInspectorDialog = true }
            .border(
                width = if (palette.zone == TimerVisualZone.RED_DANGER) 1.8.dp else 1.2.dp,
                color = palette.primaryGlowColor.copy(
                    alpha = if (palette.zone == TimerVisualZone.RED_DANGER) pulseAlpha else 0.85f
                ),
                shape = RoundedCornerShape(50)
            )
            .testTag("fullscreen_visual_timer_hud")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            // Glowing LED Indicator Dot
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(palette.primaryGlowColor.copy(alpha = 0.35f * pulseAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.primaryGlowColor)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = VisualScreenTimerManager.formatDurationHms(elapsedSeconds),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.primaryGlowColor
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = palette.zoneShortBadge,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textColor
            )
        }
    }

    if (showTimerInspectorDialog) {
        VisualTimerInspectorDialog(
            elapsedSeconds = elapsedSeconds,
            palette = palette,
            onDismiss = { showTimerInspectorDialog = false },
            onResetOrPreviewSeconds = { targetSec ->
                VisualScreenTimerManager.setElapsedSecondsForPreviewOrReset(context, targetSec)
            }
        )
    }
}

/**
 * Interactive Dialog showing the large glowing circular gauge, the 3 color zones,
 * and controls to preview each zone or reset the timer manually if desired.
 */
@Composable
fun VisualTimerInspectorDialog(
    elapsedSeconds: Long,
    palette: TimerZonePalette,
    onDismiss: () -> Unit,
    onResetOrPreviewSeconds: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("visual_timer_inspector_dialog"),
        containerColor = Color(0xFF101418),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(palette.primaryGlowColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "⏱️ स्मार्ट विजुअल ऐप टाइमर",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "ऐप बंद करके दोबारा खोलने पर भी वहीं से आगे चलता है",
                        fontSize = 11.sp,
                        color = Color(0xFFB0BEC5)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular Glowing Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(150.dp)
                ) {
                    val progress = min(1f, elapsedSeconds.toFloat() / VisualScreenTimerManager.DANGER_RED_SECONDS.toFloat())
                    Canvas(modifier = Modifier.size(140.dp)) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.12f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = palette.primaryGlowColor,
                            startAngle = 135f,
                            sweepAngle = 270f * progress,
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = VisualScreenTimerManager.formatDurationHms(elapsedSeconds),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.primaryGlowColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.badgeBgColor
                        ) {
                            Text(
                                text = palette.zoneShortBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.textColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Current Zone Message Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.darkSurfaceColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.primaryGlowColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = palette.zoneLabelHindi,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.primaryGlowColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = palette.zoneDescriptionHindi,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                // 3-Stage Color Rules Breakdown
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎨 टाइमर के 3 कलर सिग्नल (टेस्ट/चेक करने के लिए टैप करें):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCFD8DC)
                        )

                        // 1. Green Zone Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF062312))
                                .clickable { onResetOrPreviewSeconds(15 * 60L) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("timer_preview_green_zone")
                        ) {
                            Text(
                                text = "🟢 0 – 40 मिनट : हरा (Safe Focus)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                            Text("टेस्ट (15m)", fontSize = 10.sp, color = Color(0xFF69F0AE))
                        }

                        // 2. Yellow Zone Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2B2103))
                                .clickable { onResetOrPreviewSeconds(55 * 60L) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("timer_preview_yellow_zone")
                        ) {
                            Text(
                                text = "🟡 40m – 1.5 घंटा : पीला (Caution)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFEA00)
                            )
                            Text("टेस्ट (55m)", fontSize = 10.sp, color = Color(0xFFFFF176))
                        }

                        // 3. Red Danger Zone Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF3A040C))
                                .clickable { onResetOrPreviewSeconds(125 * 60L) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("timer_preview_red_zone")
                        ) {
                            Text(
                                text = "🔴 2 घंटे से ऊपर : खतरनाक रेड (Danger!)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF1744)
                            )
                            Text("टेस्ट (2h 5m)", fontSize = 10.sp, color = Color(0xFFFF8A80))
                        }
                    }
                }

                // Manual Reset Button (Only if user explicitly clicks it)
                OutlinedButton(
                    onClick = { onResetOrPreviewSeconds(0L) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visual_timer_manual_reset_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF80CBC4))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🔄 नए सेशन के लिए 00:00:00 पर रीसेट करें",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("बंद करें (Close)", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}
