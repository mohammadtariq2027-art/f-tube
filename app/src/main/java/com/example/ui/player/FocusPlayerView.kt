package com.example.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.VideoEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPlayerView(
    video: VideoEntity,
    headerTitle: String,
    allPlaylists: List<PlaylistEntity> = emptyList(),
    onBack: () -> Unit,
    onToggleCompleted: (VideoEntity) -> Unit,
    onSaveToPlaylist: ((VideoEntity, String) -> Unit)? = null,
    onCreatePlaylistAndSaveVideo: ((String, VideoEntity) -> Unit)? = null,
    onFetchDescription: (suspend (String) -> String)? = null,
    onUpdateNotes: ((Long, String) -> Unit)? = null,
    onDeleteVideo: ((Long) -> Unit)? = null
) {
    // 🛡️ Instant Focus Shield Interceptor: If this is a music or distraction video, block it instantly!
    val isDistraction = remember(video.title, video.channelName) {
        com.example.util.FocusShield.isDistractionVideo(video.title, video.channelName)
    }

    if (isDistraction) {
        // Immediately purge from database so it never appears again
        LaunchedEffect(video.id) {
            onDeleteVideo?.invoke(video.id)
        }

        DistractionBlockedCurtain(
            videoTitle = video.title,
            onBack = onBack
        )
        return
    }

    var isPlayerLoading by remember(video.videoId) { mutableStateOf(true) }
    var isVideoEnded by remember(video.videoId) { mutableStateOf(false) }
    var replayTrigger by remember(video.videoId) { mutableStateOf(0) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var currentDescription by remember(video.id) { mutableStateOf(video.description) }
    var showSaveToPlaylistDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var currentNotes by remember(video.id, video.notes) { mutableStateOf(video.notes) }
    var isAppFullscreen by remember { mutableStateOf(false) }

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

    // Auto-Orientation Fullscreen Management:
    // If the video is vertical (Shorts / 9:16), keep orientation in portrait (SENSOR_PORTRAIT).
    // If horizontal (standard YouTube 16:9), rotate to landscape (SENSOR_LANDSCAPE).
    val isVerticalVideo = remember(video.title, video.duration) {
        video.title.contains("#shorts", ignoreCase = true) ||
        video.title.contains("#short", ignoreCase = true) ||
        video.title.contains("shorts", ignoreCase = true)
    }

    DisposableEffect(isAppFullscreen, customFullscreenView) {
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

    // Back handling for Fullscreen modes
    BackHandler(enabled = customFullscreenView != null || isAppFullscreen) {
        if (customFullscreenView != null) {
            customViewCallback?.onCustomViewHidden()
            customFullscreenView = null
            customViewCallback = null
        } else if (isAppFullscreen) {
            isAppFullscreen = false
        }
    }

    // 1. Native HTML5 Fullscreen (triggered by YouTube player's built-in full screen button)
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

            // Floating Exit Fullscreen Button
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
        return
    }

    // 2. Continuous Single Player Layout (Seamless fullscreen without unmounting WebView)
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
                modifier = if (isAppFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }.background(Color.Black)
            ) {
                ZeroSuggestionPlayer(
                    videoId = video.videoId,
                    replayTrigger = replayTrigger,
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
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Exit Fullscreen Pill when in app fullscreen
                if (isAppFullscreen) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = { isAppFullscreen = false },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
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

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(16.dp))

            // Single Primary Action: Save to Playlist
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
                    text = "Save to Playlist",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Video Notes Section (वीडियो नोट्स)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_notes_card"),
                shape = RoundedCornerShape(12.dp),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Video Notes / वीडियो नोट्स",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (currentNotes.isNotBlank()) {
                            FilledTonalButton(
                                onClick = { showNotesDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("edit_notes_button")
                            ) {
                                Text("Edit Note")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentNotes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "इस वीडियो में कोई महत्वपूर्ण बात, सीख या टाइमस्टैम्प नोट करें। यह हमेशा इसी वीडियो के साथ सेव रहेगा।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showNotesDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_video_note_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add Note / नोट जोड़ें")
                        }
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

    // Video Notes Dialog
    if (showNotesDialog) {
        VideoNotesDialog(
            initialNotes = currentNotes,
            videoTitle = video.title,
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
            }
        )
    }
}

/**
 * Dialog to attach or edit notes for any video.
 */
@Composable
fun VideoNotesDialog(
    initialNotes: String,
    videoTitle: String,
    onDismiss: () -> Unit,
    onSaveNotes: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Video Notes / वीडियो नोट्स", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = videoTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Write your notes, important takeaways, or thoughts here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 8
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            noteText = if (noteText.isBlank()) "📌 Takeaway: " else "$noteText\n📌 Takeaway: "
                        }
                    ) {
                        Text("+ Takeaway", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = {
                            noteText = if (noteText.isBlank()) "⏱ Note: " else "$noteText\n⏱ Note: "
                        }
                    ) {
                        Text("+ Marker", style = MaterialTheme.typography.labelSmall)
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
    onDismiss: () -> Unit,
    onSaveExisting: (String, Boolean) -> Unit,
    onCreateAndSave: (String, Boolean) -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(allPlaylists.isEmpty()) }
    var selectedPlaylistId by remember { mutableStateOf(allPlaylists.firstOrNull()?.playlistId ?: "") }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var playlistSearchQuery by remember { mutableStateOf("") }
    var syncWithYouTubeApp by remember { mutableStateOf(false) }

    val filteredPlaylists = remember(allPlaylists, playlistSearchQuery) {
        if (playlistSearchQuery.isBlank()) allPlaylists
        else allPlaylists.filter { it.title.contains(playlistSearchQuery, ignoreCase = true) }
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

                Spacer(modifier = Modifier.height(10.dp))

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
                    if (allPlaylists.size > 5) {
                        OutlinedTextField(
                            value = playlistSearchQuery,
                            onValueChange = { playlistSearchQuery = it },
                            placeholder = { Text("Search playlists...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    // Fully Scrollable Playlist List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (filteredPlaylists.isEmpty()) {
                            Text(
                                text = "No matching playlists found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        } else {
                            filteredPlaylists.forEach { pl ->
                                val isSelected = selectedPlaylistId == pl.playlistId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { selectedPlaylistId = pl.playlistId },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedPlaylistId = pl.playlistId }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = pl.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
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
                                text = "Open in YouTube App to sync ↗",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Save to your official YouTube account playlist",
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
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun ZeroSuggestionPlayer(
    videoId: String,
    replayTrigger: Int,
    onLoadingChanged: (Boolean) -> Unit,
    onVideoEnded: () -> Unit,
    onShowFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    class PlayerJsBridge {
        @JavascriptInterface
        fun reportEnded() {
            onVideoEnded()
        }
    }

    // Clean HTML with standard controls, playsinline, and GPU-accelerated CSS distraction hiding
    val htmlContent = remember(videoId) {
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
            }
            iframe {
              width: 100%;
              height: 100%;
              border: 0;
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
              src="https://www.youtube-nocookie.com/embed/$videoId?enablejsapi=1&autoplay=0&controls=1&rel=0&modestbranding=1&iv_load_policy=3&fs=1&playsinline=1"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen"
              allowfullscreen>
            </iframe>
          </div>
          <script>
            // PostMessage listener for YouTube iframe events without DOM manipulation
            window.addEventListener('message', function(event) {
              try {
                var data = (typeof event.data === 'string') ? JSON.parse(event.data) : event.data;
                if (data && data.event === 'infoDelivery' && data.info && data.info.playerState === 0) {
                  if (window.AndroidBridge && window.AndroidBridge.reportEnded) {
                    window.AndroidBridge.reportEnded();
                  }
                }
              } catch(e) {}
            });

            // Iframe API script loader via youtube-nocookie.com for Family Link compatibility
            var tag = document.createElement('script');
            tag.src = "https://www.youtube-nocookie.com/iframe_api";
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            var player;
            function onYouTubeIframeAPIReady() {
              try {
                player = new YT.Player('focus-yt-iframe', {
                  host: 'https://www.youtube-nocookie.com',
                  events: {
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

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    // CRITICAL FOR 2X SPEED: Must be false so ratechange and adaptive buffering never stall
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setSupportZoom(false)
                    // Modern clean mobile Chrome user agent so YouTube delivers hardware-accelerated streams
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                }

                addJavascriptInterface(PlayerJsBridge(), "AndroidBridge")

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
            if (replayTrigger > 0) {
                webView.loadUrl("javascript:try{if(player&&player.seekTo){player.seekTo(0);player.playVideo();}}catch(e){};")
            }
        }
    )
}

/**
 * Focus Shield Blocked Curtain:
 * Displayed when a user clicks on an entertainment, music, comedy, movie, or gaming video.
 * Immediately halts playback and purges the distraction.
 */
@Composable
fun DistractionBlockedCurtain(
    videoTitle: String,
    onBack: () -> Unit
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
                text = "This content matches music, movies, comedy, or distraction and has been blocked and removed. FocusTube is strictly dedicated to work, farming, manufacturing, business, and skill learning.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back to Work / वापस जाएं")
            }
        }
    }
}

