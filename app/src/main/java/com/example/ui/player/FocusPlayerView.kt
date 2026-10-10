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
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
    onFetchDescription: (suspend (String) -> String)? = null
) {
    var isPlayerLoading by remember(video.videoId) { mutableStateOf(true) }
    var isVideoEnded by remember(video.videoId) { mutableStateOf(false) }
    var replayTrigger by remember(video.videoId) { mutableStateOf(0) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var currentDescription by remember(video.id) { mutableStateOf(video.description) }
    var showSaveToPlaylistDialog by remember { mutableStateOf(false) }
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

    // 2. App-Level Fullscreen Mode (triggered by our dedicated "Full Screen" button)
    if (isAppFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
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

            // Floating Header Bar in Fullscreen
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { isAppFullscreen = false },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloseFullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exit Full Screen")
                    }
                }
            }

            // End-Screen Distraction Shield in Fullscreen
            if (isVideoEnded) {
                DistractionShieldOverlay(
                    onReplay = {
                        isVideoEnded = false
                        replayTrigger++
                    }
                )
            }
        }
        return
    }

    // 3. Normal Portrait Player Screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Distraction-Free Top Bar
        TopAppBar(
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
                // Fullscreen Action Button in Top Bar
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

        // 16:9 Isolated Video Screen (Family Link Compatible, Autoplay Disabled, Zero Suggestions)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
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
                    }
                )
            }
        }

        // Details, Actions & Description
        Column(
            modifier = Modifier
                .fillMaxSize()
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

                Text(
                    text = video.duration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row 1: Full Screen Mode button + Reload Player
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { isAppFullscreen = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("enter_fullscreen_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Full Screen")
                }

                OutlinedButton(
                    onClick = {
                        isPlayerLoading = true
                        replayTrigger++
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reload")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row 2: Mark Complete & Save to Playlist (Distraction-Free)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { onToggleCompleted(video) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_complete_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (video.isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (video.isCompleted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (video.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (video.isCompleted) "Completed" else "Mark Done",
                        maxLines = 1
                    )
                }

                // Save to Playlist Button (always available)
                OutlinedButton(
                    onClick = { showSaveToPlaylistDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_to_playlist_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save to Playlist")
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

    // Save Video to Playlist Dialog (Pick existing or create new playlist)
    if (showSaveToPlaylistDialog) {
        SaveVideoToPlaylistDialog(
            video = video,
            allPlaylists = allPlaylists,
            onDismiss = { showSaveToPlaylistDialog = false },
            onSaveExisting = { targetPlaylistId ->
                onSaveToPlaylist?.invoke(video, targetPlaylistId)
                showSaveToPlaylistDialog = false
            },
            onCreateAndSave = { newPlaylistTitle ->
                if (onCreatePlaylistAndSaveVideo != null) {
                    onCreatePlaylistAndSaveVideo(newPlaylistTitle, video)
                } else if (onSaveToPlaylist != null) {
                    onSaveToPlaylist(video, newPlaylistTitle)
                }
                showSaveToPlaylistDialog = false
            }
        )
    }
}

/**
 * Distraction Shield shown when the video finishes:
 * Completely blocks YouTube's 12-grid recommendations.
 */
@Composable
private fun DistractionShieldOverlay(
    onReplay: () -> Unit
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

/**
 * Dialog to save video to an existing playlist OR create a new playlist on the fly.
 */
@Composable
fun SaveVideoToPlaylistDialog(
    video: VideoEntity,
    allPlaylists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onSaveExisting: (String) -> Unit,
    onCreateAndSave: (String) -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(allPlaylists.isEmpty()) }
    var selectedPlaylistId by remember { mutableStateOf(allPlaylists.firstOrNull()?.playlistId ?: "") }
    var newPlaylistTitle by remember { mutableStateOf("") }

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                            Text("Existing")
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

                    Spacer(modifier = Modifier.height(12.dp))
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
                    Column {
                        allPlaylists.forEach { pl ->
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
                                        .padding(10.dp),
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
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                    if (isCreatingNew || allPlaylists.isEmpty()) {
                        if (newPlaylistTitle.isNotBlank()) {
                            onCreateAndSave(newPlaylistTitle.trim())
                        }
                    } else {
                        if (selectedPlaylistId.isNotBlank()) {
                            onSaveExisting(selectedPlaylistId)
                        }
                    }
                },
                enabled = if (isCreatingNew || allPlaylists.isEmpty()) newPlaylistTitle.isNotBlank() else selectedPlaylistId.isNotBlank()
            ) {
                Text(if (isCreatingNew || allPlaylists.isEmpty()) "Create & Save" else "Save")
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

    // HTML with STRICT AUTOPLAY DISABLED (autoplay=0), controls enabled, fullscreen enabled
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
            /* STRICT DISTRACTION REMOVAL STYLES */
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
              src="https://www.youtube-nocookie.com/embed/$videoId?enablejsapi=1&autoplay=0&auto_play=0&controls=1&rel=0&modestbranding=1&iv_load_policy=3&fs=1&playsinline=1&showsearch=0"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen"
              allowfullscreen>
            </iframe>
          </div>
          <script>
            // PostMessage listener for YouTube iframe events
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

            // Iframe API script loader
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

            // Suppress overlays and recommendation cards aggressively
            function purgeDistractions() {
              try {
                var selectors = [
                  '.ytp-pause-overlay', '.ytp-pause-overlay-container',
                  '.ytp-endscreen-content', '.ytp-endscreen-paginate', '.videowall-endscreen',
                  '.ytp-ce-element', '.ytp-ce-covering-overlay', '.ytp-ce-expanding-overlay',
                  '.ytp-cards-teaser', '.ytp-cards-button', '.ytp-cards-container',
                  '.ytp-suggestion-set', '.ytp-upnext', '.ytp-next-button', '.ytp-prev-button'
                ];
                selectors.forEach(function(sel) {
                  var nodes = document.querySelectorAll(sel);
                  for (var i = 0; i < nodes.length; i++) {
                    nodes[i].remove();
                  }
                });
              } catch(e) {}
            }
            setInterval(purgeDistractions, 250);
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

                setBackgroundColor(android.graphics.Color.BLACK)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    // CRITICAL: Strictly forbid auto-playing without user gesture:
                    mediaPlaybackRequiresUserGesture = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setSupportZoom(false)
                    // Custom clean user agent to avoid Chrome child profile supervision interception:
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 FocusPlayer/2.0"
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

                    // CRITICAL FAMILY LINK & IFRAME POLICY:
                    // Never block sub-frame, video manifest, or media resource loading.
                    // Keep all playback inside this WebView, do not redirect to blocked Chrome browser!
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (request?.isForMainFrame == false) {
                            return false // DO NOT BLOCK VIDEO RESOURCES OR SUBFRAMES!
                        }
                        if (url.contains("youtube-nocookie.com") || url.contains("googlevideo.com") || url.contains("ytimg.com") || url.contains("youtube.com/embed")) {
                            return false
                        }
                        // Block any outside intents or redirects to browser:
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

                // Base URL set to youtube-nocookie.com so Family Link does not trigger the youtube.com block
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
