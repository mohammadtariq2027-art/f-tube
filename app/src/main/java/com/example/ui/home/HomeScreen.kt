package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeTitle: String,
    pinnedGroup: SubscriptionGroupEntity?,
    pinnedPlaylist: PlaylistEntity?,
    allGroups: List<SubscriptionGroupEntity>,
    allPlaylists: List<PlaylistEntity>,
    videos: List<VideoEntity>,
    allSavedVideos: List<VideoEntity> = emptyList(),
    onSelectPinnedGroup: (Long) -> Unit,
    onSelectPinnedPlaylist: (String) -> Unit,
    onSelectVideo: (VideoEntity) -> Unit,
    onSelectVideoAtTimestamp: (VideoEntity, Int?) -> Unit = { v, _ -> onSelectVideo(v) },
    onToggleCompleted: (VideoEntity) -> Unit,
    onSaveToPlaylist: ((VideoEntity) -> Unit)? = null,
    onDropVideoToPlaylist: (VideoEntity, String) -> Unit = { _, _ -> },
    onOpenAddVideoDialog: () -> Unit,
    isSyncing: Boolean = false,
    onSyncGroup: (Long) -> Unit = {},
    onSyncPlaylist: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    lastPlayedVideoId: String? = null,
    userProfile: com.example.util.GoogleUserProfile = com.example.util.GoogleUserProfile(),
    onOpenGoogleAccount: () -> Unit = {},
    onOpenBackupDialog: () -> Unit = {},
    onSyncAllNowWithAccount: () -> Unit = {},
    onOpenReminderScreen: (() -> Unit)? = null,
    onOpenDashboardTab: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var showSwitchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchScopeGlobal by remember { mutableStateOf(true) }
    // Search target mode when searching: "BOTH" (Videos + Notes), "VIDEOS_ONLY", "NOTES_ONLY"
    var searchTargetMode by remember { mutableStateOf("BOTH") }
    // Video status filter: "ALL", "SAVED_IN_PLAYLIST", "UNSAVED", "WITH_NOTES"
    var videoStatusFilter by remember { mutableStateOf("ALL") }

    // Drag-and-Drop State for Home Videos -> Playlists
    var activeDraggedHomeVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var dragPointerInRoot by remember { mutableStateOf(Offset.Zero) }
    var rootBoxOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val homePlaylistDropBounds = remember { mutableStateMapOf<String, Rect>() }
    val hoveredHomePlaylistId: String? = remember(
        activeDraggedHomeVideo,
        dragPointerInRoot,
        homePlaylistDropBounds.toMap()
    ) {
        if (activeDraggedHomeVideo == null) null
        else homePlaylistDropBounds.entries.firstOrNull { (_, rect) -> rect.contains(dragPointerInRoot) }?.key
    }

    fun finishHomeVideoDrop() {
        val vid = activeDraggedHomeVideo
        val targetPlId = hoveredHomePlaylistId
        if (vid != null && targetPlId != null) {
            onDropVideoToPlaylist(vid, targetPlId)
        }
        activeDraggedHomeVideo = null
    }

    // Fallback: If for any reason the scroll state was reset to index 0 after watching a video,
    // automatically restore scroll position to the video the user was watching.
    LaunchedEffect(lastPlayedVideoId) {
        if (lastPlayedVideoId != null) {
            val targetIndex = videos.indexOfFirst { it.videoId == lastPlayedVideoId }
            if (targetIndex > 2 && listState.firstVisibleItemIndex == 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // Fast lookup maps: Which playlist(s) each videoId is saved in, and what Note/Comment is attached
    val playlistMapById = remember(allPlaylists) {
        allPlaylists.associateBy { it.playlistId }
    }
    val videoToPlaylistsMap = remember(allSavedVideos, playlistMapById) {
        val map = HashMap<String, MutableList<PlaylistEntity>>()
        for (v in allSavedVideos) {
            val plId = v.playlistId
            if (!plId.isNullOrBlank()) {
                val pl = playlistMapById[plId]
                if (pl != null) {
                    val list = map.getOrPut(v.videoId) { mutableListOf() }
                    if (list.none { it.playlistId == pl.playlistId }) {
                        list.add(pl)
                    }
                }
            }
        }
        map
    }
    val videoToNotesMap = remember(allSavedVideos, videos) {
        val map = HashMap<String, String>()
        for (v in allSavedVideos) {
            if (v.notes.isNotBlank()) {
                map[v.videoId] = v.notes.trim()
            }
        }
        for (v in videos) {
            if (v.notes.isNotBlank()) {
                map[v.videoId] = v.notes.trim()
            }
        }
        map
    }

    val safeFeedVideos = videos
    // Deduplicate global videos by videoId so each video appears once with all its saved playlists & notes shown
    val safeGlobalVideos = remember(allSavedVideos, videos) {
        (allSavedVideos + videos).distinctBy { it.videoId }
    }

    val videoToPlaylistNamesMap = remember(videoToPlaylistsMap) {
        videoToPlaylistsMap.mapValues { (_, list) -> list.joinToString(" ") { it.title } }
    }

    val activeSearchMode = if (isSearchActive) searchTargetMode else "BOTH"

    val feedAiResults = remember(safeFeedVideos, searchQuery, activeSearchMode, videoToPlaylistNamesMap, videoToNotesMap) {
        com.example.util.AiSmartSearchEngine.searchVideosAndNotes(
            videos = safeFeedVideos,
            rawQuery = if (isSearchActive) searchQuery else "",
            searchTargetMode = activeSearchMode,
            videoToPlaylistsNames = videoToPlaylistNamesMap,
            videoToNotesMap = videoToNotesMap
        )
    }

    val globalAiResults = remember(safeGlobalVideos, searchQuery, activeSearchMode, videoToPlaylistNamesMap, videoToNotesMap) {
        com.example.util.AiSmartSearchEngine.searchVideosAndNotes(
            videos = safeGlobalVideos,
            rawQuery = if (isSearchActive) searchQuery else "",
            searchTargetMode = activeSearchMode,
            videoToPlaylistsNames = videoToPlaylistNamesMap,
            videoToNotesMap = videoToNotesMap
        )
    }

    val filteredFeedVideos = remember(feedAiResults) { feedAiResults.map { it.video } }
    val filteredGlobalVideos = remember(globalAiResults) { globalAiResults.map { it.video } }

    val aiMatchByVideoId = remember(isSearchActive, searchScopeGlobal, feedAiResults, globalAiResults) {
        val activeList = if (isSearchActive && searchScopeGlobal) globalAiResults else feedAiResults
        activeList.associateBy { it.video.videoId }
    }

    val baseVideos = if (isSearchActive && searchScopeGlobal) filteredGlobalVideos else filteredFeedVideos

    val savedInPlaylistCount = remember(baseVideos, videoToPlaylistsMap) {
        baseVideos.count { !videoToPlaylistsMap[it.videoId].isNullOrEmpty() }
    }
    val unsavedInPlaylistCount = remember(baseVideos, videoToPlaylistsMap) {
        baseVideos.count { videoToPlaylistsMap[it.videoId].isNullOrEmpty() }
    }
    val withNotesCount = remember(baseVideos, videoToNotesMap) {
        baseVideos.count { !videoToNotesMap[it.videoId].isNullOrBlank() || it.notes.isNotBlank() }
    }

    val displayedVideos = remember(baseVideos, videoStatusFilter, videoToPlaylistsMap, videoToNotesMap) {
        when (videoStatusFilter) {
            "SAVED_IN_PLAYLIST" -> baseVideos.filter { !videoToPlaylistsMap[it.videoId].isNullOrEmpty() }
            "UNSAVED" -> baseVideos.filter { videoToPlaylistsMap[it.videoId].isNullOrEmpty() }
            "WITH_NOTES" -> baseVideos.filter { !videoToNotesMap[it.videoId].isNullOrBlank() || it.notes.isNotBlank() }
            else -> baseVideos
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBoxOriginInRoot = it.positionInRoot() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Home Top App Bar with AI Smart Global & Local Feed Search (Zero duplicate top inset!)
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "🧠 AI Smart Search: type idea, topic, or time (e.g. 6 min)...",
                                    fontSize = 11.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_home_search_input"),
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    } else {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = homeTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF2E7D32)
                                ) {
                                    Text(
                                        text = "🛡️ SHIELD",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (pinnedGroup != null) "Active Group (${videos.size} videos)"
                                else if (pinnedPlaylist != null) "Active Playlist (${videos.size} videos)"
                                else "No active feed chosen",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close search"
                            )
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag("home_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search videos",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val canSync = pinnedGroup != null || pinnedPlaylist != null || allGroups.isNotEmpty()
                    if (canSync) {
                        IconButton(
                            onClick = {
                                when {
                                    pinnedGroup != null -> onSyncGroup(pinnedGroup.id)
                                    pinnedPlaylist != null -> onSyncPlaylist(pinnedPlaylist.playlistId)
                                    allGroups.isNotEmpty() -> onSyncGroup(allGroups.first().id)
                                }
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.testTag("home_sync_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync all videos from YouTube",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showSwitchSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("switch_home_target_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Switch",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = onOpenGoogleAccount,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("home_google_account_button")
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = if (userProfile.isSignedIn) Icons.Default.AccountCircle else Icons.Default.CloudSync,
                                contentDescription = "Google Account & Nightly Sync",
                                tint = if (userProfile.isSignedIn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (userProfile.isSignedIn) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(Color(0xFF4CAF50), shape = CircleShape)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Persistent App Header Sync & Backup Health Indicator Bar
            com.example.ui.components.AppHeaderSyncStatusBar(
                userProfile = userProfile,
                isSyncing = isSyncing,
                allSavedVideos = allSavedVideos,
                groupsCount = allGroups.size,
                playlistsCount = allPlaylists.size,
                onTriggerSyncAndBackupNow = onSyncAllNowWithAccount,
                onOpenGoogleAccountDialog = onOpenGoogleAccount,
                onOpenBackupRestoreDialog = onOpenBackupDialog,
                onOpenReminderScreen = onOpenReminderScreen,
                onOpenDashboardTab = onOpenDashboardTab
            )

            // Search Scope & Target Selector (Videos + Notes / Videos Only / Notes Only + Global vs Active Feed)
            if (isSearchActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🧠 AI Smart Search Active: Type any topic, keyword, or approximate timestamp (e.g. ~6 min) to find videos & scene notes:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = searchTargetMode == "BOTH",
                                onClick = { searchTargetMode = "BOTH" },
                                label = { Text("🔍 Both: Videos + Notes", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = searchTargetMode == "NOTES_ONLY",
                                onClick = { searchTargetMode = "NOTES_ONLY" },
                                label = { Text("📝 Notes / Timestamps Only", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = searchTargetMode == "VIDEOS_ONLY",
                                onClick = { searchTargetMode = "VIDEOS_ONLY" },
                                label = { Text("🎬 Videos Only", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = searchScopeGlobal,
                                onClick = { searchScopeGlobal = true },
                                label = { Text("🌐 All (${filteredGlobalVideos.size})", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = !searchScopeGlobal,
                                onClick = { searchScopeGlobal = false },
                                label = { Text("📌 Home Feed (${filteredFeedVideos.size})", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Video Playlist & Notes Status Filter Bar (Compact single row)
            if (baseVideos.isNotEmpty() || videoStatusFilter != "ALL") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = videoStatusFilter == "ALL",
                            onClick = { videoStatusFilter = "ALL" },
                            label = { Text("All (${baseVideos.size})", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = videoStatusFilter == "SAVED_IN_PLAYLIST",
                            onClick = {
                                videoStatusFilter = if (videoStatusFilter == "SAVED_IN_PLAYLIST") "ALL" else "SAVED_IN_PLAYLIST"
                            },
                            label = { Text("✅ In Playlist ($savedInPlaylistCount)", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = videoStatusFilter == "UNSAVED",
                            onClick = {
                                videoStatusFilter = if (videoStatusFilter == "UNSAVED") "ALL" else "UNSAVED"
                            },
                            label = { Text("⚪ Unsaved ($unsavedInPlaylistCount)", fontSize = 11.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = videoStatusFilter == "WITH_NOTES",
                            onClick = {
                                videoStatusFilter = if (videoStatusFilter == "WITH_NOTES") "ALL" else "WITH_NOTES"
                            },
                            label = { Text("📝 With Notes ($withNotesCount)", fontSize = 11.sp) }
                        )
                    }
                }
            }

            // === DRAG & DROP PLAYLIST TARGET DOCK ON HOME (Only shown when actively dragging a video so Home has maximum space!) ===
            if (activeDraggedHomeVideo != null && allPlaylists.isNotEmpty()) {
                val isDragging = true
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_playlist_drop_dock")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = if (isDragging) 8.dp else 3.dp)
                    ) {
                        Text(
                            text = if (isDragging) {
                                "📦 Drop '${activeDraggedHomeVideo?.title}' onto any playlist below to save/move:"
                            } else {
                                "🖐️ Drag & Drop: Hold a video's '⠿' handle and drop onto any playlist:"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDragging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allPlaylists, key = { "home_drop_pl_${it.playlistId}" }) { targetPl ->
                                val isHovered = hoveredHomePlaylistId == targetPl.playlistId
                                val plTitle = if (targetPl.title.isBlank() || targetPl.title.equals("undefined", ignoreCase = true)) "My Playlist" else targetPl.title
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isHovered) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            homePlaylistDropBounds[targetPl.playlistId] = coords.boundsInRoot()
                                        }
                                        .border(
                                            width = if (isHovered) 2.dp else 1.dp,
                                            color = if (isHovered) Color.White else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .testTag("home_drop_target_playlist_${targetPl.playlistId}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(
                                            horizontal = if (isDragging) 12.dp else 9.dp,
                                            vertical = if (isDragging) 7.dp else 4.dp
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlaylistPlay,
                                            contentDescription = null,
                                            tint = if (isHovered) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(if (isDragging) 16.dp else 13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isHovered) "📥 Drop in $plTitle" else plTitle,
                                            fontSize = if (isDragging) 12.sp else 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHovered) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Video List
            if (displayedVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isSearchActive) "No Matching Videos" else "No Videos in This Feed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isSearchActive) "No video matched '$searchQuery'."
                            else "Add YouTube videos using the '+' button, or tap 'Switch' to select another Group or Playlist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (isSearchActive) {
                            Button(onClick = { searchQuery = "" }) {
                                Text("Clear Search")
                            }
                        } else {
                            Button(onClick = { showSwitchSheet = true }) {
                                Text("Select Home Group or Playlist")
                            }
                        }
                    }
                }
            } else {
                val context = LocalContext.current
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 98.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = displayedVideos,
                        key = { "${it.id}_${it.videoId}" },
                        contentType = { "video_card" }
                    ) { video ->
                        val cleanDuration = remember(video.duration, video.videoId) {
                            com.example.util.DurationUtils.formatCleanDuration(video.duration, video.videoId)
                        }
                        val savedPlaylists = videoToPlaylistsMap[video.videoId].orEmpty()
                        val linkedNote = videoToNotesMap[video.videoId] ?: video.notes.trim()
                        val hasNote = linkedNote.isNotBlank()
                        val isSavedInPlaylist = savedPlaylists.isNotEmpty()
                        val isBeingDragged = activeDraggedHomeVideo?.id == video.id
                        var cardPosInRoot by remember { mutableStateOf(Offset.Zero) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { cardPosInRoot = it.positionInRoot() }
                                .pointerInput(video.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeDraggedHomeVideo = video
                                            dragPointerInRoot = cardPosInRoot + localOffset
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragPointerInRoot += dragAmount
                                        },
                                        onDragEnd = { finishHomeVideoDrop() },
                                        onDragCancel = { activeDraggedHomeVideo = null }
                                    )
                                }
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectVideo(video)
                                }
                                .testTag("home_video_${video.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isBeingDragged -> MaterialTheme.colorScheme.primaryContainer
                                    video.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Mobile-Optimized 16:9 Thumbnail (132dp width gives plenty of breathing room to title & action pills on Realme P4)
                                Box(
                                    modifier = Modifier
                                        .width(132.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = remember(video.thumbnailUrl) {
                                            ImageRequest.Builder(context)
                                                .data(video.thumbnailUrl)
                                                .size(320, 180)
                                                .crossfade(120)
                                                .allowHardware(true)
                                                .build()
                                        },
                                        contentDescription = video.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color.White,
                                                modifier = Modifier.padding(3.dp)
                                            )
                                        }
                                    }

                                    // Instant-Context Thumbnail Overlay Icons (Playlist & Notes indicators)
                                    if (isSavedInPlaylist || hasNote) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isSavedInPlaylist) {
                                                Surface(
                                                    shape = RoundedCornerShape(5.dp),
                                                    color = Color(0xFF1B5E20).copy(alpha = 0.9f),
                                                    modifier = Modifier.testTag("thumb_playlist_icon_${video.id}")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlaylistPlay,
                                                            contentDescription = "Saved in playlist",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = "${savedPlaylists.size}",
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            if (hasNote) {
                                                val firstTs = remember(linkedNote) {
                                                    com.example.ui.player.parseSceneNotes(linkedNote)
                                                        .firstOrNull { it.timestampSeconds != null }
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(5.dp),
                                                    color = Color(0xFFE65100).copy(alpha = 0.94f),
                                                    modifier = Modifier
                                                        .clickable {
                                                            onSelectVideoAtTimestamp(video, firstTs?.timestampSeconds ?: 0)
                                                        }
                                                        .testTag("thumb_note_icon_${video.id}")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Has user notes (tap to jump to note timestamp)",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        if (firstTs?.timestampLabel != null) {
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = firstTs.timestampLabel,
                                                                color = Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Video Duration Badge
                                    if (cleanDuration.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Black.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(3.dp)
                                        ) {
                                            Text(
                                                text = cleanDuration,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Video Title and Channel Name + Upload Date Metadata
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = video.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontSize = 13.5.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (hasNote) {
                                            val firstNoteEntry = remember(linkedNote) {
                                                com.example.ui.player.parseSceneNotes(linkedNote)
                                                    .firstOrNull { it.timestampSeconds != null }
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFFFF3E0),
                                                modifier = Modifier
                                                    .border(
                                                        width = 0.8.dp,
                                                        color = Color(0xFFE65100).copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSelectVideoAtTimestamp(video, firstNoteEntry?.timestampSeconds ?: 0)
                                                    }
                                                    .testTag("video_pencil_note_badge_${video.id}")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Written Note Indicator",
                                                        tint = Color(0xFFE65100),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    if (firstNoteEntry?.timestampLabel != null) {
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = firstNoteEntry.timestampLabel,
                                                            color = Color(0xFFE65100),
                                                            fontSize = 9.sp,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Compact Drag Handle integrated cleanly in top-right of title row
                                        var dragHandlePos by remember { mutableStateOf(Offset.Zero) }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .onGloballyPositioned { dragHandlePos = it.positionInRoot() }
                                                .pointerInput(video.id) {
                                                    detectDragGestures(
                                                        onDragStart = { localOffset ->
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            activeDraggedHomeVideo = video
                                                            dragPointerInRoot = dragHandlePos + localOffset
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragPointerInRoot += dragAmount
                                                        },
                                                        onDragEnd = { finishHomeVideoDrop() },
                                                        onDragCancel = { activeDraggedHomeVideo = null }
                                                    )
                                                }
                                                .testTag("drag_handle_home_video_${video.id}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragIndicator,
                                                contentDescription = "Drag video to playlist",
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    val metaLine = if (video.uploadDate.isNotBlank()) {
                                        "${video.channelName} • ${video.uploadDate}"
                                    } else {
                                        video.channelName
                                    }

                                    Text(
                                        text = metaLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // === 1. VISUAL PLAYLIST BADGES WITH ICON ===
                                    if (savedPlaylists.isNotEmpty()) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("video_playlist_indicators_${video.id}")
                                        ) {
                                            savedPlaylists.forEach { pl ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (savedPlaylists.size >= 2) Color(0xFF6A1B9A).copy(alpha = 0.14f) else Color(0xFF1B5E20).copy(alpha = 0.14f),
                                                    modifier = Modifier.clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSaveToPlaylist?.invoke(video)
                                                    }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlaylistPlay,
                                                            contentDescription = "Playlist",
                                                            tint = if (savedPlaylists.size >= 2) Color(0xFF6A1B9A) else Color(0xFF2E7D32),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = pl.title,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (savedPlaylists.size >= 2) Color(0xFF6A1B9A) else Color(0xFF2E7D32),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlaylistAdd,
                                                        contentDescription = "Not in playlist",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "No Playlist",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (onSaveToPlaylist != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSaveToPlaylist(video)
                                                    }
                                                ) {
                                                    Text(
                                                        text = "+ Save",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // === 2. AI MATCH BADGE + VISUAL NOTE / SCENE TIMESTAMP INDICATOR & 1-CLICK JUMP ===
                                    val aiMatch = if (isSearchActive && searchQuery.isNotBlank()) aiMatchByVideoId[video.videoId] else null
                                    if (aiMatch != null && aiMatch.matchReasonLabel.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                                        ) {
                                            Text(
                                                text = "${aiMatch.matchReasonLabel} (${aiMatch.matchConfidencePercent}%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (hasNote) {
                                        val parsedCardNotes = remember(linkedNote) {
                                            com.example.ui.player.parseSceneNotes(linkedNote)
                                        }
                                        val notesToShowOnCard = remember(parsedCardNotes, searchQuery, aiMatch) {
                                            if (aiMatch != null && aiMatch.bestMatchedSceneNotes.isNotEmpty()) {
                                                aiMatch.bestMatchedSceneNotes
                                            } else {
                                                parsedCardNotes.take(2)
                                            }
                                        }
                                        val noteMatchedSearch = searchQuery.isNotBlank() && aiMatch != null

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("video_note_indicator_${video.id}")
                                        ) {
                                            if (noteMatchedSearch) {
                                                Text(
                                                    text = "👆 Tap any timestamp badge below to jump directly to that scene:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFE65100),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            val fallbackTimestampSec = remember(parsedCardNotes) {
                                                parsedCardNotes.firstOrNull { it.timestampSeconds != null }?.timestampSeconds ?: 0
                                            }
                                            notesToShowOnCard.forEachIndexed { noteIdx, entry ->
                                                val hasTs = entry.timestampSeconds != null && entry.timestampLabel != null
                                                val jumpSec = entry.timestampSeconds ?: fallbackTimestampSec
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (noteMatchedSearch) Color(0xFFFFE0B2) else Color(0xFFFFF3E0),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            onSelectVideoAtTimestamp(video, jumpSec)
                                                        }
                                                        .testTag("video_note_item_${video.id}_$noteIdx")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Note Pencil Indicator",
                                                            tint = Color(0xFFE65100),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        if (hasTs) {
                                                            Surface(
                                                                shape = RoundedCornerShape(5.dp),
                                                                color = Color(0xFFE65100)
                                                            ) {
                                                                Text(
                                                                    text = "▶ ${entry.timestampLabel}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = Color.White,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                        }
                                                        Text(
                                                            text = entry.noteBody,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFE65100),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            if (parsedCardNotes.size > notesToShowOnCard.size) {
                                                Text(
                                                    text = "+ ${parsedCardNotes.size - notesToShowOnCard.size} more scene notes...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFE65100),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // In Global Search: Show containing group if from a group
                                    if (isSearchActive && searchScopeGlobal && video.groupId != null) {
                                        val grpName = allGroups.find { it.id == video.groupId }?.name
                                        if (grpName != null) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = "Group",
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = grpName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
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
        }

        // FAB to add video to active home item
        FloatingActionButton(
            onClick = onOpenAddVideoDialog,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("home_add_video_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Video"
            )
        }

        // Floating Drag Preview Ghost when dragging a video on Home
        activeDraggedHomeVideo?.let { draggingVideo ->
            val targetPlName = allPlaylists.find { it.playlistId == hoveredHomePlaylistId }?.title
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (targetPlName != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 10.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .zIndex(50f)
                    .offset {
                        IntOffset(
                            x = (dragPointerInRoot.x - rootBoxOriginInRoot.x - 110f).roundToInt().coerceAtLeast(8),
                            y = (dragPointerInRoot.y - rootBoxOriginInRoot.y - 68f).roundToInt().coerceAtLeast(8)
                        )
                    }
                    .testTag("home_video_drag_preview_ghost")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = draggingVideo.title.take(28) + if (draggingVideo.title.length > 28) "…" else "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = if (targetPlName != null) {
                                "📥 Drop to save/move to '$targetPlName'"
                            } else {
                                "🖐️ Drop onto any playlist chip at the top"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet to switch Home target
    if (showSwitchSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSwitchSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Set Home Destination",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Only videos from the selected Group or Playlist will be shown on your Home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Channel Groups
                Text(
                    text = "Channel Groups",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (allGroups.isEmpty()) {
                    Text(
                        text = "No channel groups created yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    allGroups.forEach { group ->
                        val isSelected = pinnedGroup?.id == group.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSelectPinnedGroup(group.id)
                                    showSwitchSheet = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectPinnedGroup(group.id)
                                        showSwitchSheet = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Playlists
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (allPlaylists.isEmpty()) {
                    Text(
                        text = "No playlists added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    allPlaylists.forEach { playlist ->
                        val isSelected = pinnedPlaylist?.playlistId == playlist.playlistId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSelectPinnedPlaylist(playlist.playlistId)
                                    showSwitchSheet = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectPinnedPlaylist(playlist.playlistId)
                                        showSwitchSheet = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
