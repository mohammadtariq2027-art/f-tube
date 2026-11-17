package com.example.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.outlined.CheckCircle
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import com.example.util.YouTubeSyncHelper
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.VideoEntity

data class VideoSeekCommand(
    val seconds: Int,
    val commandId: Long = System.nanoTime()
)

data class SceneNoteEntry(
    val lineIndex: Int,
    val rawLine: String,
    val timestampLabel: String?,
    val timestampSeconds: Int?,
    val noteBody: String
)

fun formatSecondsToTimestamp(totalSeconds: Int): String {
    val safeSec = totalSeconds.coerceAtLeast(0)
    val hours = safeSec / 3600
    val minutes = (safeSec % 3600) / 60
    val seconds = safeSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun parseTimestampToSeconds(rawTimestamp: String): Int? {
    val cleaned = rawTimestamp.trim()
        .removePrefix("[")
        .removeSuffix("]")
        .removePrefix("(")
        .removeSuffix(")")
        .trim()
    val parts = cleaned.split(":").map { it.trim().toIntOrNull() ?: return null }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}

fun parseDurationStringToSeconds(rawDuration: String, videoId: String): Int {
    val cleanDur = com.example.util.DurationUtils.formatCleanDuration(rawDuration, videoId)
    val parsed = parseTimestampToSeconds(cleanDur)
    if (parsed != null && parsed > 0) return parsed
    return parseTimestampToSeconds(rawDuration) ?: 0
}

private val TIMESTAMP_LINE_REGEX = Regex("""^\s*(?:\[|\()?\s*(\d{1,2}:\d{2}(?::\d{2})?)\s*(?:\]|\))?\s*[-–:—]?\s*(.*)$""")
private val ANYWHERE_TIMESTAMP_REGEX = Regex("""(?:\[|\()?(\d{1,2}:\d{2}(?::\d{2})?)(?:\]|\))?""")

fun parseSceneNotes(notesText: String): List<SceneNoteEntry> {
    if (notesText.isBlank()) return emptyList()
    return notesText.lines()
        .mapIndexedNotNull { idx, line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapIndexedNotNull null
            val startMatch = TIMESTAMP_LINE_REGEX.find(trimmed)
            if (startMatch != null) {
                val tsStr = startMatch.groupValues[1]
                val sec = parseTimestampToSeconds(tsStr)
                val body = startMatch.groupValues[2].trim().ifBlank { "Scene Note ($tsStr)" }
                SceneNoteEntry(
                    lineIndex = idx,
                    rawLine = trimmed,
                    timestampLabel = tsStr,
                    timestampSeconds = sec,
                    noteBody = body
                )
            } else {
                val anywhereMatch = ANYWHERE_TIMESTAMP_REGEX.find(trimmed)
                if (anywhereMatch != null) {
                    val tsStr = anywhereMatch.groupValues[1]
                    val sec = parseTimestampToSeconds(tsStr)
                    val cleanedBody = trimmed.replace(anywhereMatch.value, "").trim(' ', '-', '–', ':').ifBlank { trimmed }
                    SceneNoteEntry(
                        lineIndex = idx,
                        rawLine = trimmed,
                        timestampLabel = tsStr,
                        timestampSeconds = sec,
                        noteBody = cleanedBody
                    )
                } else {
                    SceneNoteEntry(
                        lineIndex = idx,
                        rawLine = trimmed,
                        timestampLabel = null,
                        timestampSeconds = null,
                        noteBody = trimmed
                    )
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPlayerView(
    video: VideoEntity,
    headerTitle: String,
    allPlaylists: List<PlaylistEntity> = emptyList(),
    allSavedVideos: List<VideoEntity> = emptyList(),
    recentPlaylistIds: List<String> = emptyList(),
    onBack: () -> Unit,
    onToggleCompleted: (VideoEntity) -> Unit,
    onSaveToPlaylist: ((VideoEntity, String) -> Unit)? = null,
    onCreatePlaylistAndSaveVideo: ((String, VideoEntity) -> Unit)? = null,
    onFetchDescription: (suspend (String) -> String)? = null,
    onUpdateNotes: ((Long, String) -> Unit)? = null,
    onDeleteVideo: ((Long) -> Unit)? = null,
    onRefreshYouTubePlaylists: (() -> Unit)? = null,
    initialSeekSeconds: Int? = null
) {
    // 🛡️ Focus Shield Interceptor: If this is flagged as distraction, allow user to confirm or override
    var userAllowedByOverride by remember(video.id) { mutableStateOf(false) }
    val isDistraction = remember(video.title, video.channelName) {
        com.example.util.FocusShield.isDistractionVideo(video.title, video.channelName)
    }

    if (isDistraction && !userAllowedByOverride) {
        DistractionBlockedCurtain(
            videoTitle = video.title,
            onBack = onBack,
            onAllowAndPlay = {
                userAllowedByOverride = true
            },
            onDeleteAndBack = {
                onDeleteVideo?.invoke(video.id)
                onBack()
            }
        )
        return
    }

    var isPlayerLoading by remember(video.videoId) { mutableStateOf(true) }
    var isVideoEnded by remember(video.videoId) { mutableStateOf(false) }
    var replayTrigger by remember(video.videoId) { mutableStateOf(0) }
    var pauseTrigger by remember(video.videoId) { mutableStateOf(0) }
    var togglePlayPauseTrigger by remember(video.videoId) { mutableStateOf(0) }
    var isFullscreenHudVisible by remember(video.videoId) { mutableStateOf(true) }
    var currentPlaybackSeconds by remember(video.videoId, initialSeekSeconds) {
        mutableStateOf(initialSeekSeconds?.coerceAtLeast(0) ?: 0)
    }
    var videoDurationSeconds by remember(video.videoId, video.duration) {
        mutableStateOf(parseDurationStringToSeconds(video.duration, video.videoId))
    }
    var isVideoPlaying by remember(video.videoId) { mutableStateOf(false) }
    var isScrubbingSeekBar by remember(video.videoId) { mutableStateOf(false) }
    var scrubSliderSeconds by remember(video.videoId) { mutableFloatStateOf(0f) }
    var lastLiveSeekDispatchMs by remember(video.videoId) { mutableStateOf(0L) }
    var seekCommand by remember(video.videoId, initialSeekSeconds) {
        mutableStateOf(
            if (initialSeekSeconds != null && initialSeekSeconds > 0) {
                VideoSeekCommand(seconds = initialSeekSeconds)
            } else null
        )
    }
    var lastSeekFeedback by remember(video.videoId, initialSeekSeconds) {
        mutableStateOf(
            if (initialSeekSeconds != null && initialSeekSeconds > 0) {
                "▶ Playing from scene [${formatSecondsToTimestamp(initialSeekSeconds)}]!"
            } else null
        )
    }
    var quickSceneTimestampInput by remember(video.videoId) { mutableStateOf("0:00") }
    var isQuickTimestampLocked by remember(video.videoId) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var isTitleExpanded by remember(video.videoId) { mutableStateOf(false) }
    var quickSceneNoteText by remember(video.videoId) { mutableStateOf("") }
    var isFullscreenNoteDrawerOpen by remember(video.videoId) { mutableStateOf(false) }
    var sceneNoteSearchQuery by remember(video.videoId) { mutableStateOf("") }
    var isSceneNoteSearchActive by remember(video.videoId) { mutableStateOf(false) }
    var isCompleted by remember(video.id, video.isCompleted) { mutableStateOf(video.isCompleted) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var currentDescription by remember(video.id) { mutableStateOf(video.description) }
    var showSaveToPlaylistDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showDeleteVideoConfirmDialog by remember { mutableStateOf(false) }
    val existingNoteFromAllVideos = remember(video.videoId, video.notes, allSavedVideos) {
        if (video.notes.isNotBlank()) video.notes
        else allSavedVideos.firstOrNull { it.videoId == video.videoId && it.notes.isNotBlank() }?.notes.orEmpty()
    }
    var currentNotes by remember(video.id, existingNoteFromAllVideos) { mutableStateOf(existingNoteFromAllVideos) }
    val parsedSceneNotes = remember(currentNotes) { parseSceneNotes(currentNotes) }
    val filteredSceneNotes = remember(parsedSceneNotes, sceneNoteSearchQuery) {
        if (sceneNoteSearchQuery.isBlank()) parsedSceneNotes
        else {
            val qTokens = com.example.util.AiSmartSearchEngine.tokenizeMeaningful(sceneNoteSearchQuery)
                .ifEmpty { listOf(sceneNoteSearchQuery.trim().lowercase()) }
            val expanded = qTokens.flatMap { com.example.util.AiSmartSearchEngine.expandTokenSynonyms(it) }.toSet()
            val approxSec = com.example.util.AiSmartSearchEngine.extractTargetTimestampSeconds(sceneNoteSearchQuery)
            parsedSceneNotes.filter { entry ->
                val rawLower = entry.rawLine.lowercase()
                val rawPhonetic = com.example.util.AiSmartSearchEngine.toPhoneticRoman(rawLower)
                val qPhonetic = com.example.util.AiSmartSearchEngine.toPhoneticRoman(sceneNoteSearchQuery.lowercase())
                val directMatch = rawLower.contains(sceneNoteSearchQuery, ignoreCase = true) ||
                    (qPhonetic.length >= 3 && rawPhonetic.contains(qPhonetic))
                val timeMatch = approxSec != null && entry.timestampSeconds != null &&
                    kotlin.math.abs(entry.timestampSeconds - approxSec) <= 45
                val entryWords = com.example.util.AiSmartSearchEngine.tokenizeMeaningful(rawLower)
                val semanticMatch = entryWords.any { ew ->
                    val ewp = com.example.util.AiSmartSearchEngine.toPhoneticRoman(ew)
                    ew in expanded || ewp in expanded ||
                        qTokens.any { qt -> com.example.util.AiSmartSearchEngine.wordSimilarity(qt, ew) >= 0.66 }
                }
                directMatch || timeMatch || semanticMatch
            }
        }
    }

    fun jumpToSceneTimestamp(seconds: Int, label: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isVideoEnded = false
        currentPlaybackSeconds = seconds
        scrubSliderSeconds = seconds.toFloat()
        seekCommand = VideoSeekCommand(seconds = seconds)
        lastSeekFeedback = "▶ Playing from scene [$label]!"
    }

    fun addSceneNoteEntry(timestampStr: String, noteContent: String) {
        val cleanContent = noteContent.trim()
        if (cleanContent.isBlank()) return
        val cleanTs = timestampStr.trim().ifBlank { formatSecondsToTimestamp(currentPlaybackSeconds) }
        val formattedEntry = "[$cleanTs] $cleanContent"
        val updated = if (currentNotes.isBlank()) {
            formattedEntry
        } else {
            "${currentNotes.trim()}\n$formattedEntry"
        }
        currentNotes = updated
        onUpdateNotes?.invoke(video.id, updated)
    }

    val savedInPlaylists = remember(video.videoId, video.playlistId, allSavedVideos, allPlaylists) {
        val plIds = mutableSetOf<String>()
        if (!video.playlistId.isNullOrBlank()) {
            plIds.add(video.playlistId)
        }
        for (v in allSavedVideos) {
            if (v.videoId == video.videoId && !v.playlistId.isNullOrBlank()) {
                plIds.add(v.playlistId)
            }
        }
        allPlaylists.filter { it.playlistId in plIds }
    }
    // Auto-Orientation & Automatic Fullscreen Management:
    // Detect Portrait (Shorts / 9:16 vertical) vs Landscape (16:9 widescreen)
    var detectedPortraitFromPlayer by remember(video.videoId) { mutableStateOf<Boolean?>(null) }
    val isMetadataPortrait = remember(video.title, video.duration, video.videoId) {
        val cleanDur = com.example.util.DurationUtils.formatCleanDuration(video.duration, video.videoId)
        val parts = cleanDur.split(":").mapNotNull { it.trim().toIntOrNull() }
        val durationSec = when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> Int.MAX_VALUE
        }
        video.title.contains("#shorts", ignoreCase = true) ||
        video.title.contains("#short", ignoreCase = true) ||
        video.title.contains("shorts", ignoreCase = true) ||
        video.title.contains("#vertical", ignoreCase = true) ||
        video.title.contains("#reels", ignoreCase = true) ||
        (durationSec in 1..62)
    }
    val isVerticalVideo = detectedPortraitFromPlayer ?: isMetadataPortrait

    // Clicking a video opens the full Video Page first (with Description, Save to Playlist, Notes & all options).
    // When the user taps Fullscreen, it enters Fullscreen in Portrait or Landscape according to video orientation.
    var isAppFullscreen by remember(video.videoId) { mutableStateOf(false) }

    // Two-Finger Pinch-to-Zoom state (1.0f = original fit, pinch out with 2 fingers up to 2.5f zoom in, pinch in to zoom out)
    var pinchZoomScale by remember(video.videoId) { mutableFloatStateOf(1.0f) }
    var isActivelyPinching by remember(video.videoId) { mutableStateOf(false) }
    val targetZoomScale = if (!isAppFullscreen) 1.0f else pinchZoomScale
    val animatedZoomScale by animateFloatAsState(
        targetValue = targetZoomScale,
        animationSpec = tween(durationMillis = if (isActivelyPinching) 30 else 150),
        label = "fullscreenPinchZoomScale"
    )

    val context = LocalContext.current
    fun Context.findActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // Left Side Vertical Seek Bar: Brightness State (0.05f .. 1.0f)
    var playerBrightness by remember {
        val currentWinBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (currentWinBrightness in 0.05f..1.0f) currentWinBrightness else 0.85f)
    }
    var isAdjustingBrightness by remember { mutableStateOf(false) }

    // Right Side Vertical Seek Bar: Volume State (0.0f .. 1.0f)
    var playerVolume by remember {
        val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        val curVol = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 11).coerceIn(0, maxVol)
        mutableFloatStateOf((curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f))
    }
    var isAdjustingVolume by remember { mutableStateOf(false) }

    fun applyPlayerBrightness(newBrightness: Float) {
        val clamped = newBrightness.coerceIn(0.05f, 1.0f)
        playerBrightness = clamped
        try {
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = clamped
                win.attributes = lp
            }
        } catch (_: Exception) {}
    }

    fun applyPlayerVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0f, 1f)
        playerVolume = clamped
        try {
            audioManager?.let { am ->
                val maxV = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val targetIndex = (clamped * maxV).roundToInt().coerceIn(0, maxV)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            }
        } catch (_: Exception) {}
    }

    // Double-Tap Visual Feedback State (-1 = Left -10s, 0 = Center Play/Pause, 1 = Right +10s)
    var doubleTapFeedbackZone by remember { mutableStateOf<Int?>(null) }
    var doubleTapFeedbackTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(doubleTapFeedbackTick) {
        if (doubleTapFeedbackZone != null) {
            delay(650L)
            doubleTapFeedbackZone = null
        }
    }

    // Auto-hide player controls & seek bar overlay after ~3.0 seconds of inactivity
    // Keeps controls visible while scrubbing seek bar or adjusting volume/brightness!
    var controlsRevealTick by remember(video.videoId) { mutableStateOf(1) }
    var areFullscreenIconsVisible by remember(video.videoId) { mutableStateOf(true) }
    val isInteractingWithAnyBar = isScrubbingSeekBar || isAdjustingBrightness || isAdjustingVolume
    LaunchedEffect(controlsRevealTick, isAppFullscreen, isInteractingWithAnyBar) {
        areFullscreenIconsVisible = true
        if (!isInteractingWithAnyBar) {
            delay(3000L)
            if (!isScrubbingSeekBar && !isAdjustingBrightness && !isAdjustingVolume) {
                areFullscreenIconsVisible = false
            }
        }
    }

    // Press-and-Hold Dynamic Speed Slide state (2.0x on hold, slide Right -> 2.5x..4.0x, slide Left -> 1.5x..0.5x, release -> 1.0x)
    var isHoldSpeedActive by remember(video.videoId) { mutableStateOf(false) }
    var activeHoldSpeed by remember(video.videoId) { mutableFloatStateOf(1.0f) }

    // HTML5 native Fullscreen view holder
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Fetch description asynchronously if missing
    LaunchedEffect(video.videoId) {
        if (currentDescription.isBlank() && onFetchDescription != null) {
            val fetched = onFetchDescription(video.videoId)
            if (fetched.isNotBlank()) {
                currentDescription = fetched
            }
        }
    }

    DisposableEffect(isAppFullscreen, customFullscreenView, isVerticalVideo) {
        val inFullscreen = isAppFullscreen || customFullscreenView != null
        if (inFullscreen && activity != null) {
            activity.requestedOrientation = if (isVerticalVideo) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            try {
                androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (_: Exception) {}
        } else if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            try {
                androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                    show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            } catch (_: Exception) {}
        }
        onDispose {
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                try {
                    androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                        show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Back handling for Fullscreen modes and Fullscreen Note Drawer
    BackHandler(enabled = isFullscreenNoteDrawerOpen || customFullscreenView != null || isAppFullscreen) {
        when {
            isFullscreenNoteDrawerOpen -> {
                isFullscreenNoteDrawerOpen = false
            }
            customFullscreenView != null -> {
                customViewCallback?.onCustomViewHidden()
                customFullscreenView = null
                customViewCallback = null
            }
            isAppFullscreen -> {
                isAppFullscreen = false
            }
        }
    }

    val effectiveDurationSec = maxOf(videoDurationSeconds, currentPlaybackSeconds + 1, 60)

    Box(modifier = Modifier.fillMaxSize()) {
    // Continuous Single Player Layout (Seamless fullscreen without unmounting WebView)
    Scaffold(
        topBar = {
            if (!isAppFullscreen) {
                TopAppBar(
                    windowInsets = WindowInsets.statusBars,
                    title = {
                        Column {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Distraction-Free • Autoplay Disabled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isAppFullscreen = true },
                            modifier = Modifier.testTag("player_fullscreen_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Full Screen"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isAppFullscreen) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
                .background(Color.Black)
        ) {
            // Video Screen (Expands to fill screen in fullscreen mode WITHOUT unmounting the WebView!)
            Box(
                modifier = (if (isAppFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                })
                    .clipToBounds()
                    .background(Color.Black)
            ) {
                ZeroSuggestionPlayer(
                    videoId = video.videoId,
                    initialSeekSeconds = initialSeekSeconds,
                    replayTrigger = replayTrigger,
                    pauseTrigger = pauseTrigger,
                    togglePlayPauseTrigger = togglePlayPauseTrigger,
                    seekCommand = seekCommand,
                    fullscreenZoomMode = "FIT_ORIGINAL",
                    currentZoomScale = animatedZoomScale,
                    playerVolumePercent = (playerVolume * 100f).roundToInt().coerceIn(0, 100),
                    isVideoPlaying = isVideoPlaying,
                    onHoldSpeedChanged = { holding, speed ->
                        if (holding && (!isHoldSpeedActive || abs(speed - activeHoldSpeed) >= 0.2f)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        isHoldSpeedActive = holding
                        activeHoldSpeed = speed
                    },
                    onPinchZoomChanged = { pinching, scaleDelta ->
                        isActivelyPinching = pinching
                        if (pinching && scaleDelta != 1.0f) {
                            pinchZoomScale = (pinchZoomScale * scaleDelta).coerceIn(1.0f, 2.5f)
                        }
                    },
                    onScreenTapped = {
                        controlsRevealTick++
                    },
                    onDoubleTapAction = { zone ->
                        // zone: -1 = Left (-10s backward), 0 = Center (Play/Pause), 1 = Right (+10s forward)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        doubleTapFeedbackZone = zone
                        doubleTapFeedbackTick++
                        when (zone) {
                            -1 -> {
                                val target = (currentPlaybackSeconds - 10).coerceAtLeast(0)
                                jumpToSceneTimestamp(target, formatSecondsToTimestamp(target))
                            }
                            0 -> {
                                togglePlayPauseTrigger++
                            }
                            1 -> {
                                val target = (currentPlaybackSeconds + 10).coerceAtMost(effectiveDurationSec)
                                jumpToSceneTimestamp(target, formatSecondsToTimestamp(target))
                            }
                        }
                    },
                    onSideVerticalDelta = { side, deltaFraction ->
                        // side: -1 = Left (Brightness Up/Down), 1 = Right (Volume Up/Down) — completely invisible!
                        if (side < 0) {
                            applyPlayerBrightness((playerBrightness + deltaFraction).coerceIn(0.05f, 1.0f))
                        } else if (side > 0) {
                            applyPlayerVolume((playerVolume + deltaFraction).coerceIn(0.0f, 1.0f))
                        }
                    },
                    onTimelineSeekRatio = { ratio, isFinished ->
                        val clampedRatio = ratio.coerceIn(0f, 1f)
                        val targetSec = (clampedRatio * effectiveDurationSec).roundToInt().coerceIn(0, effectiveDurationSec)
                        if (!isFinished) {
                            isScrubbingSeekBar = true
                            scrubSliderSeconds = clampedRatio * effectiveDurationSec
                            val nowMs = SystemClock.uptimeMillis()
                            if (nowMs - lastLiveSeekDispatchMs >= 140L) {
                                lastLiveSeekDispatchMs = nowMs
                                currentPlaybackSeconds = targetSec
                                seekCommand = VideoSeekCommand(seconds = targetSec)
                            }
                        } else {
                            isScrubbingSeekBar = false
                            jumpToSceneTimestamp(targetSec, formatSecondsToTimestamp(targetSec))
                            controlsRevealTick++
                        }
                    },
                    onCurrentTimeChanged = { sec ->
                        if (!isScrubbingSeekBar) {
                            currentPlaybackSeconds = sec
                            scrubSliderSeconds = sec.toFloat()
                        }
                        if (sec > videoDurationSeconds) {
                            videoDurationSeconds = sec + 30
                        }
                        if (!isQuickTimestampLocked && quickSceneNoteText.isBlank()) {
                            quickSceneTimestampInput = formatSecondsToTimestamp(sec)
                        }
                    },
                    onDurationChanged = { durSec ->
                        if (durSec > 0) {
                            videoDurationSeconds = durSec
                        }
                    },
                    onPlayingStateChanged = { playing ->
                        isVideoPlaying = playing
                    },
                    onVideoOrientationDetected = { isPortrait ->
                        detectedPortraitFromPlayer = isPortrait
                    },
                    onLoadingChanged = { isPlayerLoading = it },
                    onVideoEnded = { isVideoEnded = true },
                    onShowFullscreen = { _, callback ->
                        try {
                            callback.onCustomViewHidden()
                        } catch (_: Exception) {}
                        isAppFullscreen = !isAppFullscreen
                        controlsRevealTick++
                    },
                    onHideFullscreen = {
                        customFullscreenView = null
                        customViewCallback = null
                        isAppFullscreen = false
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer(
                            scaleX = animatedZoomScale,
                            scaleY = animatedZoomScale
                        )
                )

                // Software Dimmer Veil so Brightness Control works visually on the video brightness itself
                val brightnessDimAlpha = ((1f - playerBrightness) * 0.72f).coerceIn(0f, 0.72f)
                if (brightnessDimAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = brightnessDimAlpha))
                    )
                }

                // === DOUBLE-TAP FEEDBACK ANIMATION OVERLAY (Left = -10s, Center = Play/Pause, Right = +10s) ===
                doubleTapFeedbackZone?.let { zone ->
                    val alignment = when (zone) {
                        -1 -> Alignment.CenterStart
                        1 -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(alignment)
                            .padding(horizontal = 42.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .testTag("double_tap_feedback_indicator")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            when (zone) {
                                -1 -> {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "10 seconds backward",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "10s",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                1 -> {
                                    Text(
                                        text = "10s",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "10 seconds forward",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play or Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // === TRANSPARENT PRESS-AND-HOLD + SLIDE SPEED INDICATOR (ONLY SHOWN WHILE HOLDING SCREEN) ===
                if (isHoldSpeedActive) {
                    val speedLevels = remember { listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f) }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.28f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 18.dp, start = 16.dp, end = 16.dp)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.28f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .testTag("fullscreen_hold_speed_hud")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "⏩ ${activeHoldSpeed}x",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Transparent 8-Step Speed Level Track (0.5x .. 4.0x)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                speedLevels.forEach { lvl ->
                                    val isCurrent = abs(lvl - activeHoldSpeed) < 0.05f
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) {
                                            Color(0xFF00E676).copy(alpha = 0.48f)
                                        } else {
                                            Color.White.copy(alpha = 0.10f)
                                        },
                                        modifier = Modifier.border(
                                            width = if (isCurrent) 1.dp else 0.dp,
                                            color = if (isCurrent) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    ) {
                                        Text(
                                            text = "${lvl}x",
                                            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.72f),
                                            fontSize = if (isCurrent) 11.sp else 9.sp,
                                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                                            modifier = Modifier.padding(
                                                horizontal = if (isCurrent) 7.dp else 4.dp,
                                                vertical = if (isCurrent) 3.dp else 2.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // === TRANSPARENT PINCH-TO-ZOOM INDICATOR (ONLY SHOWN WHILE PINCHING WITH 2 FINGERS) ===
                if (isActivelyPinching && isAppFullscreen) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.28f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Text(
                            text = "🔍 ${String.format(java.util.Locale.US, "%.1fx", pinchZoomScale)}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // === CLEAN AUTO-HIDING FULLSCREEN TOP ICONS (Appears on tap, then hides completely so ONLY video plays) ===
                val showPlayerOverlays = (areFullscreenIconsVisible || isInteractingWithAnyBar) && !isHoldSpeedActive && !isPlayerLoading && !isVideoEnded
                val overlayAlpha by animateFloatAsState(
                    targetValue = if (showPlayerOverlays) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "playerOverlayAlpha"
                )

                if (isAppFullscreen && showPlayerOverlays) {
                    // Top-Left: Back Icon + Icon-Only Orientation Toggle (No text, no clutter)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.38f)
                        ) {
                            IconButton(
                                onClick = { isAppFullscreen = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit Fullscreen to Video Page",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // Icon-Only Orientation Toggle (Portrait / Landscape icon ONLY — zero text!)
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.38f),
                            modifier = Modifier.testTag("fullscreen_orientation_mode_badge")
                        ) {
                            IconButton(
                                onClick = {
                                    detectedPortraitFromPlayer = !isVerticalVideo
                                    controlsRevealTick++
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVerticalVideo) {
                                        Icons.Default.StayCurrentPortrait
                                    } else {
                                        Icons.Default.StayCurrentLandscape
                                    },
                                    contentDescription = "Toggle Orientation",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // Top-Right: Exit Fullscreen / Compact View Icon Only
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.38f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isFullscreenNoteDrawerOpen = false
                                isAppFullscreen = false
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("player_exit_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "Switch to Compact View",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // === LEFT VERTICAL SEEK BAR: BRIGHTNESS CONTROL (Click/Slide Up = +Brightness, Down = -Brightness) ===
                if (!isPlayerLoading && !isVideoEnded) {
                    VerticalSideSeekBar(
                        value = playerBrightness,
                        icon = Icons.Default.LightMode,
                        accentColor = Color(0xFFFFD54F),
                        alpha = overlayAlpha,
                        onValueChange = { newVal ->
                            isAdjustingBrightness = true
                            applyPlayerBrightness(newVal)
                            controlsRevealTick++
                        },
                        onValueChangeFinished = {
                            isAdjustingBrightness = false
                            controlsRevealTick++
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = if (isAppFullscreen) 14.dp else 8.dp)
                            .testTag("player_brightness_seek_bar")
                    )

                    // === RIGHT VERTICAL SEEK BAR: VOLUME CONTROL (Click/Slide Up = +Volume, Down = -Volume) ===
                    VerticalSideSeekBar(
                        value = playerVolume,
                        icon = if (playerVolume <= 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        accentColor = Color(0xFF00E5FF),
                        alpha = overlayAlpha,
                        onValueChange = { newVal ->
                            isAdjustingVolume = true
                            applyPlayerVolume(newVal)
                            controlsRevealTick++
                        },
                        onValueChangeFinished = {
                            isAdjustingVolume = false
                            controlsRevealTick++
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = if (isAppFullscreen) 14.dp else 8.dp)
                            .testTag("player_volume_seek_bar")
                    )
                }

                // === ULTRA-MINIMAL & MODERN UNSCALED BOTTOM SEEK BAR ===
                // Always mounted outside ZeroSuggestionPlayer's graphicsLayer(scaleX, scaleY) so touch coordinates
                // map 100% accurately to [0.0 .. 1.0] of the video timeline regardless of the current zoom level!
                // Contains ZERO text buttons ('-10s', '+10s', 'Play' removed as requested).
                if (!isPlayerLoading && !isVideoEnded) {
                    val activeDisplaySeconds = if (isScrubbingSeekBar) {
                        scrubSliderSeconds.roundToInt().coerceIn(0, effectiveDurationSec)
                    } else {
                        currentPlaybackSeconds.coerceIn(0, effectiveDurationSec)
                    }
                    val progressFraction = (if (isScrubbingSeekBar) {
                        scrubSliderSeconds / effectiveDurationSec.toFloat().coerceAtLeast(1f)
                    } else {
                        currentPlaybackSeconds.toFloat() / effectiveDurationSec.toFloat().coerceAtLeast(1f)
                    }).coerceIn(0f, 1f)

                    MinimalModernSeekBar(
                        progressFraction = progressFraction,
                        currentSeconds = activeDisplaySeconds,
                        totalSeconds = effectiveDurationSec,
                        isScrubbing = isScrubbingSeekBar,
                        overlayAlpha = overlayAlpha,
                        isAppFullscreen = isAppFullscreen,
                        pinchZoomScale = pinchZoomScale,
                        onScrubFractionChanged = { ratio ->
                            if (!isScrubbingSeekBar) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            isScrubbingSeekBar = true
                            val targetFloat = ratio.coerceIn(0f, 1f) * effectiveDurationSec
                            scrubSliderSeconds = targetFloat
                            val nowMs = SystemClock.uptimeMillis()
                            if (nowMs - lastLiveSeekDispatchMs >= 140L) {
                                lastLiveSeekDispatchMs = nowMs
                                val liveSec = targetFloat.roundToInt().coerceIn(0, effectiveDurationSec)
                                currentPlaybackSeconds = liveSec
                                seekCommand = VideoSeekCommand(seconds = liveSec)
                            }
                        },
                        onScrubFinished = { finalRatio ->
                            val finalSec = (finalRatio.coerceIn(0f, 1f) * effectiveDurationSec)
                                .roundToInt()
                                .coerceIn(0, effectiveDurationSec)
                            isScrubbingSeekBar = false
                            jumpToSceneTimestamp(finalSec, formatSecondsToTimestamp(finalSec))
                            controlsRevealTick++
                        },
                        onResetZoom = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pinchZoomScale = 1.0f
                            controlsRevealTick++
                        },
                        onToggleFullscreen = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isAppFullscreen = !isAppFullscreen
                            controlsRevealTick++
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .testTag("player_controls_overlay")
                    )
                }

                // Loading Indicator
                if (isPlayerLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Loading Distraction-Free Player...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    }
                }

                // End-Screen Distraction Shield (Prevents YouTube's recommendation matrix)
                if (isVideoEnded) {
                    DistractionShieldOverlay(
                        onReplay = {
                            isVideoEnded = false
                            replayTrigger++
                        },
                        onBack = onBack
                    )
                }
            }

            // Details, Actions, Notes & Description (Visible only in normal portrait mode)
            if (!isAppFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.5.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (isTitleExpanded) 5 else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable { isTitleExpanded = !isTitleExpanded }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = video.channelName,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (video.uploadDate.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = video.uploadDate,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val displayDuration = com.example.util.DurationUtils.formatCleanDuration(video.duration, video.videoId)
                    if (displayDuration.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "⏱ $displayDuration",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

            Spacer(modifier = Modifier.height(10.dp))

            // === VIDEO PLAYLIST & NOTE/COMMENT STATUS OVERVIEW CARD ===
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_video_status_card"),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "📌 Video Status (Playlist & Note):",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(5.dp))

                    // 1. Saved Playlist(s) Status
                    if (savedInPlaylists.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (savedInPlaylists.size >= 2) Color(0xFF6A1B9A).copy(alpha = 0.15f) else Color(0xFF1B5E20).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (savedInPlaylists.size >= 2) {
                                    "⚡ Saved in ${savedInPlaylists.size} playlists: " + savedInPlaylists.joinToString(", ") { it.title }
                                } else {
                                    "✅ Saved in Playlist: 📁 ${savedInPlaylists.first().title}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (savedInPlaylists.size >= 2) Color(0xFF6A1B9A) else Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = Color(0xFFE65100).copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚪ Not saved in any playlist yet",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // 2. Note / Comment Status
                    if (currentNotes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNotesDialog = true }
                        ) {
                            Text(
                                text = "📝 Note: \"${currentNotes.trim()}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNotesDialog = true }
                        ) {
                            Text(
                                text = "💬 No notes added yet (+ Tap to write a note)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Action: Save to Playlist
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showSaveToPlaylistDialog = true
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("save_to_playlist_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (savedInPlaylists.isNotEmpty()) {
                        "✅ Saved (${savedInPlaylists.size}) • + Add to Another Playlist"
                    } else {
                        "Save to Playlist"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Video Page Options Row (Fullscreen, Mark Completed, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isAppFullscreen = true
                        controlsRevealTick++
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("video_page_fullscreen_button"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fullscreen", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isCompleted = !isCompleted
                        onToggleCompleted(video.copy(isCompleted = isCompleted))
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("video_page_complete_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isCompleted) Color(0xFF1B5E20).copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = "Mark Completed",
                        tint = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCompleted) "Completed ✓" else "Mark Done",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (onDeleteVideo != null && video.id > 0L) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteVideoConfirmDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("video_page_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Video",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Video Notes Section (सीन टाइमस्टैम्प नोट्स - Click timestamp to jump & play from that exact scene!)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_notes_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Scene Notes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap any timestamp badge (▶ 6:02) to jump directly to that scene",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                pauseTrigger++
                                quickSceneTimestampInput = formatSecondsToTimestamp(currentPlaybackSeconds)
                                isQuickTimestampLocked = true
                                showNotesDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("edit_notes_button")
                        ) {
                            Text("Full Editor", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }

                    // Seek Confirmation Banner when user taps a timestamp
                    lastSeekFeedback?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1B5E20).copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.clickable { lastSeekFeedback = null }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // === INLINE SCENE TIMESTAMP NOTE CREATOR ===
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Current Live Scene Capture Button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE65100).copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .clickable {
                                            pauseTrigger++
                                            quickSceneTimestampInput = formatSecondsToTimestamp(currentPlaybackSeconds)
                                            isQuickTimestampLocked = true
                                        }
                                        .testTag("capture_current_scene_time_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "⏸ Lock Scene: ${formatSecondsToTimestamp(currentPlaybackSeconds)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }

                                // Editable Timestamp Box (e.g. user can also type 6:02 manually)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "⏱ Time:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    OutlinedTextField(
                                        value = quickSceneTimestampInput,
                                        onValueChange = {
                                            quickSceneTimestampInput = it
                                            isQuickTimestampLocked = true
                                        },
                                        singleLine = true,
                                        placeholder = { Text("6:02", fontSize = 11.sp) },
                                        textStyle = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        ),
                                        modifier = Modifier
                                            .width(78.dp)
                                            .testTag("scene_timestamp_input"),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = quickSceneNoteText,
                                    onValueChange = {
                                        if (quickSceneNoteText.isEmpty() && it.isNotEmpty() && !isQuickTimestampLocked) {
                                            // Automatically capture the video's current scene time when user starts typing!
                                            quickSceneTimestampInput = formatSecondsToTimestamp(currentPlaybackSeconds)
                                            isQuickTimestampLocked = true
                                        }
                                        quickSceneNoteText = it
                                    },
                                    placeholder = {
                                        Text(
                                            "Write key point at [${quickSceneTimestampInput.ifBlank { formatSecondsToTimestamp(currentPlaybackSeconds) }}]...",
                                            fontSize = 12.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_scene_note_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    maxLines = 3
                                )

                                Button(
                                    onClick = {
                                        val ts = quickSceneTimestampInput.trim().ifBlank {
                                            formatSecondsToTimestamp(currentPlaybackSeconds)
                                        }
                                        addSceneNoteEntry(ts, quickSceneNoteText)
                                        quickSceneNoteText = ""
                                        isQuickTimestampLocked = false
                                        quickSceneTimestampInput = formatSecondsToTimestamp(currentPlaybackSeconds)
                                    },
                                    enabled = quickSceneNoteText.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    modifier = Modifier.testTag("save_quick_scene_note_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Save Scene Note",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // === SAVED SCENE NOTES LIST WITH CLICKABLE TIME HIGHLIGHTS & NOTE SEARCH ===
                    if (parsedSceneNotes.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📋 Saved Scene Notes (${filteredSceneNotes.size}/${parsedSceneNotes.size}) — Tap to jump:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    isSceneNoteSearchActive = !isSceneNoteSearchActive
                                    if (!isSceneNoteSearchActive) sceneNoteSearchQuery = ""
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("player_scene_note_search_button")
                            ) {
                                Icon(
                                    imageVector = if (isSceneNoteSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search scene notes",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isSceneNoteSearchActive) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = sceneNoteSearchQuery,
                                onValueChange = { sceneNoteSearchQuery = it },
                                placeholder = { Text("🔍 Search notes or timestamps (e.g. 6:02)...", fontSize = 11.sp) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (sceneNoteSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { sceneNoteSearchQuery = "" }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("player_scene_note_search_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredSceneNotes.forEach { entry ->
                                val hasTimestamp = entry.timestampSeconds != null && entry.timestampLabel != null
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (hasTimestamp) {
                                                jumpToSceneTimestamp(entry.timestampSeconds!!, entry.timestampLabel!!)
                                            } else {
                                                showNotesDialog = true
                                            }
                                        }
                                        .testTag("scene_note_item_${entry.lineIndex}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (hasTimestamp) {
                                                // HIGHLIGHTED CLICKABLE TIMESTAMP BADGE (e.g. ▶ 6:02)
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFE65100),
                                                    modifier = Modifier
                                                        .clickable {
                                                            jumpToSceneTimestamp(entry.timestampSeconds!!, entry.timestampLabel!!)
                                                        }
                                                        .testTag("timestamp_badge_${entry.timestampLabel}")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Play from ${entry.timestampLabel}",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = entry.timestampLabel!!,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                            } else {
                                                Text(
                                                    text = "📝",
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.noteBody,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (hasTimestamp) {
                                                    Text(
                                                        text = "👆 Tap to jump to ${entry.timestampLabel}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFFE65100)
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                val allLines = currentNotes.lines().filter { it.trim().isNotBlank() }.toMutableList()
                                                if (entry.lineIndex in allLines.indices) {
                                                    allLines.removeAt(entry.lineIndex)
                                                    val updated = allLines.joinToString("\n")
                                                    currentNotes = updated
                                                    onUpdateNotes?.invoke(video.id, updated)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete scene note",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "🌙 All scene timestamp notes are linked to this video and automatically backed up.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Example: Write a note above at 6:02 and tap 'Add'. A clickable [▶ 6:02] badge will appear so you can jump straight to that scene anytime!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Video Description Section (Expandable)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Video Description",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            imageVector = if (isDescriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isDescriptionExpanded) "Show less" else "Show more",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (currentDescription.isNotBlank()) {
                        Text(
                            text = currentDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "No description provided for this video.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    if (currentDescription.length > 100) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isDescriptionExpanded) "Show Less" else "Show More...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}

        // Native HTML5 Fullscreen Overlay (kept inside Box so WebView underneath is NEVER unmounted!)
        if (customFullscreenView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { customFullscreenView!! },
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    IconButton(
                        onClick = {
                            customViewCallback?.onCustomViewHidden()
                            customFullscreenView = null
                            customViewCallback = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloseFullscreen,
                            contentDescription = "Exit Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // Video Notes Dialog
    if (showNotesDialog) {
        VideoNotesDialog(
            initialNotes = currentNotes,
            videoTitle = video.title,
            currentSceneTimestamp = formatSecondsToTimestamp(currentPlaybackSeconds),
            onSeekToTimestamp = { sec, label ->
                jumpToSceneTimestamp(sec, label)
                showNotesDialog = false
            },
            onDismiss = { showNotesDialog = false },
            onSaveNotes = { newNotes ->
                currentNotes = newNotes
                onUpdateNotes?.invoke(video.id, newNotes)
            }
        )
    }

    // Save Video to Playlist Dialog (Pick existing or create new playlist)
    if (showSaveToPlaylistDialog) {
        val context = LocalContext.current
        SaveVideoToPlaylistDialog(
            video = video,
            allPlaylists = allPlaylists,
            allSavedVideos = allSavedVideos,
            recentPlaylistIds = recentPlaylistIds,
            onDismiss = { showSaveToPlaylistDialog = false },
            onSaveExisting = { targetPlaylistId, syncYouTube ->
                onSaveToPlaylist?.invoke(video, targetPlaylistId)
                if (syncYouTube) {
                    YouTubeSyncHelper.openInYouTubeApp(context, video.videoId, targetPlaylistId)
                }
                showSaveToPlaylistDialog = false
            },
            onCreateAndSave = { newPlaylistTitle, syncYouTube ->
                if (onCreatePlaylistAndSaveVideo != null) {
                    onCreatePlaylistAndSaveVideo(newPlaylistTitle, video)
                } else if (onSaveToPlaylist != null) {
                    onSaveToPlaylist(video, newPlaylistTitle)
                }
                if (syncYouTube) {
                    YouTubeSyncHelper.openInYouTubeApp(context, video.videoId, null)
                }
                showSaveToPlaylistDialog = false
            },
            onRefreshYouTubePlaylists = onRefreshYouTubePlaylists
        )
    }

    // Confirmation Dialog before deleting a video from the Video Page
    if (showDeleteVideoConfirmDialog && onDeleteVideo != null && video.id > 0L) {
        AlertDialog(
            onDismissRequest = { showDeleteVideoConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Delete Video?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${video.title}' from the playlist?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteVideoConfirmDialog = false
                        onDeleteVideo(video.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_video_dialog_button")
                ) {
                    Text("Yes, Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteVideoConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_delete_video_dialog_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dialog to attach or edit scene timestamp notes for any video.
 */
@Composable
fun VideoNotesDialog(
    initialNotes: String,
    videoTitle: String,
    currentSceneTimestamp: String = "0:00",
    onSeekToTimestamp: ((Int, String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaveNotes: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(initialNotes) }
    val dialogSceneNotes = remember(noteText) { parseSceneNotes(noteText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Scene Notes", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = videoTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Quick Timestamp Insert Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val prefix = "[$currentSceneTimestamp] "
                            noteText = if (noteText.isBlank()) prefix else "${noteText.trimEnd()}\n$prefix"
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("⏱ + Current Scene [$currentSceneTimestamp]", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            noteText = if (noteText.isBlank()) "[6:02] " else "${noteText.trimEnd()}\n[6:02] "
                        }
                    ) {
                        Text("+ [6:02] Example", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Example:\n[6:02] Main concept explained here\n[8:15] Second important scene...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 8
                )

                // Clickable Timestamp Preview Chips inside Dialog (great when opened from Fullscreen!)
                val timestampedEntries = dialogSceneNotes.filter { it.timestampSeconds != null && it.timestampLabel != null }
                if (timestampedEntries.isNotEmpty() && onSeekToTimestamp != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "▶ Tap any timestamp badge below to jump directly to that scene:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        timestampedEntries.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFF3E0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSaveNotes(noteText.trim())
                                        onSeekToTimestamp(item.timestampSeconds!!, item.timestampLabel!!)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFE65100)
                                    ) {
                                        Text(
                                            text = "▶ ${item.timestampLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.noteBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveNotes(noteText.trim())
                    onDismiss()
                }
            ) {
                Text("Save Note")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Distraction Shield shown when the video finishes:
 * Completely blocks YouTube's 12-grid recommendations.
 */
@Composable
private fun DistractionShieldOverlay(
    onReplay: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFA11151F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1B5E20),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Video Completed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Zero recommendations • Distraction shield active",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onBack != null) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back to List")
                    }
                }

                Button(
                    onClick = onReplay,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Replay Video")
                }
            }
        }
    }
}

/**
 * Dialog to save video to an existing playlist OR create a new playlist on the fly.
 */
@Composable
fun SaveVideoToPlaylistDialog(
    video: VideoEntity,
    allPlaylists: List<PlaylistEntity>,
    allSavedVideos: List<VideoEntity> = emptyList(),
    recentPlaylistIds: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSaveExisting: (String, Boolean) -> Unit,
    onCreateAndSave: (String, Boolean) -> Unit,
    onRefreshYouTubePlaylists: (() -> Unit)? = null
) {
    var isCreatingNew by remember { mutableStateOf(allPlaylists.isEmpty()) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var playlistSearchQuery by remember { mutableStateOf("") }
    var sortAlphabetically by remember { mutableStateOf(false) } // false = Recently Used First (User Default), true = A to Z
    var syncWithYouTubeApp by remember { mutableStateOf(false) }

    // Set of playlistIds where this video is ALREADY saved!
    val alreadySavedPlaylistIds = remember(video.videoId, video.playlistId, allSavedVideos) {
        val ids = mutableSetOf<String>()
        if (!video.playlistId.isNullOrBlank()) {
            ids.add(video.playlistId)
        }
        for (v in allSavedVideos) {
            if (v.videoId == video.videoId && !v.playlistId.isNullOrBlank()) {
                ids.add(v.playlistId)
            }
        }
        ids
    }
    val alreadySavedPlaylists = remember(allPlaylists, alreadySavedPlaylistIds) {
        allPlaylists.filter { it.playlistId in alreadySavedPlaylistIds }
    }

    val recentIndexMap = remember(recentPlaylistIds) {
        recentPlaylistIds.withIndex().associate { (idx, plId) -> plId to idx }
    }

    val orderedPlaylists = remember(allPlaylists, recentIndexMap, sortAlphabetically) {
        if (sortAlphabetically) {
            allPlaylists.sortedBy { it.title.lowercase() }
        } else {
            val recent = allPlaylists
                .filter { it.playlistId in recentIndexMap }
                .sortedBy { recentIndexMap[it.playlistId] ?: Int.MAX_VALUE }
            val others = allPlaylists
                .filter { it.playlistId !in recentIndexMap }
                .sortedBy { it.title.lowercase() }
            recent + others
        }
    }

    var selectedPlaylistId by remember(orderedPlaylists) {
        mutableStateOf(orderedPlaylists.firstOrNull()?.playlistId ?: "")
    }

    val filteredPlaylists = remember(orderedPlaylists, playlistSearchQuery) {
        if (playlistSearchQuery.isBlank()) orderedPlaylists
        else orderedPlaylists.filter { it.title.contains(playlistSearchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save to Playlist",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Save '${video.title}' to a playlist:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Clear Banner showing which playlist(s) this video is ALREADY saved in!
                if (alreadySavedPlaylists.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1B5E20).copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✅ Already saved (${alreadySavedPlaylists.size}): " + alreadySavedPlaylists.joinToString(", ") { it.title },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚪ Not saved in any playlist yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (allPlaylists.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { isCreatingNew = false },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (!isCreatingNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!isCreatingNew) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Existing (${allPlaylists.size})")
                        }

                        FilledTonalButton(
                            onClick = { isCreatingNew = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isCreatingNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isCreatingNew) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("+ New")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (isCreatingNew || allPlaylists.isEmpty()) {
                    OutlinedTextField(
                        value = newPlaylistTitle,
                        onValueChange = { newPlaylistTitle = it },
                        label = { Text("New Playlist Name") },
                        placeholder = { Text("e.g. My Favorites") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Always-visible search bar so user can find any playlist from A to Z
                    OutlinedTextField(
                        value = playlistSearchQuery,
                        onValueChange = { playlistSearchQuery = it },
                        placeholder = { Text("Search playlists (A to Z)...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search playlists",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (playlistSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { playlistSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_dialog_playlist_search_input")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Sort Toggle + Refresh All Playlists + Scroll Hint Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !sortAlphabetically,
                                onClick = { sortAlphabetically = false },
                                label = { Text("🕒 Recent", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = sortAlphabetically,
                                onClick = { sortAlphabetically = true },
                                label = { Text("🔤 A–Z (${allPlaylists.size})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (onRefreshYouTubePlaylists != null) {
                            TextButton(
                                onClick = onRefreshYouTubePlaylists
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh playlists from YouTube",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fetch All", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Text(
                                text = "↕ Scroll (${filteredPlaylists.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Fully Scrollable LazyColumn for all playlists (supports 100s of playlists smoothly)
                    if (filteredPlaylists.isEmpty()) {
                        Text(
                            text = "No matching playlists found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 300.dp)
                                .testTag("save_dialog_playlist_lazy_list"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredPlaylists, key = { it.playlistId }) { pl ->
                                val isSelected = selectedPlaylistId == pl.playlistId
                                val isRecent = pl.playlistId in recentIndexMap
                                val isAlreadySavedHere = pl.playlistId in alreadySavedPlaylistIds
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPlaylistId = pl.playlistId }
                                        .testTag("save_dialog_playlist_item_${pl.playlistId}"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            isAlreadySavedHere -> Color(0xFF1B5E20).copy(alpha = 0.12f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedPlaylistId = pl.playlistId }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = pl.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected || isAlreadySavedHere) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isAlreadySavedHere) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF2E7D32)
                                                ) {
                                                    Text(
                                                        text = "✅ Saved",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else if (isRecent && !sortAlphabetically) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "🕒 Recent",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val isYouTubeAutoSaveActive = remember { YouTubeSyncHelper.hasYouTubeWebSession() }
                if (isYouTubeAutoSaveActive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1B5E20).copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✅ Official YouTube Playlist Auto-Save is active (saves directly to your YouTube playlist too)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { syncWithYouTubeApp = !syncWithYouTubeApp }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = syncWithYouTubeApp,
                            onCheckedChange = { syncWithYouTubeApp = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Also Open in YouTube App ↗",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Open this video in the official YouTube app right after saving",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCreatingNew || allPlaylists.isEmpty()) {
                        if (newPlaylistTitle.isNotBlank()) {
                            onCreateAndSave(newPlaylistTitle.trim(), syncWithYouTubeApp)
                        }
                    } else {
                        if (selectedPlaylistId.isNotBlank()) {
                            onSaveExisting(selectedPlaylistId, syncWithYouTubeApp)
                        }
                    }
                },
                enabled = if (isCreatingNew || allPlaylists.isEmpty()) newPlaylistTitle.isNotBlank() else selectedPlaylistId.isNotBlank()
            ) {
                Text(
                    if (syncWithYouTubeApp) "Save & Open in YouTube ↗"
                    else if (isCreatingNew || allPlaylists.isEmpty()) "Create & Save"
                    else "Save"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Invisible Vertical Side Seek Bar for Left (Brightness) and Right (Volume) controls.
 * Completely invisible (zero visual clutter) while allowing vertical drag up/down or top/bottom tap
 * to increase or decrease Brightness (Left) and Volume (Right).
 */
@Composable
fun VerticalSideSeekBar(
    value: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    alpha: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    var barHeightPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .width(32.dp)
            .height(140.dp)
            .onSizeChanged { size ->
                if (size.height > 0) {
                    barHeightPx = size.height.toFloat()
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val h = barHeightPx.coerceAtLeast(1f)
                    var lastY = down.position.y
                    var totalMove = 0f

                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        pointerId = change.id
                        change.consume()
                        val dy = lastY - change.position.y // Positive when dragging UP, negative when dragging DOWN
                        totalMove += abs(dy)
                        lastY = change.position.y
                        if (abs(dy) > 0.5f) {
                            val stepDelta = dy / h
                            currentOnValueChange((currentValue + stepDelta).coerceIn(0f, 1f))
                        }
                    }

                    // If it was a quick tap without dragging: upper half increases, lower half decreases
                    if (totalMove < 12f) {
                        val tapStep = if (down.position.y < h * 0.5f) 0.10f else -0.10f
                        currentOnValueChange((currentValue + tapStep).coerceIn(0f, 1f))
                    }
                    currentOnValueChangeFinished()
                }
            }
    )
}

/**
 * Ultra-Minimal & Modern Bottom Seek Bar:
 * - Zero text buttons ('-10s', '+10s', 'Play' completely removed).
 * - Custom touch handler maps horizontal touch coordinate `position.x / trackWidth` directly to `[0.0 .. 1.0]`
 *   of the video timeline in unscaled parent coordinates, so zooming in (1.0x .. 2.5x) never affects seek accuracy.
 */
@Composable
fun MinimalModernSeekBar(
    progressFraction: Float,
    currentSeconds: Int,
    totalSeconds: Int,
    isScrubbing: Boolean,
    overlayAlpha: Float,
    isAppFullscreen: Boolean,
    pinchZoomScale: Float,
    onScrubFractionChanged: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    onResetZoom: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnScrubFractionChanged by rememberUpdatedState(onScrubFractionChanged)
    val currentOnScrubFinished by rememberUpdatedState(onScrubFinished)
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 3.dp,
        animationSpec = tween(durationMillis = 120),
        label = "minimalSeekTrackHeight"
    )
    val animatedThumbSize by animateDpAsState(
        targetValue = if (isScrubbing) 14.dp else 9.dp,
        animationSpec = tween(durationMillis = 120),
        label = "minimalSeekThumbSize"
    )

    Column(
        modifier = modifier
            .graphicsLayer { alpha = if (isScrubbing) 1f else overlayAlpha }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.52f)
                    )
                )
            )
            .padding(
                horizontal = if (isAppFullscreen) 20.dp else 12.dp,
                vertical = if (isAppFullscreen) 8.dp else 4.dp
            )
    ) {
        // Floating minimal time bubble while actively scrubbing
        if (isScrubbing) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp)
                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            ) {
                Text(
                    text = "${formatSecondsToTimestamp(currentSeconds)} / ${formatSecondsToTimestamp(totalSeconds)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Minimal numeric timestamp (no text labels)
            Text(
                text = "${formatSecondsToTimestamp(currentSeconds)} / ${formatSecondsToTimestamp(totalSeconds)}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("player_seek_time_label")
            )

            // Sleek Custom Interactive Timeline Track
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .onSizeChanged { size ->
                        if (size.width > 0) {
                            trackWidthPx = size.width.toFloat()
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val w = trackWidthPx.coerceAtLeast(1f)
                            var latestRatio = (down.position.x / w).coerceIn(0f, 1f)
                            currentOnScrubFractionChanged(latestRatio)

                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                pointerId = change.id
                                change.consume()
                                latestRatio = (change.position.x / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                currentOnScrubFractionChanged(latestRatio)
                            }
                            currentOnScrubFinished(latestRatio)
                        }
                    }
                    .testTag("player_interactive_seek_bar"),
                contentAlignment = Alignment.CenterStart
            ) {
                // Background Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedTrackHeight)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.26f))
                )

                // Active Progress Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .height(animatedTrackHeight)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF5252), Color(0xFFFF3D00))
                            )
                        )
                )

                // Minimal Circular Thumb
                val thumbOffsetPx = (progressFraction.coerceIn(0f, 1f) * trackWidthPx).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = thumbOffsetPx, y = 0) }
                        .offset(x = -(animatedThumbSize / 2))
                        .size(animatedThumbSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, Color(0xFFFF3D00), CircleShape)
                )
            }

            // Icon-Only Reset Zoom button (only when zoomed in)
            if (pinchZoomScale > 1.02f) {
                IconButton(
                    onClick = onResetZoom,
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("overlay_reset_zoom_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Zoom",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Icon-Only Fullscreen Toggle
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .size(26.dp)
                    .testTag("overlay_fullscreen_toggle_button")
            ) {
                Icon(
                    imageVector = if (isAppFullscreen) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                    contentDescription = if (isAppFullscreen) "Exit Fullscreen" else "Fullscreen",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * Android WebView Isolated YouTube Player:
 * 1. Family Link / Parental Controls compatible:
 *    - Loads from `https://www.youtube-nocookie.com` to bypass Chrome parental filters on youtube.com.
 *    - Allows internal loading of all video streams and manifests.
 *    - Prevents external redirection to the blocked Chrome browser.
 * 2. Autoplay completely disabled:
 *    - `autoplay=0` and `auto_play=0`.
 *    - `mediaPlaybackRequiresUserGesture = true` enforced at the Android WebKit engine level.
 *    - Video remains paused until the user explicitly taps play.
 * 3. Fullscreen support:
 *    - HTML5 fullscreen via WebChromeClient.
 * 4. Zero suggestions:
 *    - Pause overlay, end-screen cards, teaser cards, watermarks, annotations hidden via CSS.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "ClickableViewAccessibility")
@Composable
fun ZeroSuggestionPlayer(
    videoId: String,
    initialSeekSeconds: Int? = null,
    replayTrigger: Int,
    pauseTrigger: Int = 0,
    togglePlayPauseTrigger: Int = 0,
    seekCommand: VideoSeekCommand? = null,
    fullscreenZoomMode: String = "FIT_ORIGINAL",
    currentZoomScale: Float = 1.0f,
    playerVolumePercent: Int = 100,
    isVideoPlaying: Boolean = false,
    onHoldSpeedChanged: (Boolean, Float) -> Unit = { _, _ -> },
    onPinchZoomChanged: (Boolean, Float) -> Unit = { _, _ -> },
    onScreenTapped: () -> Unit = {},
    onDoubleTapAction: (Int) -> Unit = {},
    onSideVerticalDelta: (Int, Float) -> Unit = { _, _ -> },
    onTimelineSeekRatio: (Float, Boolean) -> Unit = { _, _ -> },
    onCurrentTimeChanged: (Int) -> Unit = {},
    onDurationChanged: (Int) -> Unit = {},
    onPlayingStateChanged: (Boolean) -> Unit = {},
    onVideoOrientationDetected: (Boolean) -> Unit = {},
    onLoadingChanged: (Boolean) -> Unit,
    onVideoEnded: () -> Unit,
    onShowFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val currentOnZoomScale by rememberUpdatedState(currentZoomScale)
    val currentIsVideoPlaying by rememberUpdatedState(isVideoPlaying)
    val currentOnHoldSpeedChanged by rememberUpdatedState(onHoldSpeedChanged)
    val currentOnPinchZoomChanged by rememberUpdatedState(onPinchZoomChanged)
    val currentOnScreenTapped by rememberUpdatedState(onScreenTapped)
    val currentOnDoubleTapAction by rememberUpdatedState(onDoubleTapAction)
    val currentOnSideVerticalDelta by rememberUpdatedState(onSideVerticalDelta)
    val currentOnTimelineSeekRatio by rememberUpdatedState(onTimelineSeekRatio)
    val currentOnCurrentTimeChanged by rememberUpdatedState(onCurrentTimeChanged)
    val currentOnDurationChanged by rememberUpdatedState(onDurationChanged)
    val currentOnPlayingStateChanged by rememberUpdatedState(onPlayingStateChanged)
    val currentOnVideoOrientationDetected by rememberUpdatedState(onVideoOrientationDetected)
    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)
    var lastHandledReplay by remember(videoId) { mutableStateOf(0) }
    var lastHandledPause by remember(videoId) { mutableStateOf(0) }
    var lastHandledTogglePlayPause by remember(videoId) { mutableStateOf(0) }
    var lastHandledSeekId by remember(videoId) { mutableStateOf(0L) }
    var lastHandledZoomMode by remember(videoId) { mutableStateOf("") }
    var lastHandledVolumePercent by remember(videoId) { mutableIntStateOf(-1) }
    val startParamSeconds = (initialSeekSeconds ?: 0).coerceAtLeast(0)
    val autoplayParam = if (startParamSeconds > 0) 1 else 0

    class PlayerJsBridge {
        @JavascriptInterface
        fun reportEnded() {
            mainHandler.post {
                currentOnPlayingStateChanged(false)
                currentOnVideoEnded()
            }
        }

        @JavascriptInterface
        fun reportCurrentTime(seconds: Int) {
            if (seconds >= 0) {
                mainHandler.post { currentOnCurrentTimeChanged(seconds) }
            }
        }

        @JavascriptInterface
        fun reportDuration(seconds: Int) {
            if (seconds > 0) {
                mainHandler.post { currentOnDurationChanged(seconds) }
            }
        }

        @JavascriptInterface
        fun reportPlayingState(isPlaying: Boolean) {
            mainHandler.post { currentOnPlayingStateChanged(isPlaying) }
        }

        @JavascriptInterface
        fun reportVideoOrientation(isPortrait: Boolean) {
            mainHandler.post { currentOnVideoOrientationDetected(isPortrait) }
        }
    }

    // Clean HTML with standard controls, playsinline, GPU-accelerated CSS distraction hiding,
    // Auto Zoom-to-Fill (No Black Bars), and Dynamic Hold-Speed Control (0.5x to 4.0x)
    val htmlContent = remember(videoId, startParamSeconds) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body {
              width: 100%;
              height: 100%;
              background-color: #000000;
              overflow: hidden;
            }
            #player-wrap {
              width: 100%;
              height: 100%;
              position: relative;
              overflow: hidden;
              display: flex;
              align-items: center;
              justify-content: center;
              background-color: #000000;
            }
            iframe {
              width: 100%;
              height: 100%;
              border: 0;
              transform-origin: center center;
              transition: transform 0.2s ease-out;
            }
            /* GPU-evaluated CSS distraction hiding: zero JS thread CPU consumption */
            .ytp-pause-overlay,
            .ytp-pause-overlay-container,
            .ytp-endscreen-content,
            .ytp-endscreen-paginate,
            .ytp-endscreen-previous,
            .ytp-endscreen-next,
            .videowall-endscreen,
            .ytp-show-tiles,
            .ytp-suggestion-set,
            .ytp-ce-element,
            .ytp-ce-element-show,
            .ytp-ce-covering-overlay,
            .ytp-ce-expanding-overlay,
            .ytp-ce-channel,
            .ytp-ce-playlist,
            .ytp-ce-video,
            .ytp-cards-teaser,
            .ytp-cards-button,
            .ytp-cards-container,
            .ytp-watermark,
            .ytp-chrome-top-buttons,
            .ytp-show-cards-title,
            .ytp-paid-content-overlay,
            .ytp-upnext,
            .ytp-next-button,
            .ytp-prev-button,
            a.ytp-title-link,
            a.ytp-watermark {
              display: none !important;
              visibility: hidden !important;
              opacity: 0 !important;
              pointer-events: none !important;
            }
          </style>
        </head>
        <body>
          <div id="player-wrap">
            <iframe id="focus-yt-iframe"
              src="https://www.youtube-nocookie.com/embed/$videoId?enablejsapi=1&autoplay=$autoplayParam&start=$startParamSeconds&controls=0&rel=0&modestbranding=1&iv_load_policy=3&fs=0&playsinline=1"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen"
              allowfullscreen>
            </iframe>
          </div>
          <script>
            var player;
            var lastReportedSec = -1;
            var lastReportedDur = -1;
            var lastReportedPlaying = null;
            var orientationReported = false;
            var initialStartSec = $startParamSeconds;
            var latestKnownTime = $startParamSeconds;
            var highSpeedAssistTimer = null;
            var activeDynamicRate = 1.0;

            function notifyTime(sec) {
              if (typeof sec === 'number' && !isNaN(sec) && sec >= 0) {
                latestKnownTime = sec;
              }
              var floored = Math.floor(sec);
              if (floored >= 0 && floored !== lastReportedSec) {
                lastReportedSec = floored;
                if (window.AndroidBridge && window.AndroidBridge.reportCurrentTime) {
                  window.AndroidBridge.reportCurrentTime(floored);
                }
              }
            }

            function notifyDuration(dur) {
              if (typeof dur === 'number' && !isNaN(dur) && dur > 0) {
                var rounded = Math.round(dur);
                if (rounded > 0 && rounded !== lastReportedDur) {
                  lastReportedDur = rounded;
                  if (window.AndroidBridge && window.AndroidBridge.reportDuration) {
                    window.AndroidBridge.reportDuration(rounded);
                  }
                }
              }
            }

            function notifyPlayingState(isPlaying) {
              var b = !!isPlaying;
              if (b !== lastReportedPlaying) {
                lastReportedPlaying = b;
                if (window.AndroidBridge && window.AndroidBridge.reportPlayingState) {
                  window.AndroidBridge.reportPlayingState(b);
                }
              }
            }

            // Detect real video aspect ratio (Portrait vs Landscape) via YouTube oEmbed
            try {
              fetch('https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json')
                .then(function(r) { return r.json(); })
                .then(function(meta) {
                  if (meta && typeof meta.width === 'number' && typeof meta.height === 'number' && meta.width > 0 && meta.height > 0) {
                    var isVert = meta.height > meta.width || (meta.title && meta.title.toLowerCase().indexOf('#short') !== -1);
                    orientationReported = true;
                    if (window.AndroidBridge && window.AndroidBridge.reportVideoOrientation) {
                      window.AndroidBridge.reportVideoOrientation(isVert);
                    }
                  }
                }).catch(function(e) {});
            } catch(e) {}

            // PostMessage listener for YouTube iframe events (tracks live currentTime, duration & ended state)
            window.addEventListener('message', function(event) {
              try {
                var data = (typeof event.data === 'string') ? JSON.parse(event.data) : event.data;
                if (data && data.event === 'infoDelivery' && data.info) {
                  if (typeof data.info.currentTime === 'number') {
                    notifyTime(data.info.currentTime);
                  }
                  if (typeof data.info.duration === 'number' && data.info.duration > 0) {
                    notifyDuration(data.info.duration);
                  }
                  if (typeof data.info.playerState === 'number') {
                    if (data.info.playerState === 1) {
                      notifyPlayingState(true);
                    } else if (data.info.playerState === 2 || data.info.playerState === 0) {
                      notifyPlayingState(false);
                    }
                  }
                  if (!orientationReported && data.info.videoData && data.info.videoData.title) {
                    var tLower = data.info.videoData.title.toLowerCase();
                    if (tLower.indexOf('#short') !== -1 || tLower.indexOf('shorts') !== -1) {
                      orientationReported = true;
                      if (window.AndroidBridge && window.AndroidBridge.reportVideoOrientation) {
                        window.AndroidBridge.reportVideoOrientation(true);
                      }
                    }
                  }
                  if (data.info.playerState === 0) {
                    if (window.AndroidBridge && window.AndroidBridge.reportEnded) {
                      window.AndroidBridge.reportEnded();
                    }
                  }
                }
              } catch(e) {}
            });

            // Iframe API script loader via youtube-nocookie.com for Family Link compatibility
            var tag = document.createElement('script');
            tag.src = "https://www.youtube-nocookie.com/iframe_api";
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            function onYouTubeIframeAPIReady() {
              try {
                player = new YT.Player('focus-yt-iframe', {
                  host: 'https://www.youtube-nocookie.com',
                  events: {
                    'onReady': function(event) {
                      try {
                        if (player && typeof player.getDuration === 'function') {
                          var d = player.getDuration();
                          if (d > 0) notifyDuration(d);
                        }
                      } catch(e) {}
                      if (initialStartSec > 0) {
                        window.seekToScene(initialStartSec);
                      }
                    },
                    'onStateChange': function(event) {
                      if (event.data === 1) {
                        notifyPlayingState(true);
                      } else if (event.data === 2 || event.data === 0) {
                        notifyPlayingState(false);
                      }
                      if (event.data === 0) { // ENDED
                        if (window.AndroidBridge && window.AndroidBridge.reportEnded) {
                          window.AndroidBridge.reportEnded();
                        }
                      }
                    }
                  }
                });
              } catch(e) {}
            }

            var activeZoomMode = 'FIT_ORIGINAL';

            // Poll current time & duration every 400ms, detect exact video stream aspect ratio, & ensure inner zoom style is injected
            setInterval(function() {
              try {
                var vEl = getInnerVideoElement();
                if (vEl) {
                  if (typeof vEl.currentTime === 'number' && !isNaN(vEl.currentTime) && vEl.currentTime > 0) {
                    notifyTime(vEl.currentTime);
                  }
                  if (typeof vEl.duration === 'number' && !isNaN(vEl.duration) && vEl.duration > 0) {
                    notifyDuration(vEl.duration);
                  }
                  if ((vEl.src || vEl.currentSrc) && vEl.readyState > 0) {
                    notifyPlayingState(!vEl.paused && !vEl.ended);
                  }
                  if (!orientationReported && vEl.videoWidth > 0 && vEl.videoHeight > 0) {
                    orientationReported = true;
                    if (window.AndroidBridge && window.AndroidBridge.reportVideoOrientation) {
                      window.AndroidBridge.reportVideoOrientation(vEl.videoHeight > vEl.videoWidth);
                    }
                  }
                }
                if (player && typeof player.getCurrentTime === 'function') {
                  var t = player.getCurrentTime();
                  if (typeof t === 'number' && !isNaN(t)) {
                    notifyTime(t);
                  }
                  if (typeof player.getDuration === 'function') {
                    var pd = player.getDuration();
                    if (typeof pd === 'number' && !isNaN(pd) && pd > 0) {
                      notifyDuration(pd);
                    }
                  }
                } else {
                  var iframe = document.getElementById('focus-yt-iframe');
                  if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage(JSON.stringify({event: 'listening', id: 1}), '*');
                  }
                }
                if (window.setIframeZoomMode) {
                  window.setIframeZoomMode(activeZoomMode);
                }
              } catch(e) {}
            }, 400);

            window.seekToScene = function(sec) {
              latestKnownTime = sec;
              // 1. Direct HTML5 <video> element seek (instantaneous even when zoomed in!)
              try {
                var vEl = getInnerVideoElement();
                if (vEl && (vEl.src || vEl.currentSrc)) {
                  vEl.currentTime = sec;
                  if (vEl.paused) {
                    vEl.play();
                  }
                }
              } catch(e) {}
              // 2. YouTube IFrame Player API seek
              try {
                if (player && typeof player.seekTo === 'function') {
                  player.seekTo(sec, true);
                  if (typeof player.playVideo === 'function') {
                    player.playVideo();
                  }
                }
              } catch(e) {}
              // 3. YouTube postMessage command fallback
              try {
                var iframe = document.getElementById('focus-yt-iframe');
                if (iframe && iframe.contentWindow) {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: 'seekTo',
                    args: [sec, true]
                  }), '*');
                  iframe.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: 'playVideo',
                    args: []
                  }), '*');
                }
              } catch(e) {}
              notifyTime(sec);
              notifyPlayingState(true);
            };

            window.playScene = function() {
              try {
                var ifr = document.getElementById('focus-yt-iframe');
                if (ifr && ifr.contentDocument) {
                  var largeBtn = ifr.contentDocument.querySelector('.ytp-large-play-button');
                  var cuedOverlay = ifr.contentDocument.querySelector('.ytp-cued-thumbnail-overlay');
                  if (largeBtn && (!cuedOverlay || cuedOverlay.style.display !== 'none')) {
                    largeBtn.click();
                  }
                }
              } catch(e) {}
              try {
                var vEl = getInnerVideoElement();
                if (vEl && (vEl.src || vEl.currentSrc) && vEl.paused) {
                  var p = vEl.play();
                  if (p && typeof p.catch === 'function') p.catch(function(){});
                }
              } catch(e) {}
              try {
                if (player && typeof player.playVideo === 'function') {
                  player.playVideo();
                }
              } catch(e) {}
              try {
                var iframe = document.getElementById('focus-yt-iframe');
                if (iframe && iframe.contentWindow) {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: 'playVideo',
                    args: []
                  }), '*');
                }
              } catch(e) {}
              notifyPlayingState(true);
            };

            window.pauseScene = function() {
              try {
                var vEl = getInnerVideoElement();
                if (vEl && !vEl.paused) {
                  vEl.pause();
                }
              } catch(e) {}
              try {
                if (player && typeof player.pauseVideo === 'function') {
                  player.pauseVideo();
                }
              } catch(e) {}
              try {
                var iframe = document.getElementById('focus-yt-iframe');
                if (iframe && iframe.contentWindow) {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: 'pauseVideo',
                    args: []
                  }), '*');
                }
              } catch(e) {}
              notifyPlayingState(false);
            };

            window.togglePlayPauseScene = function() {
              var isActuallyPlaying = (lastReportedPlaying === true);
              try {
                var vEl = getInnerVideoElement();
                if (vEl && (vEl.src || vEl.currentSrc) && vEl.readyState > 0) {
                  isActuallyPlaying = (!vEl.paused && !vEl.ended);
                } else if (player && typeof player.getPlayerState === 'function') {
                  var st = player.getPlayerState();
                  if (st === 1) isActuallyPlaying = true;
                  else if (st === 2 || st === 0 || st === 5 || st === -1) isActuallyPlaying = false;
                }
              } catch(e) {}

              if (isActuallyPlaying) {
                window.pauseScene();
              } else {
                window.playScene();
              }
            };

            window.setPlayerVolume = function(pct) {
              var vol = Math.max(0, Math.min(100, parseInt(pct, 10) || 0));
              try {
                var vEl = getInnerVideoElement();
                if (vEl) {
                  vEl.muted = (vol === 0);
                  vEl.volume = vol / 100.0;
                }
              } catch(e) {}
              try {
                if (player && typeof player.setVolume === 'function') {
                  if (vol === 0 && typeof player.mute === 'function') {
                    player.mute();
                  } else {
                    if (typeof player.unMute === 'function') player.unMute();
                    player.setVolume(vol);
                  }
                }
              } catch(e) {}
              try {
                var iframe = document.getElementById('focus-yt-iframe');
                if (iframe && iframe.contentWindow) {
                  iframe.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: vol === 0 ? 'mute' : 'unMute',
                    args: []
                  }), '*');
                  iframe.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: 'setVolume',
                    args: [vol]
                  }), '*');
                }
              } catch(e) {}
            };

            function getInnerVideoElement() {
              try {
                var ifr = document.getElementById('focus-yt-iframe');
                if (ifr && ifr.contentDocument) {
                  attachSeekBarTouchEnhancer();
                  var v = ifr.contentDocument.querySelector('video');
                  if (v) {
                    if (!v._focusRateGuardAttached) {
                      v._focusRateGuardAttached = true;
                      v.addEventListener('ratechange', function() {
                        try {
                          if (activeDynamicRate !== 1.0 && Math.abs(v.playbackRate - activeDynamicRate) > 0.04) {
                            v.playbackRate = activeDynamicRate;
                          }
                        } catch(err) {}
                      });
                    }
                    return v;
                  }
                }
              } catch(e) {}
              return null;
            }

            // Enhances YouTube's inner seek bar / progress bar so touch-dragging (scrubbing) forward & backward works smoothly in fullscreen
            function attachSeekBarTouchEnhancer() {
              try {
                var ifr = document.getElementById('focus-yt-iframe');
                if (!ifr || !ifr.contentDocument || !ifr.contentDocument.body) return;
                var doc = ifr.contentDocument;
                if (doc._focusSeekEnhancerAttached) return;
                doc._focusSeekEnhancerAttached = true;

                if (doc.head && !doc.getElementById('focus-seekbar-touch-style')) {
                  var st = doc.createElement('style');
                  st.id = 'focus-seekbar-touch-style';
                  st.textContent =
                    '.ytp-progress-bar-container { height: 26px !important; padding: 8px 0 !important; touch-action: none !important; pointer-events: auto !important; z-index: 9999 !important; } ' +
                    '.ytp-progress-bar, .ytp-scrubber-container, .PlayerControlsProgressBarHost, [role="slider"] { touch-action: none !important; pointer-events: auto !important; } ' +
                    '.ytp-scrubber-button { width: 16px !important; height: 16px !important; border-radius: 8px !important; }';
                  doc.head.appendChild(st);
                }

                var isScrubbingSeekBar = false;

                function findSeekBarElement(target) {
                  if (!target || !target.closest) return null;
                  return target.closest('.ytp-progress-bar-container, .ytp-progress-bar, .ytp-scrubber-container, .PlayerControlsProgressBarHost, .progress-bar-line, .ytm-progress-bar, [role="slider"]');
                }

                function applySeekFromTouch(touch, barEl) {
                  var v = doc.querySelector('video');
                  if (!v || !v.duration || isNaN(v.duration) || v.duration <= 0) return;
                  var track = doc.querySelector('.ytp-progress-bar, .PlayerControlsProgressBarHost, [role="slider"]') || barEl;
                  var rect = track ? track.getBoundingClientRect() : null;
                  var left = (rect && rect.width > 20) ? rect.left : 16;
                  var width = (rect && rect.width > 20) ? rect.width : ( doc.documentElement.clientWidth - 32 );
                  if (width <= 20) return;
                  var ratio = (touch.clientX - left) / width;
                  if (ratio < 0) ratio = 0;
                  if (ratio > 1) ratio = 1;
                  var targetTime = ratio * v.duration;
                  v.currentTime = targetTime;
                  if (player && typeof player.seekTo === 'function') {
                    try { player.seekTo(targetTime, true); } catch(err) {}
                  }
                  notifyTime(targetTime);
                }

                doc.addEventListener('touchstart', function(e) {
                  if (!e.touches || e.touches.length !== 1) return;
                  var t = e.touches[0];
                  var barEl = findSeekBarElement(e.target);
                  if (barEl) {
                    isScrubbingSeekBar = true;
                    applySeekFromTouch(t, barEl);
                  }
                }, { passive: true, capture: true });

                doc.addEventListener('touchmove', function(e) {
                  if (!e.touches || e.touches.length !== 1) return;
                  var t = e.touches[0];
                  if (!isScrubbingSeekBar) {
                    var barElCheck = findSeekBarElement(e.target);
                    if (barElCheck) {
                      isScrubbingSeekBar = true;
                    }
                  }
                  if (isScrubbingSeekBar) {
                    var barEl = doc.querySelector('.ytp-progress-bar, .ytp-progress-bar-container, [role="slider"]');
                    applySeekFromTouch(t, barEl);
                  }
                }, { passive: true, capture: true });

                doc.addEventListener('touchend', function(e) {
                  if (!isScrubbingSeekBar) return;
                  isScrubbingSeekBar = false;
                  if (e.changedTouches && e.changedTouches.length > 0) {
                    var barEl = doc.querySelector('.ytp-progress-bar, .ytp-progress-bar-container, [role="slider"]');
                    applySeekFromTouch(e.changedTouches[0], barEl);
                  }
                }, { passive: true, capture: true });

                doc.addEventListener('touchcancel', function() {
                  isScrubbingSeekBar = false;
                }, { passive: true, capture: true });
              } catch(e) {}
            }

            // Dynamic Press-and-Hold Playback Speed Controller (supports 0.5x, 1.0x, 1.5x, 2.0x, 2.5x, 3.0x, 3.5x, 4.0x)
            // Uses direct same-origin HTML5 <video>.playbackRate for silky-smooth 0.5x..4.0x hardware playback without seek stutter!
            window.setDynamicPlaybackSpeed = function(rate) {
              var numericRate = parseFloat(rate);
              if (isNaN(numericRate) || numericRate <= 0) numericRate = 1.0;
              activeDynamicRate = numericRate;

              if (highSpeedAssistTimer) {
                clearInterval(highSpeedAssistTimer);
                highSpeedAssistTimer = null;
              }

              var appliedDirectlyToHtml5Video = false;
              try {
                var vEl = getInnerVideoElement();
                if (vEl) {
                  if ('preservesPitch' in vEl) vEl.preservesPitch = true;
                  if ('webkitPreservesPitch' in vEl) vEl.webkitPreservesPitch = true;
                  vEl.playbackRate = numericRate;
                  if (numericRate !== 1.0 && vEl.paused) {
                    vEl.play();
                  }
                  appliedDirectlyToHtml5Video = (Math.abs(vEl.playbackRate - numericRate) < 0.08);
                }
              } catch(e) {}

              var ytBaseRate = Math.min(2.0, Math.max(0.25, numericRate));
              try {
                if (!appliedDirectlyToHtml5Video || numericRate <= 2.0) {
                  if (player && typeof player.setPlaybackRate === 'function') {
                    player.setPlaybackRate(ytBaseRate);
                  }
                }
                if (numericRate !== 1.0 && player && typeof player.playVideo === 'function') {
                  player.playVideo();
                }
              } catch(e) {}

              try {
                var iframe = document.getElementById('focus-yt-iframe');
                if (iframe && iframe.contentWindow) {
                  if (!appliedDirectlyToHtml5Video || numericRate <= 2.0) {
                    iframe.contentWindow.postMessage(JSON.stringify({
                      event: 'command',
                      func: 'setPlaybackRate',
                      args: [ytBaseRate]
                    }), '*');
                  }
                  if (numericRate !== 1.0) {
                    iframe.contentWindow.postMessage(JSON.stringify({
                      event: 'command',
                      func: 'playVideo',
                      args: []
                    }), '*');
                  }
                }
              } catch(e) {}

              // Re-assert direct HTML5 video playbackRate after 40ms in case YouTube's postMessage handler clamped it to 2.0x
              if (numericRate > 2.0) {
                setTimeout(function() {
                  try {
                    var vEl2 = getInnerVideoElement();
                    if (vEl2 && activeDynamicRate > 2.0) {
                      vEl2.playbackRate = activeDynamicRate;
                    }
                  } catch(e) {}
                }, 40);
              }

              // Fallback micro-step accelerator ONLY if direct HTML5 <video> element could not be reached
              if (numericRate > 2.0 && !appliedDirectlyToHtml5Video) {
                highSpeedAssistTimer = setInterval(function() {
                  try {
                    var vCheck = getInnerVideoElement();
                    if (vCheck) {
                      vCheck.playbackRate = activeDynamicRate;
                      clearInterval(highSpeedAssistTimer);
                      highSpeedAssistTimer = null;
                      return;
                    }
                    var cur = latestKnownTime;
                    if (player && typeof player.getCurrentTime === 'function') {
                      var pt = player.getCurrentTime();
                      if (typeof pt === 'number' && !isNaN(pt) && pt > 0) {
                        cur = pt;
                      }
                    }
                    var nextSec = cur + (0.25 * (numericRate / 2.0));
                    latestKnownTime = nextSec;
                    if (player && typeof player.seekTo === 'function') {
                      player.seekTo(nextSec, true);
                    }
                  } catch(e) {}
                }, 250);
              }
            };

            window.setIframeZoomMode = function(mode) {
              try {
                if (mode) activeZoomMode = mode;
                var ifr = document.getElementById('focus-yt-iframe');
                if (!ifr) return;
                attachSeekBarTouchEnhancer();
                // Do not re-apply DOM styles every 400ms if zoom mode has not changed (prevents cancelling active seek bar drag!)
                if (ifr._appliedZoomMode === activeZoomMode) return;
                ifr._appliedZoomMode = activeZoomMode;

                if (activeZoomMode === 'AUTO_FILL') {
                  ifr.style.transform = 'scale(1.02)';
                } else if (activeZoomMode === 'ULTRA_FILL') {
                  ifr.style.transform = 'scale(1.08)';
                } else {
                  ifr.style.transform = 'scale(1.0)';
                }
                if (ifr.contentDocument && ifr.contentDocument.head) {
                  var styleEl = ifr.contentDocument.getElementById('focus-inner-zoom-style');
                  if (!styleEl) {
                    styleEl = ifr.contentDocument.createElement('style');
                    styleEl.id = 'focus-inner-zoom-style';
                    ifr.contentDocument.head.appendChild(styleEl);
                  }
                  var hideInnerClutterCss = '.ytp-chrome-top, .ytp-chrome-bottom, .ytp-gradient-top, .ytp-gradient-bottom, .ytp-pause-overlay, .ytp-scroll-min, .ytp-ce-element, .ytp-cards-teaser, .ytp-cards-button, .ytp-watermark, .iv-branding, .branding-img, .branding-img-container, .ytp-copylink-button, .ytp-impression-link, .ytp-PaidContentOverlay, .ytp-endscreen-content, .html5-endscreen, .ytp-Videowall-still, .ytp-suggestion-set, .ytp-more-videos-view, .ytp-show-cards-title, .ytp-title, .ytp-title-channel, .ytp-watch-later-button, .ytp-share-button { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; } ';
                  if (mode === 'AUTO_FILL' || mode === 'ULTRA_FILL') {
                    styleEl.textContent = hideInnerClutterCss + 'video.html5-main-video, .html5-video-container video { object-fit: cover !important; width: 100vw !important; height: 100vh !important; left: 0 !important; top: 0 !important; }';
                  } else {
                    styleEl.textContent = hideInnerClutterCss + 'video.html5-main-video, .html5-video-container video { object-fit: contain !important; }';
                  }
                }
              } catch(e) {}
            };
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Use default LAYER_TYPE_NONE so WebView renders directly without offscreen rendernode overhead
                setLayerType(View.LAYER_TYPE_NONE, null)
                setBackgroundColor(android.graphics.Color.BLACK)
                isLongClickable = false
                setOnLongClickListener { true }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    // CRITICAL FOR 2X-4X SPEED & INSTANT TIMESTAMP SEEK: Must be false so seekTo + playVideo never stall
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setSupportZoom(false)
                    // Modern clean mobile Chrome user agent so YouTube delivers hardware-accelerated streams
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                }

                addJavascriptInterface(PlayerJsBridge(), "AndroidBridge")

                // Zoom-Independent Touch Coordinate Normalization +
                // Double-Tap (Left = -10s, Center = Play/Pause, Right = +10s) +
                // Press-and-Hold 2.0x + Horizontal Slide Speed Control (0.5x .. 4.0x) +
                // Two-Finger Pinch-to-Zoom (1.0x .. 2.5x)
                val speedSteps = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
                val defaultHoldIndex = 3 // 2.0x
                val density = ctx.resources.displayMetrics.density
                val touchSlopPx = 10f * density
                val stepWidthPx = 42f * density

                var downX = 0f
                var downY = 0f
                var downUnscaledX = 0f
                var downUnscaledY = 0f
                var downTimestampMs = 0L
                var holdOriginUnscaledX = 0f
                var isHoldSpeedMode = false
                var isPinchGestureActive = false
                var isBottomTimelineScrubActive = false
                var isTopHeaderZoneTouch = false
                var isSideVerticalDragActive = false
                var sideVerticalDragZone = 0 // -1 = Left (Brightness), 1 = Right (Volume)
                var lastSideDragY = 0f
                var currentHoldSpeed = 1.0f

                var lastTapTimeMs = 0L
                var lastTapZone = -99

                /**
                 * Maps a local WebView touch coordinate back to the unscaled [0..viewDim] container coordinate space.
                 * When the WebView is scaled by `S = currentOnZoomScale` around its center `(D/2)`, Android's inverse
                 * matrix maps screen touch `P` to `eventCoord = D/2 + (P - D/2) / S`.
                 * Multiplying `(eventCoord - D/2)` by `S` restores the exact unscaled screen coordinate `P`!
                 */
                fun toUnscaledCoord(eventCoord: Float, viewDim: Float): Float {
                    val scale = currentOnZoomScale.coerceAtLeast(1.0f)
                    val center = viewDim / 2f
                    return (center + (eventCoord - center) * scale).coerceIn(0f, viewDim)
                }

                fun cancelUnderlyingWebViewTouch() {
                    val now = SystemClock.uptimeMillis()
                    val cancelEvt = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
                    onTouchEvent(cancelEvt)
                    cancelEvt.recycle()
                }

                val holdActivationRunnable = Runnable {
                    if (isPinchGestureActive || isBottomTimelineScrubActive || isTopHeaderZoneTouch || isSideVerticalDragActive) return@Runnable
                    isHoldSpeedMode = true
                    holdOriginUnscaledX = downUnscaledX
                    currentHoldSpeed = speedSteps[defaultHoldIndex] // 2.0x
                    parent?.requestDisallowInterceptTouchEvent(true)
                    cancelUnderlyingWebViewTouch()
                    evaluateJavascript(
                        "try{if(window.setDynamicPlaybackSpeed){window.setDynamicPlaybackSpeed(2.0);}}catch(e){};",
                        null
                    )
                    currentOnHoldSpeedChanged(true, 2.0f)
                }

                val scaleGestureDetector = ScaleGestureDetector(
                    ctx,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            if (!isPinchGestureActive) {
                                isPinchGestureActive = true
                                cancelUnderlyingWebViewTouch()
                            }
                            mainHandler.removeCallbacks(holdActivationRunnable)
                            if (isHoldSpeedMode) {
                                isHoldSpeedMode = false
                                currentHoldSpeed = 1.0f
                                evaluateJavascript(
                                    "try{if(window.setDynamicPlaybackSpeed){window.setDynamicPlaybackSpeed(1.0);}}catch(e){};",
                                    null
                                )
                                currentOnHoldSpeedChanged(false, 1.0f)
                            }
                            parent?.requestDisallowInterceptTouchEvent(true)
                            currentOnPinchZoomChanged(true, 1.0f)
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val factor = detector.scaleFactor
                            if (abs(factor - 1.0f) > 0.004f) {
                                currentOnPinchZoomChanged(true, factor)
                            }
                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            currentOnPinchZoomChanged(false, 1.0f)
                            currentOnScreenTapped()
                        }
                    }
                )

                setOnTouchListener { view, event ->
                    scaleGestureDetector.onTouchEvent(event)

                    if (event.pointerCount >= 2) {
                        mainHandler.removeCallbacks(holdActivationRunnable)
                        if (!isPinchGestureActive) {
                            isPinchGestureActive = true
                            cancelUnderlyingWebViewTouch()
                        }
                        return@setOnTouchListener true
                    }

                    val viewW = view.width.toFloat().coerceAtLeast(1f)
                    val viewH = view.height.toFloat().coerceAtLeast(1f)
                    val unscaledX = toUnscaledCoord(event.x, viewW)
                    val unscaledY = toUnscaledCoord(event.y, viewH)
                    val seekHorizPadPx = 16f * density

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            downUnscaledX = unscaledX
                            downUnscaledY = unscaledY
                            downTimestampMs = SystemClock.uptimeMillis()
                            isHoldSpeedMode = false
                            isPinchGestureActive = false
                            isSideVerticalDragActive = false
                            sideVerticalDragZone = 0
                            lastSideDragY = unscaledY
                            currentOnScreenTapped()
                            mainHandler.removeCallbacks(holdActivationRunnable)

                            val bottomSeekZonePx = 44f * density
                            val topHeaderZonePx = 48f * density
                            isBottomTimelineScrubActive = (unscaledY >= viewH - bottomSeekZonePx)
                            isTopHeaderZoneTouch = (unscaledY <= topHeaderZonePx)

                            if (isBottomTimelineScrubActive) {
                                parent?.requestDisallowInterceptTouchEvent(true)
                                val ratio = ((unscaledX - seekHorizPadPx) / (viewW - 2f * seekHorizPadPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                currentOnTimelineSeekRatio(ratio, false)
                                cancelUnderlyingWebViewTouch()
                                true
                            } else if (isTopHeaderZoneTouch) {
                                false
                            } else {
                                mainHandler.postDelayed(holdActivationRunnable, 420L)
                                false
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (isPinchGestureActive) {
                                true
                            } else if (isBottomTimelineScrubActive) {
                                parent?.requestDisallowInterceptTouchEvent(true)
                                val ratio = ((unscaledX - seekHorizPadPx) / (viewW - 2f * seekHorizPadPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                currentOnTimelineSeekRatio(ratio, false)
                                true
                            } else if (isSideVerticalDragActive) {
                                parent?.requestDisallowInterceptTouchEvent(true)
                                val stepDy = lastSideDragY - unscaledY // Positive when moving UP, negative when moving DOWN
                                lastSideDragY = unscaledY
                                val deltaFraction = stepDy / (viewH * 0.65f).coerceAtLeast(1f)
                                if (abs(deltaFraction) > 0.002f) {
                                    currentOnSideVerticalDelta(sideVerticalDragZone, deltaFraction)
                                }
                                true
                            } else if (!isHoldSpeedMode) {
                                val dx = unscaledX - downUnscaledX
                                val dy = unscaledY - downUnscaledY
                                if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                                    mainHandler.removeCallbacks(holdActivationRunnable)
                                    // Check if user is sliding vertically on the Left (Brightness) or Right (Volume) side
                                    val isLeftOrRightSide = (downUnscaledX < viewW * 0.35f) || (downUnscaledX > viewW * 0.65f)
                                    if (isLeftOrRightSide && abs(dy) > abs(dx) * 1.2f && abs(dy) > touchSlopPx) {
                                        isSideVerticalDragActive = true
                                        sideVerticalDragZone = if (downUnscaledX < viewW * 0.35f) -1 else 1
                                        lastSideDragY = unscaledY
                                        parent?.requestDisallowInterceptTouchEvent(true)
                                        cancelUnderlyingWebViewTouch()
                                        val initialDelta = (downUnscaledY - unscaledY) / (viewH * 0.65f).coerceAtLeast(1f)
                                        currentOnSideVerticalDelta(sideVerticalDragZone, initialDelta)
                                        return@setOnTouchListener true
                                    }
                                }
                                false
                            } else {
                                val deltaX = unscaledX - holdOriginUnscaledX
                                val stepOffset = (deltaX / stepWidthPx).roundToInt()
                                val targetIdx = (defaultHoldIndex + stepOffset).coerceIn(0, speedSteps.lastIndex)
                                val newSpeed = speedSteps[targetIdx]
                                if (abs(newSpeed - currentHoldSpeed) > 0.01f) {
                                    currentHoldSpeed = newSpeed
                                    evaluateJavascript(
                                        "try{if(window.setDynamicPlaybackSpeed){window.setDynamicPlaybackSpeed($newSpeed);}}catch(e){};",
                                        null
                                    )
                                    currentOnHoldSpeedChanged(true, newSpeed)
                                }
                                true
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            mainHandler.removeCallbacks(holdActivationRunnable)
                            if (isPinchGestureActive) {
                                isPinchGestureActive = false
                                currentOnPinchZoomChanged(false, 1.0f)
                                currentOnScreenTapped()
                                parent?.requestDisallowInterceptTouchEvent(false)
                                cancelUnderlyingWebViewTouch()
                                true
                            } else if (isBottomTimelineScrubActive) {
                                isBottomTimelineScrubActive = false
                                parent?.requestDisallowInterceptTouchEvent(false)
                                val ratio = ((unscaledX - seekHorizPadPx) / (viewW - 2f * seekHorizPadPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                currentOnTimelineSeekRatio(ratio, true)
                                true
                            } else if (isSideVerticalDragActive) {
                                isSideVerticalDragActive = false
                                sideVerticalDragZone = 0
                                parent?.requestDisallowInterceptTouchEvent(false)
                                true
                            } else if (isHoldSpeedMode) {
                                isHoldSpeedMode = false
                                currentHoldSpeed = 1.0f
                                parent?.requestDisallowInterceptTouchEvent(false)
                                evaluateJavascript(
                                    "try{if(window.setDynamicPlaybackSpeed){window.setDynamicPlaybackSpeed(1.0);}}catch(e){};",
                                    null
                                )
                                currentOnHoldSpeedChanged(false, 1.0f)
                                cancelUnderlyingWebViewTouch()
                                true
                            } else {
                                val nowMs = SystemClock.uptimeMillis()
                                val isQuickTap = (event.actionMasked == MotionEvent.ACTION_UP) &&
                                    (nowMs - downTimestampMs <= 280L) &&
                                    (abs(unscaledX - downUnscaledX) <= touchSlopPx * 1.5f) &&
                                    (abs(unscaledY - downUnscaledY) <= touchSlopPx * 1.5f) &&
                                    !isTopHeaderZoneTouch

                                if (isQuickTap) {
                                    val tapZone = when {
                                        unscaledX < viewW * 0.35f -> -1 // Left Zone: -10s Backward
                                        unscaledX > viewW * 0.65f -> 1  // Right Zone: +10s Forward
                                        else -> 0                       // Center Zone: Play / Pause
                                    }
                                    if (nowMs - lastTapTimeMs <= 320L && tapZone == lastTapZone) {
                                        // Double Tap Detected!
                                        lastTapTimeMs = 0L
                                        lastTapZone = -99
                                        cancelUnderlyingWebViewTouch()
                                        currentOnDoubleTapAction(tapZone)
                                        return@setOnTouchListener true
                                    } else {
                                        lastTapTimeMs = nowMs
                                        lastTapZone = tapZone
                                        if (tapZone == 0 && !currentIsVideoPlaying) {
                                            // Video is paused or in initial unstarted state:
                                            // Clicking in the Center immediately starts playing the video
                                            // AND lets the native WebView click hit YouTube's center Play button!
                                            evaluateJavascript(
                                                "try{if(window.playScene){window.playScene();}}catch(e){};",
                                                null
                                            )
                                            return@setOnTouchListener false
                                        }
                                        // When video is already playing, cancel single-tap on WebView so single-tap only reveals HUD,
                                        // while Double-Tap in Center toggles Play/Pause!
                                        cancelUnderlyingWebViewTouch()
                                        return@setOnTouchListener true
                                    }
                                }
                                isTopHeaderZoneTouch = false
                                false
                            }
                        }
                        else -> false
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingChanged(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingChanged(false)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (request?.isForMainFrame == false) {
                            return false // DO NOT BLOCK VIDEO RESOURCES OR SUBFRAMES!
                        }
                        if (url.contains("youtube-nocookie.com") || url.contains("googlevideo.com") || url.contains("ytimg.com") || url.contains("gstatic.com")) {
                            return false
                        }
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view != null && callback != null) {
                            onShowFullscreen(view, callback)
                        }
                    }

                    override fun onHideCustomView() {
                        onHideFullscreen()
                    }
                }

                // Family Link compatible Base URL (youtube-nocookie.com is never blocked by Chrome Family Link)
                loadDataWithBaseURL(
                    "https://www.youtube-nocookie.com",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            if (replayTrigger > lastHandledReplay) {
                lastHandledReplay = replayTrigger
                webView.loadUrl("javascript:try{if(window.seekToScene){window.seekToScene(0);}}catch(e){};")
            }
            if (pauseTrigger > lastHandledPause) {
                lastHandledPause = pauseTrigger
                webView.loadUrl("javascript:try{if(window.pauseScene){window.pauseScene();}}catch(e){};")
            }
            if (togglePlayPauseTrigger > lastHandledTogglePlayPause) {
                lastHandledTogglePlayPause = togglePlayPauseTrigger
                webView.loadUrl("javascript:try{if(window.togglePlayPauseScene){window.togglePlayPauseScene();}}catch(e){};")
            }
            if (seekCommand != null && seekCommand.commandId != lastHandledSeekId) {
                lastHandledSeekId = seekCommand.commandId
                val targetSec = seekCommand.seconds.coerceAtLeast(0)
                webView.loadUrl("javascript:try{if(window.seekToScene){window.seekToScene($targetSec);}}catch(e){};")
            }
            if (fullscreenZoomMode != lastHandledZoomMode) {
                lastHandledZoomMode = fullscreenZoomMode
                webView.loadUrl("javascript:try{if(window.setIframeZoomMode){window.setIframeZoomMode('$fullscreenZoomMode');}}catch(e){};")
            }
            if (playerVolumePercent != lastHandledVolumePercent) {
                lastHandledVolumePercent = playerVolumePercent
                webView.loadUrl("javascript:try{if(window.setPlayerVolume){window.setPlayerVolume($playerVolumePercent);}}catch(e){};")
            }
        }
    )
}

/**
 * Focus Shield Blocked Curtain:
 * Displayed when a video is flagged as potential distraction.
 * Provides user with full control:
 * 1. Allow & Play ("यह पढ़ाई/काम का वीडियो है - चालू करें")
 * 2. Block & Remove ("हटा दें")
 * 3. Back
 */
@Composable
fun DistractionBlockedCurtain(
    videoTitle: String,
    onBack: () -> Unit,
    onAllowAndPlay: () -> Unit = {},
    onDeleteAndBack: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F141C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Shield Locked",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "🛡️ Focus Shield: Content Blocked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "'$videoTitle'",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "If this video is related to your study, education, or work, tap 'Allow & Play' to watch it immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Option 1: Allow & Play (for educational & important videos)
            Button(
                onClick = onAllowAndPlay,
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("📖 Allow & Play (Educational / Work Video)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Option 2: Block & Delete
            androidx.compose.material3.OutlinedButton(
                onClick = onDeleteAndBack,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("🚫 Block & Delete", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(6.dp))

            androidx.compose.material3.TextButton(
                onClick = onBack
            ) {
                Text("Back", color = Color.LightGray)
            }
        }
    }
}

