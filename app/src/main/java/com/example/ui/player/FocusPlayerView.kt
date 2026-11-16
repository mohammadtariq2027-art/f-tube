package com.example.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
                val body = startMatch.groupValues[2].trim().ifBlank { "सीन नोट ($tsStr)" }
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
                "▶ सीन [${formatSecondsToTimestamp(initialSeekSeconds)}] से वीडियो चालू हो गई!"
            } else null
        )
    }
    var quickSceneTimestampInput by remember(video.videoId) { mutableStateOf("0:00") }
    var isQuickTimestampLocked by remember(video.videoId) { mutableStateOf(false) }
    var quickSceneNoteText by remember(video.videoId) { mutableStateOf("") }
    var isFullscreenNoteDrawerOpen by remember(video.videoId) { mutableStateOf(false) }
    var sceneNoteSearchQuery by remember(video.videoId) { mutableStateOf("") }
    var isSceneNoteSearchActive by remember(video.videoId) { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var currentDescription by remember(video.id) { mutableStateOf(video.description) }
    var showSaveToPlaylistDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
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
        isVideoEnded = false
        currentPlaybackSeconds = seconds
        seekCommand = VideoSeekCommand(seconds = seconds)
        lastSeekFeedback = "▶ सीन [$label] से वीडियो चालू हो गई!"
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

    // Every video automatically opens in Fullscreen according to its orientation:
    // - Portrait videos open in Portrait Fullscreen
    // - Landscape videos open in Landscape Fullscreen
    var isAppFullscreen by remember(video.videoId) { mutableStateOf(true) }

    // Automatic Fullscreen Zoom-to-Fill (eliminates black bars on sides and top/bottom by default
    // using inner HTML5 <video> object-fit: cover + subtle edge-bleed scale so controls stay visible)
    // Modes: "AUTO_FILL" (default crop-to-fill, zero black bars), "ULTRA_FILL" (deep zoom), "FIT_ORIGINAL" (1.0x)
    var fullscreenZoomMode by remember(video.videoId) { mutableStateOf("AUTO_FILL") }
    val targetZoomScale = if (!isAppFullscreen) {
        1.0f
    } else {
        when (fullscreenZoomMode) {
            "AUTO_FILL" -> 1.06f
            "ULTRA_FILL" -> 1.18f
            else -> 1.0f
        }
    }
    val animatedZoomScale by animateFloatAsState(
        targetValue = targetZoomScale,
        animationSpec = tween(durationMillis = 220),
        label = "fullscreenAutoZoomScale"
    )

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
                onBack()
            }
        }
    }

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
                    fullscreenZoomMode = if (isAppFullscreen) fullscreenZoomMode else "FIT_ORIGINAL",
                    onHoldSpeedChanged = { holding, speed ->
                        isHoldSpeedActive = holding
                        activeHoldSpeed = speed
                    },
                    onCurrentTimeChanged = { sec ->
                        currentPlaybackSeconds = sec
                        if (!isQuickTimestampLocked && quickSceneNoteText.isBlank()) {
                            quickSceneTimestampInput = formatSecondsToTimestamp(sec)
                        }
                    },
                    onVideoOrientationDetected = { isPortrait ->
                        detectedPortraitFromPlayer = isPortrait
                    },
                    onLoadingChanged = { isPlayerLoading = it },
                    onVideoEnded = { isVideoEnded = true },
                    onShowFullscreen = { view, callback ->
                        customFullscreenView = view
                        customViewCallback = callback
                    },
                    onHideFullscreen = {
                        customFullscreenView = null
                        customViewCallback = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer(
                            scaleX = animatedZoomScale,
                            scaleY = animatedZoomScale
                        )
                )

                // === LIVE PRESS-AND-HOLD SPEED SLIDE HUD OVERLAY (0.5x ➔ 1.0x ➔ 1.5x ➔ 2.0x ➔ 2.5x ➔ 3.0x ➔ 3.5x ➔ 4.0x) ===
                if (isHoldSpeedActive) {
                    val speedLevels = remember { listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f) }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xEB0B131E),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                            .border(
                                width = 1.5.dp,
                                color = if (activeHoldSpeed >= 2.0f) Color(0xFF00E676) else Color(0xFF4FC3F7),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .testTag("fullscreen_hold_speed_hud")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when {
                                        activeHoldSpeed >= 3.0f -> "🚀 ${activeHoldSpeed}x अल्ट्रा फास्ट (Locked while holding)"
                                        activeHoldSpeed >= 2.0f -> "⏩ ${activeHoldSpeed}x फास्ट स्पीड (Locked while holding)"
                                        activeHoldSpeed > 1.0f -> "🏃 ${activeHoldSpeed}x स्पीड"
                                        activeHoldSpeed == 1.0f -> "▶️ 1.0x नॉर्मल स्पीड"
                                        else -> "🐢 ${activeHoldSpeed}x स्लो-मोशन"
                                    },
                                    color = if (activeHoldSpeed >= 2.0f) Color(0xFF00E676) else Color(0xFF81D4FA),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 8-Step Speed Level Track (0.5x .. 4.0x)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                speedLevels.forEach { lvl ->
                                    val isCurrent = abs(lvl - activeHoldSpeed) < 0.05f
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isCurrent && lvl >= 2.0f -> Color(0xFF00C853)
                                            isCurrent -> Color(0xFF0288D1)
                                            else -> Color.White.copy(alpha = 0.12f)
                                        },
                                        modifier = Modifier.border(
                                            width = if (isCurrent) 1.2.dp else 0.dp,
                                            color = if (isCurrent) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    ) {
                                        Text(
                                            text = "${lvl}x",
                                            color = if (isCurrent) Color.White else Color(0xFFB0BEC5),
                                            fontSize = if (isCurrent) 11.sp else 9.sp,
                                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            modifier = Modifier.padding(
                                                horizontal = if (isCurrent) 8.dp else 5.dp,
                                                vertical = if (isCurrent) 4.dp else 2.dp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                text = "⬅️ बाएं स्लाइड: 1.5x•1.0x•0.5x | दाएं स्लाइड: 2.5x•3x•3.5x•4x ➡️ (उंगली हटाते ही 1x)",
                                color = Color(0xFFCFD8DC),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Floating Quick Controls when in app fullscreen (Back, Auto-Orientation Badge, Auto Zoom-Fill Badge, Save to Playlist, Toggleable Note Drawer, Exit Fullscreen)
                if (isAppFullscreen) {
                    // Top-Left Back button + Orientation Badge + Auto Zoom-to-Fill (No Black Bars) Badge in Fullscreen
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Orientation Mode Toggle Pill (Landscape vs Portrait Auto-Fullscreen)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.60f),
                            modifier = Modifier
                                .clickable {
                                    detectedPortraitFromPlayer = !isVerticalVideo
                                }
                                .testTag("fullscreen_orientation_mode_badge")
                        ) {
                            Text(
                                text = if (isVerticalVideo) "📱 Portrait" else "🖥️ Landscape",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        // Auto Zoom-to-Fill (No Black Bars) Mode Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (fullscreenZoomMode != "FIT_ORIGINAL") Color(0xFF00695C).copy(alpha = 0.85f)
                            else Color.Black.copy(alpha = 0.60f),
                            modifier = Modifier
                                .clickable {
                                    fullscreenZoomMode = when (fullscreenZoomMode) {
                                        "AUTO_FILL" -> "ULTRA_FILL"
                                        "ULTRA_FILL" -> "FIT_ORIGINAL"
                                        else -> "AUTO_FILL"
                                    }
                                }
                                .testTag("fullscreen_zoom_fill_badge")
                        ) {
                            Text(
                                text = when (fullscreenZoomMode) {
                                    "AUTO_FILL" -> "🔍 Auto Zoom-Fill (No Bars)"
                                    "ULTRA_FILL" -> "🔍 Ultra Fill (1.36x)"
                                    else -> "🔍 Original Fit (1.0x)"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Top-Right Action Bar in Fullscreen (+ Save, Toggleable Pop-Out Note Drawer, Compact View)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.clickable { showSaveToPlaylistDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = "Save to Playlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+ Save",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Toggle Button for Pop-Out Fullscreen Note Drawer!
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isFullscreenNoteDrawerOpen) Color(0xFF2E7D32) else Color(0xFFE65100).copy(alpha = 0.92f),
                            modifier = Modifier
                                .clickable {
                                    val opening = !isFullscreenNoteDrawerOpen
                                    isFullscreenNoteDrawerOpen = opening
                                    if (opening) {
                                        pauseTrigger++
                                        quickSceneTimestampInput = formatSecondsToTimestamp(currentPlaybackSeconds)
                                        isQuickTimestampLocked = true
                                    }
                                }
                                .testTag("fullscreen_toggle_note_drawer_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isFullscreenNoteDrawerOpen) Icons.Default.Close else Icons.Default.Edit,
                                    contentDescription = "Toggle Fullscreen Note Panel",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isFullscreenNoteDrawerOpen) {
                                        "✕ नोट बंद करें"
                                    } else {
                                        "📝 नोट लें [${formatSecondsToTimestamp(currentPlaybackSeconds)}]"
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Clean HUD Hide/Show Toggle Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .clickable { isFullscreenHudVisible = !isFullscreenHudVisible }
                                .testTag("fullscreen_hud_visibility_button")
                        ) {
                            Text(
                                text = if (isFullscreenHudVisible) "👁️ HUD" else "👁️‍🗨️",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            IconButton(
                                onClick = {
                                    isFullscreenNoteDrawerOpen = false
                                    isAppFullscreen = false
                                },
                                modifier = Modifier
                                    .size(38.dp)
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

                    // === FULLSCREEN BOTTOM-CENTER QUICK TRANSPORT BAR (-10s, Play/Pause, +10s, Live Time, Speed Hint) ===
                    if (isFullscreenHudVisible && !isFullscreenNoteDrawerOpen) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Black.copy(alpha = 0.62f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .testTag("fullscreen_quick_transport_bar")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // -10s Quick Rewind
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .clickable {
                                            val rewindSec = (currentPlaybackSeconds - 10).coerceAtLeast(0)
                                            jumpToSceneTimestamp(rewindSec, formatSecondsToTimestamp(rewindSec))
                                        }
                                        .testTag("fullscreen_rewind_10s_button")
                                ) {
                                    Text(
                                        text = "⏪ -10s",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }

                                // Play / Pause Toggle
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .clickable { togglePlayPauseTrigger++ }
                                        .testTag("fullscreen_play_pause_button")
                                ) {
                                    Text(
                                        text = "⏯ Play/Pause",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                // +10s Quick Forward
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .clickable {
                                            val forwardSec = currentPlaybackSeconds + 10
                                            jumpToSceneTimestamp(forwardSec, formatSecondsToTimestamp(forwardSec))
                                        }
                                        .testTag("fullscreen_forward_10s_button")
                                ) {
                                    Text(
                                        text = "+10s ⏩",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }

                                // Live Timestamp & Hold-for-2x..4x Tip
                                Text(
                                    text = "⏱ ${formatSecondsToTimestamp(currentPlaybackSeconds)} • दबाए रखें: 2x..4x",
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // === TOGGLEABLE POP-OUT COMPACT NOTE DRAWER IN FULLSCREEN MODE ===
                    if (isFullscreenNoteDrawerOpen) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xEB121722),
                            tonalElevation = 10.dp,
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .align(if (isVerticalVideo) Alignment.BottomCenter else Alignment.CenterEnd)
                                .statusBarsPadding()
                                .padding(
                                    top = if (isVerticalVideo) 64.dp else 56.dp,
                                    end = 12.dp,
                                    start = 12.dp,
                                    bottom = 14.dp
                                )
                                .width(if (isVerticalVideo) 360.dp else 310.dp)
                                .testTag("fullscreen_floating_note_drawer")
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Header: Live Scene Lock + Editable Time + Close
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFE65100).copy(alpha = 0.25f),
                                        modifier = Modifier.clickable {
                                            pauseTrigger++
                                            quickSceneTimestampInput = formatSecondsToTimestamp(currentPlaybackSeconds)
                                            isQuickTimestampLocked = true
                                        }
                                    ) {
                                        Text(
                                            text = "⏸ सीन लॉक: ${formatSecondsToTimestamp(currentPlaybackSeconds)}",
                                            color = Color(0xFFFFB74D),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "⏱",
                                            fontSize = 11.sp,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        OutlinedTextField(
                                            value = quickSceneTimestampInput,
                                            onValueChange = {
                                                quickSceneTimestampInput = it
                                                isQuickTimestampLocked = true
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFFFFB74D),
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier
                                                .width(68.dp)
                                                .testTag("fullscreen_drawer_timestamp_input"),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Compact Note Input + Save Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = quickSceneNoteText,
                                        onValueChange = {
                                            if (quickSceneNoteText.isEmpty() && it.isNotEmpty() && !isQuickTimestampLocked) {
                                                quickSceneTimestampInput = formatSecondsToTimestamp(currentPlaybackSeconds)
                                                isQuickTimestampLocked = true
                                            }
                                            quickSceneNoteText = it
                                        },
                                        placeholder = {
                                            Text(
                                                "सीन [${quickSceneTimestampInput.ifBlank { formatSecondsToTimestamp(currentPlaybackSeconds) }}] का नोट लिखें...",
                                                fontSize = 11.sp,
                                                color = Color.LightGray
                                            )
                                        },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                        maxLines = 2,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("fullscreen_drawer_note_input")
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
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        modifier = Modifier.testTag("fullscreen_drawer_save_button")
                                    ) {
                                        Text("+ सेव", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Seek feedback inside fullscreen drawer
                                lastSeekFeedback?.let { msg ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = msg,
                                        color = Color(0xFF81C784),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Saved Scene Notes inside Fullscreen Drawer (Tap any ▶ 6:02 badge to jump immediately!)
                                if (parsedSceneNotes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "📋 सेव सीन नोट्स (${parsedSceneNotes.size}) — टैप करके चलाएं:",
                                        color = Color(0xFF90CAF9),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        parsedSceneNotes.forEach { entry ->
                                            val hasTs = entry.timestampSeconds != null && entry.timestampLabel != null
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White.copy(alpha = 0.10f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (hasTs) {
                                                            jumpToSceneTimestamp(entry.timestampSeconds!!, entry.timestampLabel!!)
                                                        }
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                ) {
                                                    if (hasTs) {
                                                        Surface(
                                                            shape = RoundedCornerShape(5.dp),
                                                            color = Color(0xFFE65100)
                                                        ) {
                                                            Text(
                                                                text = "▶ ${entry.timestampLabel}",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                    }
                                                    Text(
                                                        text = entry.noteBody,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
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
                        .padding(16.dp)
                ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = video.channelName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (video.uploadDate.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = video.uploadDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val displayDuration = com.example.util.DurationUtils.formatCleanDuration(video.duration, video.videoId)
                    if (displayDuration.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⏱ $displayDuration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

            Spacer(modifier = Modifier.height(14.dp))

            // === VIDEO PLAYLIST & NOTE/COMMENT STATUS OVERVIEW CARD ===
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_video_status_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📌 वीडियो की स्थिति (Playlist & Note Status):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // 1. Saved Playlist(s) Status
                    if (savedInPlaylists.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (savedInPlaylists.size >= 2) Color(0xFF6A1B9A).copy(alpha = 0.15f) else Color(0xFF1B5E20).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (savedInPlaylists.size >= 2) {
                                    "⚡ यह वीडियो ${savedInPlaylists.size} प्लेलिस्ट (डबल/मल्टी) में सेव है: " + savedInPlaylists.joinToString(", ") { it.title }
                                } else {
                                    "✅ यह वीडियो प्लेलिस्ट में सेव है: 📁 ${savedInPlaylists.first().title}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (savedInPlaylists.size >= 2) Color(0xFF6A1B9A) else Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE65100).copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚪ यह वीडियो अभी किसी भी प्लेलिस्ट में सेव नहीं है",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. Note / Comment Status
                    if (currentNotes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNotesDialog = true }
                        ) {
                            Text(
                                text = "📝 नोट/कमेंट लिखा हुआ है: \"${currentNotes.trim()}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNotesDialog = true }
                        ) {
                            Text(
                                text = "💬 इस वीडियो पर अभी कोई नोट/कमेंट नहीं लिखा है (+ नोट लिखने के लिए टैप करें)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action: Save to Playlist
            Button(
                onClick = { showSaveToPlaylistDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_to_playlist_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (savedInPlaylists.isNotEmpty()) {
                        "✅ सेव है (${savedInPlaylists.size} प्लेलिस्ट) • + दूसरी प्लेलिस्ट में जोड़ें"
                    } else {
                        "Save to Playlist / प्लेलिस्ट में सेव करें"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Video Notes Section (सीन टाइमस्टैम्प नोट्स - Click timestamp to jump & play from that exact scene!)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_notes_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Scene Notes / सीन टाइमस्टैम्प नोट्स",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "टाइम हाईलाइट (जैसे ▶ 6:02) पर क्लिक करते ही वीडियो वहीं से चलेगी",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
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
                            modifier = Modifier.testTag("edit_notes_button")
                        ) {
                            Text("Full Editor", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                            text = "⏸ अभी का सीन लॉक करें: ${formatSecondsToTimestamp(currentPlaybackSeconds)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }

                                // Editable Timestamp Box (e.g. user can also type 6:02 manually)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "⏱ टाइम:",
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
                                            "इस सीन [${quickSceneTimestampInput.ifBlank { formatSecondsToTimestamp(currentPlaybackSeconds) }}] की काम की बात लिखें...",
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
                                    Text("जोड़ें", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                text = "📋 आपके सेव किए गए सीन नोट्स (${filteredSceneNotes.size}/${parsedSceneNotes.size}) — चलाने के लिए टैप करें:",
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
                                placeholder = { Text("🔍 इस वीडियो के नोट्स या टाइमस्टैम्प (जैसे 6:02) खोजें...", fontSize = 11.sp) },
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
                                                        text = "👆 टैप करें — वीडियो ${entry.timestampLabel} से चालू होगी",
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
                            text = "🌙 ये सभी सीन टाइमस्टैम्प नोट्स इसी वीडियो के साथ लिंक हैं और ऑटो-बैकअप में सुरक्षित रहते हैं।",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "उदाहरण: अगर 10 मिनट की वीडियो में 6 मिनट 2 सेकंड (6:02) पर कोई काम की बात है, तो ऊपर नोट लिखकर 'जोड़ें' दबाएं। उस पर [▶ 6:02] का हाईलाइट आ जाएगा जिस पर क्लिक करते ही वीडियो वहीं से चालू हो जाएगी!",
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
            Text("Scene Notes / वीडियो सीन नोट्स", fontWeight = FontWeight.Bold)
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
                        Text("⏱ + अभी का सीन [$currentSceneTimestamp]", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            noteText = if (noteText.isBlank()) "[6:02] " else "${noteText.trimEnd()}\n[6:02] "
                        }
                    ) {
                        Text("+ [6:02] उदाहरण", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("जैसे:\n[6:02] यहाँ मुख्य पॉइंट समझाया गया है\n[8:15] दूसरा महत्वपूर्ण सीन...") },
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
                        text = "▶ किसी भी टाइम हाईलाइट पर क्लिक करके वीडियो वहीं से चलाएं:",
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
                            text = "✅ पहले से सेव है (${alreadySavedPlaylists.size}): " + alreadySavedPlaylists.joinToString(", ") { it.title },
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
                            text = "⚪ यह वीडियो अभी किसी भी प्लेलिस्ट में सेव नहीं है",
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
                                label = { Text("🕒 हाल ही में", style = MaterialTheme.typography.labelSmall) }
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
                                Text("सारी लाएं", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Text(
                                text = "↕ ${filteredPlaylists.size} स्क्रॉल करें",
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
                            text = "कोई मिलती-जुलती प्लेलिस्ट नहीं मिली।",
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
                                                        text = "✅ सेव है",
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
                                                        text = "🕒 हाल ही में",
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
                                text = "✅ ओरिजिनल YouTube प्लेलिस्ट Auto-Save सक्रिय है (Save दबाते ही YouTube ऐप में भी सेव होगा)",
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
                                text = "साथ में YouTube App भी खोलें ↗",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "सेव करने के तुरंत बाद YouTube ऐप में वीडियो खोलें",
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
    fullscreenZoomMode: String = "AUTO_FILL",
    onHoldSpeedChanged: (Boolean, Float) -> Unit = { _, _ -> },
    onCurrentTimeChanged: (Int) -> Unit = {},
    onVideoOrientationDetected: (Boolean) -> Unit = {},
    onLoadingChanged: (Boolean) -> Unit,
    onVideoEnded: () -> Unit,
    onShowFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val currentOnHoldSpeedChanged by rememberUpdatedState(onHoldSpeedChanged)
    val currentOnCurrentTimeChanged by rememberUpdatedState(onCurrentTimeChanged)
    val currentOnVideoOrientationDetected by rememberUpdatedState(onVideoOrientationDetected)
    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)
    var lastHandledReplay by remember(videoId) { mutableStateOf(0) }
    var lastHandledPause by remember(videoId) { mutableStateOf(0) }
    var lastHandledTogglePlayPause by remember(videoId) { mutableStateOf(0) }
    var lastHandledSeekId by remember(videoId) { mutableStateOf(0L) }
    var lastHandledZoomMode by remember(videoId) { mutableStateOf("") }
    val startParamSeconds = (initialSeekSeconds ?: 0).coerceAtLeast(0)
    val autoplayParam = if (startParamSeconds > 0) 1 else 0

    class PlayerJsBridge {
        @JavascriptInterface
        fun reportEnded() {
            mainHandler.post { currentOnVideoEnded() }
        }

        @JavascriptInterface
        fun reportCurrentTime(seconds: Int) {
            if (seconds >= 0) {
                mainHandler.post { currentOnCurrentTimeChanged(seconds) }
            }
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
              src="https://www.youtube-nocookie.com/embed/$videoId?enablejsapi=1&autoplay=$autoplayParam&start=$startParamSeconds&controls=1&rel=0&modestbranding=1&iv_load_policy=3&fs=1&playsinline=1"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen"
              allowfullscreen>
            </iframe>
          </div>
          <script>
            var player;
            var lastReportedSec = -1;
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
                      if (initialStartSec > 0) {
                        window.seekToScene(initialStartSec);
                      }
                    },
                    'onStateChange': function(event) {
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

            var activeZoomMode = 'AUTO_FILL';

            // Poll current time every 400ms, detect exact video stream aspect ratio, & ensure inner zoom style is injected
            setInterval(function() {
              try {
                var vEl = getInnerVideoElement();
                if (vEl) {
                  if (typeof vEl.currentTime === 'number' && !isNaN(vEl.currentTime) && vEl.currentTime > 0) {
                    notifyTime(vEl.currentTime);
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
              try {
                if (player && typeof player.seekTo === 'function') {
                  player.seekTo(sec, true);
                  if (typeof player.playVideo === 'function') {
                    player.playVideo();
                  }
                }
              } catch(e) {}
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
            };

            window.pauseScene = function() {
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
            };

            window.togglePlayPauseScene = function() {
              try {
                var vEl = getInnerVideoElement();
                if (vEl) {
                  if (vEl.paused) {
                    vEl.play();
                  } else {
                    vEl.pause();
                  }
                  return;
                }
              } catch(e) {}
              try {
                if (player && typeof player.getPlayerState === 'function') {
                  var st = player.getPlayerState();
                  if (st === 1) {
                    player.pauseVideo();
                  } else {
                    player.playVideo();
                  }
                }
              } catch(e) {}
            };

            function getInnerVideoElement() {
              try {
                var ifr = document.getElementById('focus-yt-iframe');
                if (ifr && ifr.contentDocument) {
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
                if (activeZoomMode === 'AUTO_FILL') {
                  ifr.style.transform = 'scale(1.02)';
                } else if (activeZoomMode === 'ULTRA_FILL') {
                  ifr.style.transform = 'scale(1.08)';
                } else {
                  ifr.style.transform = 'scale(1.0)';
                }
                // Inject object-fit: cover directly on the inner HTML5 <video> so video fills 100% of screen with zero black bars!
                if (ifr.contentDocument && ifr.contentDocument.head) {
                  var styleEl = ifr.contentDocument.getElementById('focus-inner-zoom-style');
                  if (!styleEl) {
                    styleEl = ifr.contentDocument.createElement('style');
                    styleEl.id = 'focus-inner-zoom-style';
                    ifr.contentDocument.head.appendChild(styleEl);
                  }
                  if (mode === 'AUTO_FILL' || mode === 'ULTRA_FILL') {
                    styleEl.textContent = 'video.html5-main-video, .html5-video-container video { object-fit: cover !important; width: 100vw !important; height: 100vh !important; left: 0 !important; top: 0 !important; }';
                  } else {
                    styleEl.textContent = 'video.html5-main-video, .html5-video-container video { object-fit: contain !important; }';
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

                // 1. Force hardware acceleration for smooth 60fps/120fps video rendering
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
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

                // Press-and-Hold 2.0x + Horizontal Slide Speed Control (0.5x ➔ 1.0x ➔ 1.5x ➔ 2.0x ➔ 2.5x ➔ 3.0x ➔ 3.5x ➔ 4.0x)
                val speedSteps = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
                val defaultHoldIndex = 3 // 2.0x
                val density = ctx.resources.displayMetrics.density
                val touchSlopPx = 20f * density
                val stepWidthPx = 42f * density

                var downX = 0f
                var downY = 0f
                var holdOriginX = 0f
                var isHoldSpeedMode = false
                var currentHoldSpeed = 1.0f

                val holdActivationRunnable = Runnable {
                    isHoldSpeedMode = true
                    holdOriginX = downX
                    currentHoldSpeed = speedSteps[defaultHoldIndex] // 2.0x
                    parent?.requestDisallowInterceptTouchEvent(true)
                    // Immediately cancel any pending WebView internal tap/scrub gesture so sliding only controls speed
                    val now = SystemClock.uptimeMillis()
                    val cancelEvt = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
                    onTouchEvent(cancelEvt)
                    cancelEvt.recycle()
                    evaluateJavascript(
                        "try{if(window.setDynamicPlaybackSpeed){window.setDynamicPlaybackSpeed(2.0);}}catch(e){};",
                        null
                    )
                    currentOnHoldSpeedChanged(true, 2.0f)
                }

                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            isHoldSpeedMode = false
                            mainHandler.removeCallbacks(holdActivationRunnable)
                            mainHandler.postDelayed(holdActivationRunnable, 260L)
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!isHoldSpeedMode) {
                                if (abs(event.x - downX) > touchSlopPx || abs(event.y - downY) > touchSlopPx) {
                                    mainHandler.removeCallbacks(holdActivationRunnable)
                                }
                                false
                            } else {
                                val deltaX = event.x - holdOriginX
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
                            if (isHoldSpeedMode) {
                                isHoldSpeedMode = false
                                currentHoldSpeed = 1.0f
                                parent?.requestDisallowInterceptTouchEvent(false)
                                evaluateJavascript(
                                    "try{if(window.setDynamicPlaybackSpeed){window.setDynamicPlaybackSpeed(1.0);}}catch(e){};",
                                    null
                                )
                                currentOnHoldSpeedChanged(false, 1.0f)
                                // Cancel underlying WebView click so releasing finger after speed-hold doesn't pause the video
                                val now = SystemClock.uptimeMillis()
                                val cancelEvt = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, event.x, event.y, 0)
                                view.onTouchEvent(cancelEvt)
                                cancelEvt.recycle()
                                true
                            } else {
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
                text = "अगर यह वीडियो आपकी पढ़ाई, शिक्षा, या काम से जुड़ा है, तो आप 'Allow & Play' दबाकर इसे तुरंत चला सकते हैं।",
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
                Text("📖 Allow & Play (यह पढ़ाई का वीडियो है)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Option 2: Block & Delete
            androidx.compose.material3.OutlinedButton(
                onClick = onDeleteAndBack,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("🚫 Block & Delete / हटा दें", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(6.dp))

            androidx.compose.material3.TextButton(
                onClick = onBack
            ) {
                Text("Back / वापस जाएं", color = Color.LightGray)
            }
        }
    }
}

