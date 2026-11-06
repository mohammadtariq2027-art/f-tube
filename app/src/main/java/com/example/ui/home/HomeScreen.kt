package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity

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
    onToggleCompleted: (VideoEntity) -> Unit,
    onSaveToPlaylist: ((VideoEntity) -> Unit)? = null,
    onOpenAddVideoDialog: () -> Unit,
    isSyncing: Boolean = false,
    onSyncGroup: (Long) -> Unit = {},
    onSyncPlaylist: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    lastPlayedVideoId: String? = null,
    userProfile: com.example.util.GoogleUserProfile = com.example.util.GoogleUserProfile(),
    onOpenGoogleAccount: () -> Unit = {}
) {
    var showSwitchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchScopeGlobal by remember { mutableStateOf(true) }

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

    val safeFeedVideos = videos
    val safeGlobalVideos = allSavedVideos

    val filteredFeedVideos = remember(safeFeedVideos, searchQuery) {
        if (searchQuery.isBlank()) safeFeedVideos
        else safeFeedVideos.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.channelName.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredGlobalVideos = remember(safeGlobalVideos, searchQuery) {
        if (searchQuery.isBlank()) safeGlobalVideos
        else safeGlobalVideos.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.channelName.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true)
        }
    }

    val displayedVideos = if (isSearchActive && searchScopeGlobal) filteredGlobalVideos else filteredFeedVideos

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Home Top App Bar with Global & Local Feed Search
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(if (searchScopeGlobal) "Search all saved videos & notes..." else "Search current feed...") },
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
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF2E7D32)
                                ) {
                                    Text(
                                        text = "🛡️ SHIELD",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (pinnedGroup != null) "Active Group (${videos.size} videos) • Work & Focus Only"
                                else if (pinnedPlaylist != null) "Active Playlist (${videos.size} videos) • Work & Focus Only"
                                else "No active feed chosen",
                                style = MaterialTheme.typography.bodySmall,
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

            // Search Scope Selector (Global across all playlists/groups vs Active feed)
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = searchScopeGlobal,
                        onClick = { searchScopeGlobal = true },
                        label = { Text("Global (${filteredGlobalVideos.size})") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                    FilterChip(
                        selected = !searchScopeGlobal,
                        onClick = { searchScopeGlobal = false },
                        label = { Text("Active Feed (${filteredFeedVideos.size})") }
                    )
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
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedVideos, key = { "${it.id}_${it.videoId}" }) { video ->
                        val cleanDuration = remember(video.duration, video.videoId) {
                            com.example.util.DurationUtils.formatCleanDuration(video.duration, video.videoId)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectVideo(video) }
                                .testTag("home_video_${video.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (video.isCompleted) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Prominent Enlarged Thumbnail (160dp width, 16:9, rounded corners)
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
                                                .crossfade(150)
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
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val metaLine = if (video.uploadDate.isNotBlank()) {
                                        "${video.channelName} • ${video.uploadDate}"
                                    } else {
                                        video.channelName
                                    }

                                    Text(
                                        text = metaLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Attached Notes Indicator Badge
                                    if (video.notes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = "📝 Notes attached",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // In Global Search: Show containing group or playlist origin
                                    if (isSearchActive && searchScopeGlobal) {
                                        val sourceLabel = when {
                                            video.playlistId != null -> allPlaylists.find { it.playlistId == video.playlistId }?.let { "Playlist: ${it.title}" }
                                            video.groupId != null -> allGroups.find { it.id == video.groupId }?.let { "Group: ${it.name}" }
                                            else -> null
                                        }
                                        if (sourceLabel != null) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "📁 $sourceLabel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
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
