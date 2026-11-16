package com.example.ui.playlists

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.util.YouTubeSyncHelper
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.local.entity.VideoEntity
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    videos: List<VideoEntity>,
    onBack: () -> Unit,
    onPinToHome: (String) -> Unit,
    onSelectVideo: (VideoEntity) -> Unit,
    onSelectVideoAtTimestamp: (VideoEntity, Int?) -> Unit = { v, _ -> onSelectVideo(v) },
    onToggleCompleted: (VideoEntity) -> Unit,
    onDeleteVideo: (Long) -> Unit,
    onMoveVideoToPlaylist: (VideoEntity, String) -> Unit = { _, _ -> },
    onOpenAddVideoDialog: () -> Unit,
    isSyncing: Boolean = false,
    onSyncPlaylist: (String) -> Unit = {},
    onMarkVideoSynced: (Long, Boolean) -> Unit = { _, _ -> },
    onMarkAllSynced: (String) -> Unit = {},
    onSyncBatchToYouTube: (List<String>) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    lastPlayedVideoId: String? = null,
    userProfile: com.example.util.GoogleUserProfile = com.example.util.GoogleUserProfile(),
    onOpenGoogleAccount: () -> Unit = {},
    onOpenBackupDialog: () -> Unit = {},
    onSyncAllNowWithAccount: () -> Unit = {},
    allSavedVideos: List<VideoEntity> = emptyList(),
    allPlaylists: List<PlaylistEntity> = emptyList(),
    groupsCount: Int = 0,
    onOpenReminderScreen: (() -> Unit)? = null,
    onOpenDashboardTab: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    // Search target mode: "BOTH" (Videos + Notes), "NOTES_ONLY", "VIDEOS_ONLY"
    var searchTargetMode by remember { mutableStateOf("BOTH") }
    var showSyncAssistantDialog by remember { mutableStateOf(false) }

    // Drag-and-Drop State for Moving Videos Between Playlists
    var activeDraggedVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var dragPointerInRoot by remember { mutableStateOf(Offset.Zero) }
    var rootBoxOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val playlistDropDockBounds = remember { mutableStateMapOf<String, Rect>() }
    val targetPlaylistsForMove = remember(allPlaylists, playlist.playlistId) {
        allPlaylists.filter { it.playlistId != playlist.playlistId }
    }
    val hoveredTargetPlaylistId: String? = remember(
        activeDraggedVideo,
        dragPointerInRoot,
        playlistDropDockBounds.toMap()
    ) {
        if (activeDraggedVideo == null) null
        else playlistDropDockBounds.entries.firstOrNull { (_, rect) -> rect.contains(dragPointerInRoot) }?.key
    }

    fun finishVideoPlaylistDrop() {
        val vid = activeDraggedVideo
        val targetPlId = hoveredTargetPlaylistId
        if (vid != null && targetPlId != null && targetPlId != playlist.playlistId) {
            onMoveVideoToPlaylist(vid, targetPlId)
        }
        activeDraggedVideo = null
    }

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

    // Display all videos stored in the playlist without secondary runtime filtering
    val safeVideos = videos
    val unsyncedVideos = remember(safeVideos) { safeVideos.filter { !it.isSyncedToYouTube } }

    // Fallback: If scroll state was reset to index 0 after watching a video in playlist,
    // automatically restore scroll position to the video the user was watching.
    LaunchedEffect(lastPlayedVideoId) {
        if (lastPlayedVideoId != null) {
            val targetIndex = safeVideos.indexOfFirst { it.videoId == lastPlayedVideoId }
            if (targetIndex > 2 && listState.firstVisibleItemIndex == 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    val videoToPlaylistNamesMap = remember(videoToPlaylistsMap) {
        videoToPlaylistsMap.mapValues { (_, list) -> list.joinToString(" ") { it.title } }
    }

    val playlistAiResults = remember(safeVideos, searchQuery, isSearchActive, searchTargetMode, videoToPlaylistNamesMap, videoToNotesMap) {
        com.example.util.AiSmartSearchEngine.searchVideosAndNotes(
            videos = safeVideos,
            rawQuery = if (isSearchActive) searchQuery else "",
            searchTargetMode = if (isSearchActive) searchTargetMode else "BOTH",
            videoToPlaylistsNames = videoToPlaylistNamesMap,
            videoToNotesMap = videoToNotesMap
        )
    }

    val filteredVideos = remember(playlistAiResults) { playlistAiResults.map { it.video } }
    val aiMatchByVideoId = remember(playlistAiResults) { playlistAiResults.associateBy { it.video.videoId } }

    val displayPlaylistTitle = remember(playlist.title) {
        if (playlist.title.isBlank() || playlist.title.equals("undefined", ignoreCase = true)) "My Playlist" else playlist.title
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { rootBoxOriginInRoot = it.positionInRoot() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "🧠 AI Smart Search: रफ आईडिया, टॉपिक, या टाइम (जैसे 6 min) लिखें...",
                                    fontSize = 11.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_playlist_search_input"),
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
                            Text(
                                text = displayPlaylistTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "${filteredVideos.size} of ${videos.size} videos" else "${videos.size} videos in playlist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search videos in playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { onSyncPlaylist(playlist.playlistId) },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("sync_playlist_button")
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
                                contentDescription = "Sync all videos from YouTube playlist",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (playlist.isPinnedHome) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1B5E20).copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "ACTIVE ON HOME",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = { onPinToHome(playlist.playlistId) }) {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = "Set as Home",
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                groupsCount = groupsCount,
                playlistsCount = allPlaylists.size,
                onTriggerSyncAndBackupNow = onSyncAllNowWithAccount,
                onOpenGoogleAccountDialog = onOpenGoogleAccount,
                onOpenBackupRestoreDialog = onOpenBackupDialog,
                onOpenReminderScreen = onOpenReminderScreen,
                onOpenDashboardTab = onOpenDashboardTab
            )

            // Search Target Filter Bar when Search is Active (Videos + Notes, Notes Only, Videos Only)
            if (isSearchActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🧠 AI Smart Search सक्रिय: पूरा शब्द याद न हो तो भी रफ आईडिया (हिंदी/Hinglish/English या ~6 min) लिखें — AI अर्थ समझकर वीडियो और नोट ढूंढ देगा:",
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
                                label = { Text("🔍 दोनों: वीडियो + नोट्स (${filteredVideos.size})", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = searchTargetMode == "NOTES_ONLY",
                                onClick = { searchTargetMode = "NOTES_ONLY" },
                                label = { Text("📝 सिर्फ नोट्स / सीन टाइम", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = searchTargetMode == "VIDEOS_ONLY",
                                onClick = { searchTargetMode = "VIDEOS_ONLY" },
                                label = { Text("🎬 सिर्फ वीडियो", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // === DRAG & DROP PLAYLIST MOVE DOCK (Drop any video onto another playlist to move it) ===
            if (targetPlaylistsForMove.isNotEmpty() && videos.isNotEmpty()) {
                val isDragging = activeDraggedVideo != null
                Surface(
                    color = if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_move_drop_dock")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = if (isDragging) 8.dp else 4.dp)
                    ) {
                        Text(
                            text = if (isDragging) {
                                "📦 '${activeDraggedVideo?.title}' को जिस प्लेलिस्ट में मूव करना है उस पर ड्रॉप करें:"
                            } else {
                                "🖐️ Drag & Drop: वीडियो के '⠿' हैंडल को पकड़कर दूसरी प्लेलिस्ट में मूव करें:"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDragging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(targetPlaylistsForMove, key = { "drop_pl_${it.playlistId}" }) { targetPl ->
                                val isHovered = hoveredTargetPlaylistId == targetPl.playlistId
                                val plTitle = if (targetPl.title.isBlank() || targetPl.title.equals("undefined", ignoreCase = true)) "My Playlist" else targetPl.title
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isHovered) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            playlistDropDockBounds[targetPl.playlistId] = coords.boundsInRoot()
                                        }
                                        .border(
                                            width = if (isHovered) 2.dp else 1.dp,
                                            color = if (isHovered) Color.White else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .testTag("drop_target_playlist_${targetPl.playlistId}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(
                                            horizontal = if (isDragging) 12.dp else 10.dp,
                                            vertical = if (isDragging) 7.dp else 4.dp
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlaylistPlay,
                                            contentDescription = null,
                                            tint = if (isHovered) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(if (isDragging) 16.dp else 14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isHovered) "📥 Move to $plTitle" else plTitle,
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

            val context = LocalContext.current
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // YouTube App Sync Status Banner
                if (unsyncedVideos.isNotEmpty() && !isSearchActive) {
                    item {
                        if (userProfile.isSignedIn) {
                            // === Connected Google Account & Nightly Sync Card ===
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Google Account: ${userProfile.email}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1B5E20).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "स्थायी लॉगिन",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🌙 रोज़ रात को ऑटो-सिंक सक्रिय है। आपकी प्लेलिस्ट हमेशा सुरक्षित रहेगी।",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = onSyncAllNowWithAccount,
                                            enabled = !isSyncing,
                                            modifier = Modifier.weight(1.3f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("🔄 अभी सिंक करें", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = onOpenGoogleAccount,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("अकाउंट सेटिंग्स", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            // === Not Signed In: Google Login Banner ===
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Google अकाउंट लॉगिन (स्थायी ऑटो-सिंक)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "सिर्फ 1 बार लॉगिन करें — फिर रोज़ रात को अपने आप बैकअप व सिंक होगा। दोबारा लॉगिन की कभी ज़रूरत नहीं पड़ेगी।",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = onOpenGoogleAccount,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Google अकाउंट से लॉगिन करें ↗", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (unsyncedVideos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${unsyncedVideos.size} video(s) pending official sync",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        TextButton(onClick = { showSyncAssistantDialog = true }) {
                                            Text("YouTube App Options ↗", fontSize = 11.sp)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val idsToSync = unsyncedVideos.map { it.videoId }
                                            onSyncBatchToYouTube(idsToSync)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("⚡ YouTube ऐप में 1-क्लिक में कतार खोलें ↗", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (videos.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Videos in Playlist",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Add YouTube videos using URL or Video ID.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onOpenAddVideoDialog) {
                                    Text("Add First Video")
                                }
                            }
                        }
                    }
                } else if (filteredVideos.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Matching Videos",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No video matched '$searchQuery' in this playlist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { searchQuery = "" }) {
                                    Text("Clear Search")
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredVideos,
                        key = { "${it.id}_${it.videoId}" },
                        contentType = { "playlist_video_card" }
                    ) { video ->
                        val cleanDuration = remember(video.duration, video.videoId) {
                            com.example.util.DurationUtils.formatCleanDuration(video.duration, video.videoId)
                        }
                        val allSavedForVideo = remember(video.videoId, playlist, videoToPlaylistsMap) {
                            val mapped = videoToPlaylistsMap[video.videoId].orEmpty()
                            if (mapped.any { it.playlistId == playlist.playlistId }) mapped
                            else listOf(playlist) + mapped
                        }
                        val linkedNote = videoToNotesMap[video.videoId] ?: video.notes.trim()
                        val hasNote = linkedNote.isNotBlank()
                        val isBeingDragged = activeDraggedVideo?.id == video.id
                        var cardPosInRoot by remember { mutableStateOf(Offset.Zero) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { cardPosInRoot = it.positionInRoot() }
                                .pointerInput(video.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            activeDraggedVideo = video
                                            dragPointerInRoot = cardPosInRoot + localOffset
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragPointerInRoot += dragAmount
                                        },
                                        onDragEnd = { finishVideoPlaylistDrop() },
                                        onDragCancel = { activeDraggedVideo = null }
                                    )
                                }
                                .clickable { onSelectVideo(video) },
                            shape = RoundedCornerShape(12.dp),
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
                                    .padding(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(11.dp))
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

                                    // Thumbnail Overlay Icons (Saved Playlists count + User Notes icon)
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = Color(0xFF1B5E20).copy(alpha = 0.9f),
                                            modifier = Modifier.testTag("playlist_thumb_pl_icon_${video.id}")
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
                                                    text = "${allSavedForVideo.size}",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
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
                                                    .testTag("playlist_thumb_note_icon_${video.id}")
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
                                            Spacer(modifier = Modifier.width(6.dp))
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
                                                        onSelectVideoAtTimestamp(video, firstNoteEntry?.timestampSeconds ?: 0)
                                                    }
                                                    .testTag("playlist_video_pencil_note_badge_${video.id}")
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
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                }
                                            }
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // YouTube Sync Status Chip / Action
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        if (video.isSyncedToYouTube) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF1B5E20).copy(alpha = 0.2f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF4CAF50),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "Synced in YouTube",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF4CAF50),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.clickable {
                                                    YouTubeSyncHelper.openInYouTubeApp(context, video.videoId, playlist.playlistId)
                                                    onMarkVideoSynced(video.id, true)
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Sync,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "Sync to YouTube ↗",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Visual Playlist Badges (shows all playlists this video is saved in)
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        allSavedForVideo.forEach { pl ->
                                            val plTitle = if (pl.title.isBlank() || pl.title.equals("undefined", ignoreCase = true)) "My Playlist" else pl.title
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (allSavedForVideo.size >= 2) Color(0xFF6A1B9A).copy(alpha = 0.14f) else Color(0xFF1B5E20).copy(alpha = 0.14f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlaylistPlay,
                                                        contentDescription = "Playlist",
                                                        tint = if (allSavedForVideo.size >= 2) Color(0xFF6A1B9A) else Color(0xFF2E7D32),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = plTitle,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (allSavedForVideo.size >= 2) Color(0xFF6A1B9A) else Color(0xFF2E7D32),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // AI Match Badge + Visual Note & Scene Timestamp Indicator Badge (1-Click Jump to Scene!)
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
                                                .testTag("playlist_video_note_indicator_${video.id}")
                                        ) {
                                            if (noteMatchedSearch) {
                                                Text(
                                                    text = "👆 नीचे टाइम बैज पर टैप करते ही वीडियो सीधे उसी सीन से चलेगी:",
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
                                                        .testTag("playlist_video_note_item_${video.id}_$noteIdx")
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
                                                    text = "+ ${parsedCardNotes.size - notesToShowOnCard.size} और सीन नोट्स...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFE65100),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Dedicated Drag Handle to move video to another playlist
                                    var dragHandlePos by remember { mutableStateOf(Offset.Zero) }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .onGloballyPositioned { dragHandlePos = it.positionInRoot() }
                                            .pointerInput(video.id) {
                                                detectDragGestures(
                                                    onDragStart = { localOffset ->
                                                        activeDraggedVideo = video
                                                        dragPointerInRoot = dragHandlePos + localOffset
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragPointerInRoot += dragAmount
                                                    },
                                                    onDragEnd = { finishVideoPlaylistDrop() },
                                                    onDragCancel = { activeDraggedVideo = null }
                                                )
                                            }
                                            .testTag("drag_handle_playlist_video_${video.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragIndicator,
                                            contentDescription = "Drag to move video to another playlist",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteVideo(video.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete video",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenAddVideoDialog,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add video"
            )
        }

        // Floating Drag Preview Ghost when moving a video between playlists
        activeDraggedVideo?.let { draggingVideo ->
            val targetPlName = targetPlaylistsForMove.find { it.playlistId == hoveredTargetPlaylistId }?.title
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
                    .testTag("playlist_video_drag_preview_ghost")
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
                                "📥 Drop to move to '$targetPlName'"
                            } else {
                                "🖐️ ऊपर किसी भी प्लेलिस्ट पर ड्रॉप करें"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        if (showSyncAssistantDialog) {
            YouTubeSyncAssistantDialog(
                playlist = playlist,
                unsyncedVideos = unsyncedVideos,
                onDismiss = { showSyncAssistantDialog = false },
                onSyncBatchToYouTube = { videoIds ->
                    onSyncBatchToYouTube(videoIds)
                },
                onOpenVideoInYouTube = { v ->
                    YouTubeSyncHelper.openInYouTubeApp(context, v.videoId, playlist.playlistId)
                    onMarkVideoSynced(v.id, true)
                },
                onMarkAllSynced = {
                    onMarkAllSynced(playlist.playlistId)
                    showSyncAssistantDialog = false
                },
                onCopyAllLinks = {
                    YouTubeSyncHelper.copyAllVideoLinks(context, playlist.title, videos)
                },
                onShareAllLinks = {
                    YouTubeSyncHelper.sharePlaylistVideos(context, playlist.title, videos)
                }
            )
        }
    }
}

/**
 * YouTube Sync Assistant Dialog:
 * Shows unsynced videos in this playlist, lets the user open them in the official YouTube app,
 * copy links in batch, share, or mark all as synced.
 */
@Composable
fun YouTubeSyncAssistantDialog(
    playlist: PlaylistEntity,
    unsyncedVideos: List<VideoEntity>,
    onDismiss: () -> Unit,
    onSyncBatchToYouTube: (List<String>) -> Unit = {},
    onOpenVideoInYouTube: (VideoEntity) -> Unit,
    onMarkAllSynced: () -> Unit,
    onCopyAllLinks: () -> Unit,
    onShareAllLinks: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "YouTube Sync Assistant",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Playlist: ${playlist.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (unsyncedVideos.isNotEmpty()) {
                        "There are ${unsyncedVideos.size} video(s) saved in FocusTube that you can add into your official YouTube app playlist with your logged-in Google account."
                    } else {
                        "All videos in this playlist are marked as synced! You can copy or share all video links below."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ⚡ 1-Click Batch Sync Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚡ 1-Click Batch Sync (अनुशंसित)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "एक ही क्लिक में YouTube ऐप में सभी वीडियो की कतार (Queue) खुलेगी। ऊपर '+' (Save) बटन दबाते ही सभी वीडियो आपकी YouTube प्लेलिस्ट में एक साथ सेव हो जाएंगे!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val targetIds = unsyncedVideos.map { it.videoId }
                                onSyncBatchToYouTube(targetIds)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("YouTube ऐप में 1 क्लिक में सिंक करें ↗", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (unsyncedVideos.isNotEmpty()) {
                    Text(
                        text = "Pending Videos (${unsyncedVideos.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    unsyncedVideos.forEachIndexed { idx, v ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. ${v.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = v.channelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onOpenVideoInYouTube(v) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Open ↗", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onMarkAllSynced,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Mark All as Synced (सभी सिंक मार्क करें)")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyAllLinks,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Links", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onShareAllLinks,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
