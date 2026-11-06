package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.PlaylistEntity
import com.example.data.repository.FocusTubeRepository
import com.example.ui.add.AddPlaylistDialog
import com.example.ui.add.AddVideoGenericDialog
import com.example.ui.backup.BackupRestoreDialog
import com.example.ui.components.GoogleAccountDialog
import com.example.ui.groups.GroupsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.player.FocusPlayerView
import com.example.ui.player.SaveVideoToPlaylistDialog
import com.example.ui.playlists.PlaylistDetailScreen
import com.example.ui.playlists.PlaylistsScreen
import com.example.ui.viewmodel.FocusTubeViewModel

enum class MainTab {
    HOME,
    GROUPS,
    PLAYLISTS
}

sealed class VideoAddDestination {
    object Home : VideoAddDestination()
    data class Group(val groupId: Long) : VideoAddDestination()
    data class Playlist(val playlistId: String) : VideoAddDestination()
}

@Composable
fun MainApp(
    viewModel: FocusTubeViewModel = viewModel(),
    initialSharedUrl: String? = null,
    onClearSharedUrl: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var viewingPlaylistDetail by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showGoogleAccountDialog by remember { mutableStateOf(false) }
    var addVideoDestination by remember { mutableStateOf<VideoAddDestination?>(null) }
    var videoToSaveToPlaylist by remember { mutableStateOf<com.example.data.local.entity.VideoEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Observables
    val homeTitle by viewModel.homeTitle.collectAsState()
    val pinnedGroup by viewModel.pinnedGroup.collectAsState()
    val pinnedPlaylist by viewModel.pinnedPlaylist.collectAsState()
    val allGroups by viewModel.allGroups.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val homeVideos by viewModel.homeVideos.collectAsState()
    val allSavedVideos by viewModel.allSavedVideos.collectAsState()
    val currentPlayingVideo by viewModel.currentPlayingVideo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val channelSearchHistory by viewModel.getSearchHistory("CHANNEL").collectAsState(initial = emptyList())
    val playlistSearchHistory by viewModel.getSearchHistory("PLAYLIST").collectAsState(initial = emptyList())
    val lastPlayedVideoId by viewModel.lastPlayedVideoId.collectAsState()
    val googleUser by viewModel.googleUserProfile.collectAsState()

    // Handle shared YouTube links directly from Android Share Sheet
    LaunchedEffect(initialSharedUrl) {
        val shared = initialSharedUrl?.trim()
        if (!shared.isNullOrBlank()) {
            if (shared.contains("list=") || shared.startsWith("PL")) {
                viewModel.addPlaylist(shared)
            } else {
                val extractedId = FocusTubeRepository.extractVideoId(shared) ?: shared
                val dummyVideo = com.example.data.local.entity.VideoEntity(
                    videoId = extractedId,
                    title = "Shared Video",
                    channelName = "YouTube",
                    duration = "",
                    thumbnailUrl = "https://img.youtube.com/vi/$extractedId/hqdefault.jpg",
                    uploadDate = "Shared"
                )
                videoToSaveToPlaylist = dummyVideo
            }
            onClearSharedUrl()
        }
    }

    // Persistent scroll states across video playback and tab navigation
    val homeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val groupsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    // Show status snackbars when messages arrive
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Back button handling
    BackHandler(enabled = currentPlayingVideo != null || viewingPlaylistDetail != null || selectedTab != MainTab.HOME) {
        when {
            currentPlayingVideo != null -> viewModel.closePlayer()
            viewingPlaylistDetail != null -> viewingPlaylistDetail = null
            selectedTab != MainTab.HOME -> selectedTab = MainTab.HOME
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Base Layer: Main Scaffold with navigation bar and main tabs
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (viewingPlaylistDetail == null && currentPlayingVideo == null) {
                    NavigationBar(modifier = Modifier.testTag("main_bottom_nav")) {
                        NavigationBarItem(
                            selected = selectedTab == MainTab.HOME,
                            onClick = { selectedTab = MainTab.HOME },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == MainTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_home_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == MainTab.GROUPS,
                            onClick = { selectedTab = MainTab.GROUPS },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == MainTab.GROUPS) Icons.Filled.Folder else Icons.Outlined.Folder,
                                    contentDescription = "Groups"
                                )
                            },
                            label = { Text("Groups") },
                            modifier = Modifier.testTag("nav_groups_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == MainTab.PLAYLISTS,
                            onClick = { selectedTab = MainTab.PLAYLISTS },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == MainTab.PLAYLISTS) Icons.Filled.PlaylistPlay else Icons.Outlined.PlaylistPlay,
                                    contentDescription = "Playlists"
                                )
                            },
                            label = { Text("Playlists") },
                            modifier = Modifier.testTag("nav_playlists_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (viewingPlaylistDetail == null && currentPlayingVideo == null) innerPadding else PaddingValues(0.dp))
            ) {
                when (selectedTab) {
                    MainTab.HOME -> {
                        HomeScreen(
                            homeTitle = homeTitle,
                            pinnedGroup = pinnedGroup,
                            pinnedPlaylist = pinnedPlaylist,
                            allGroups = allGroups,
                            allPlaylists = allPlaylists,
                            videos = homeVideos,
                            allSavedVideos = allSavedVideos,
                            listState = homeListState,
                            lastPlayedVideoId = lastPlayedVideoId,
                            onSelectPinnedGroup = { viewModel.setPinnedGroup(it) },
                            onSelectPinnedPlaylist = { viewModel.setPinnedPlaylist(it) },
                            onSelectVideo = { viewModel.playVideo(it) },
                            onToggleCompleted = { viewModel.toggleCompleted(it) },
                            onSaveToPlaylist = { videoToSaveToPlaylist = it },
                            onOpenAddVideoDialog = {
                                addVideoDestination = VideoAddDestination.Home
                            },
                            isSyncing = isSyncing,
                            onSyncGroup = { viewModel.syncGroup(it) },
                            onSyncPlaylist = { viewModel.syncPlaylist(it) },
                            userProfile = googleUser,
                            onOpenGoogleAccount = { showGoogleAccountDialog = true }
                        )
                    }

                    MainTab.GROUPS -> {
                        GroupsScreen(
                            groups = allGroups,
                            pinnedGroup = pinnedGroup,
                            searchHistory = channelSearchHistory,
                            listState = groupsListState,
                            getChannelsForGroup = { viewModel.getChannelsForGroup(it) },
                            onSelectPinnedGroup = { viewModel.setPinnedGroup(it) },
                            onCreateGroup = { viewModel.createGroup(it) },
                            onDeleteGroup = { viewModel.deleteGroup(it) },
                            onAddChannel = { rawInput, gId -> viewModel.addChannelToGroup(rawInput, gId) },
                            onDeleteChannel = { viewModel.deleteChannel(it) },
                            onMoveChannel = { cId, gId -> viewModel.moveChannelToGroup(cId, gId) },
                            onOpenAddVideoToGroup = { gId ->
                                addVideoDestination = VideoAddDestination.Group(gId)
                            },
                            onDeleteSearchHistoryItem = { query ->
                                viewModel.deleteSearchHistoryItem(query, "CHANNEL")
                            },
                            onClearSearchHistory = {
                                viewModel.clearSearchHistory("CHANNEL")
                            },
                            onOpenBackupDialog = { showBackupDialog = true },
                            onSyncGroup = { viewModel.syncGroup(it) }
                        )
                    }

                    MainTab.PLAYLISTS -> {
                        PlaylistsScreen(
                            playlists = allPlaylists,
                            pinnedPlaylist = pinnedPlaylist,
                            onSelectPinnedPlaylist = { viewModel.setPinnedPlaylist(it) },
                            onOpenPlaylistVideos = { viewingPlaylistDetail = it },
                            onOpenAddVideoToPlaylist = { plId ->
                                addVideoDestination = VideoAddDestination.Playlist(plId)
                            },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            onOpenCreatePlaylistDialog = { showAddPlaylistDialog = true },
                            onOpenBackupDialog = { showBackupDialog = true },
                            onSyncPlaylist = { viewModel.syncPlaylist(it) },
                            userProfile = googleUser,
                            onOpenGoogleAccount = { showGoogleAccountDialog = true }
                        )
                    }
                }

                // Real Fetching Loading Spinner Overlay (Only for initial manual fetches, not background sync)
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Fetching videos from YouTube...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Playlist Details view: Stays composed beneath the video player overlay
        viewingPlaylistDetail?.let { playlist ->
            val playlistVideos by viewModel.getVideosForPlaylist(playlist.playlistId).collectAsState(initial = emptyList())
            val playlistListState = rememberSaveable(playlist.playlistId, saver = LazyListState.Saver) {
                LazyListState()
            }
            PlaylistDetailScreen(
                playlist = playlist,
                videos = playlistVideos,
                listState = playlistListState,
                lastPlayedVideoId = lastPlayedVideoId,
                onBack = { viewingPlaylistDetail = null },
                onPinToHome = { viewModel.setPinnedPlaylist(it) },
                onSelectVideo = { viewModel.playVideo(it) },
                onToggleCompleted = { viewModel.toggleCompleted(it) },
                onDeleteVideo = { viewModel.deleteVideo(it) },
                onOpenAddVideoDialog = {
                    addVideoDestination = VideoAddDestination.Playlist(playlist.playlistId)
                },
                isSyncing = isSyncing,
                onSyncPlaylist = { viewModel.syncPlaylist(it) },
                onMarkVideoSynced = { vId, synced -> viewModel.markVideoSynced(vId, synced) },
                onMarkAllSynced = { plId -> viewModel.markAllVideosInPlaylistSynced(plId) },
                onSyncBatchToYouTube = { videoIds -> viewModel.syncBatchToYouTube(context, playlist.playlistId, videoIds) },
                userProfile = googleUser,
                onOpenGoogleAccount = { showGoogleAccountDialog = true },
                onSyncAllNowWithAccount = { viewModel.syncAllNowWithAccount() }
            )
        }

        // Fullscreen Clean Player (100% Distraction-Proof, Zero Suggestions)
        // Rendered on top so that closing it reveals the previous scroll position instantly
        currentPlayingVideo?.let { activeVideo ->
            FocusPlayerView(
                video = activeVideo,
                headerTitle = activeVideo.title,
                allPlaylists = allPlaylists,
                onBack = { viewModel.closePlayer() },
                onToggleCompleted = { viewModel.toggleCompleted(it) },
                onSaveToPlaylist = { v, plId -> viewModel.saveVideoToPlaylist(v, plId) },
                onCreatePlaylistAndSaveVideo = { title, v -> viewModel.createPlaylistAndSaveVideo(title, v) },
                onFetchDescription = { vId -> viewModel.fetchVideoDescription(vId) },
                onUpdateNotes = { vId, notes -> viewModel.updateVideoNotes(vId, notes) },
                onDeleteVideo = { vId ->
                    viewModel.deleteVideo(vId)
                    viewModel.closePlayer()
                }
            )
        }
    }

    // Dialog: Add Playlist with live URL resolution
    if (showAddPlaylistDialog) {
        AddPlaylistDialog(
            searchHistory = playlistSearchHistory,
            onDeleteSearchHistoryItem = { query ->
                viewModel.deleteSearchHistoryItem(query, "PLAYLIST")
            },
            onClearSearchHistory = {
                viewModel.clearSearchHistory("PLAYLIST")
            },
            onDismiss = { showAddPlaylistDialog = false },
            onConfirm = { urlOrId, title ->
                viewModel.addPlaylist(urlOrId, title)
                showAddPlaylistDialog = false
            }
        )
    }

    // Dialog: Backup and Restore (Export & Import Data)
    if (showBackupDialog) {
        BackupRestoreDialog(
            onDismiss = { showBackupDialog = false },
            onExportData = { viewModel.exportDataJson() },
            onImportData = { json, replace, callback ->
                viewModel.importDataJson(json, replace, callback)
            }
        )
    }

    // Dialog: Google Account & Nightly Sync / Backup
    if (showGoogleAccountDialog) {
        GoogleAccountDialog(
            userProfile = googleUser,
            isSyncing = isSyncing,
            onDismiss = { showGoogleAccountDialog = false },
            onSignInWithGoogle = {
                viewModel.signInWithGoogle(context)
            },
            onManualConnect = { email, name ->
                viewModel.saveManualGoogleLogin(email, name)
            },
            onSignOut = {
                viewModel.signOutGoogle()
            },
            onToggleNightlySync = { enabled ->
                viewModel.toggleNightlySync(enabled)
            },
            onSyncAllNow = {
                viewModel.syncAllNowWithAccount()
            }
        )
    }

    // Dialog: Add Video to specific destination
    addVideoDestination?.let { dest ->
        val prefix = when (dest) {
            is VideoAddDestination.Home -> "Home"
            is VideoAddDestination.Group -> "Group"
            is VideoAddDestination.Playlist -> "Playlist"
        }

        AddVideoGenericDialog(
            titlePrefix = prefix,
            onDismiss = { addVideoDestination = null },
            onConfirm = { videoUrl, title ->
                when (dest) {
                    is VideoAddDestination.Home -> {
                        val g = pinnedGroup
                        val pl = pinnedPlaylist
                        if (g != null) {
                            viewModel.addVideoToGroup(g.id, title, g.name, videoUrl)
                        } else if (pl != null) {
                            viewModel.addVideoToPlaylist(pl.playlistId, title, pl.title, videoUrl)
                        }
                    }
                    is VideoAddDestination.Group -> {
                        viewModel.addVideoToGroup(dest.groupId, title, "Group Video", videoUrl)
                    }
                    is VideoAddDestination.Playlist -> {
                        viewModel.addVideoToPlaylist(dest.playlistId, title, "Playlist Video", videoUrl)
                    }
                }
                addVideoDestination = null
            }
        )
    }

    // Save Video to Playlist dialog from feeds
    if (videoToSaveToPlaylist != null) {
        val vid = videoToSaveToPlaylist!!
        val context = LocalContext.current
        SaveVideoToPlaylistDialog(
            video = vid,
            allPlaylists = allPlaylists,
            onDismiss = { videoToSaveToPlaylist = null },
            onSaveExisting = { targetPlId, syncYouTube ->
                viewModel.saveVideoToPlaylist(vid, targetPlId, andOpenYouTube = syncYouTube, context = context)
                videoToSaveToPlaylist = null
            },
            onCreateAndSave = { newTitle, syncYouTube ->
                viewModel.createPlaylistAndSaveVideo(newTitle, vid, andOpenYouTube = syncYouTube, context = context)
                videoToSaveToPlaylist = null
            }
        )
    }
}
