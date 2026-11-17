package com.example.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.VideoEntity
import com.example.util.GoogleUserProfile
import com.example.util.YouTubeSyncHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    pinnedPlaylist: PlaylistEntity?,
    allSavedVideos: List<VideoEntity> = emptyList(),
    onSelectPinnedPlaylist: (String) -> Unit,
    onOpenPlaylistVideos: (PlaylistEntity) -> Unit,
    onSelectVideoAtTimestamp: (VideoEntity, Int?) -> Unit = { _, _ -> },
    onOpenAddVideoToPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenCreatePlaylistDialog: () -> Unit,
    onOpenBackupDialog: () -> Unit = {},
    onSyncPlaylist: (String) -> Unit = {},
    isSyncing: Boolean = false,
    onSyncYouTubeAccount: () -> Unit = {},
    userProfile: GoogleUserProfile = GoogleUserProfile(),
    onOpenGoogleAccount: () -> Unit = {},
    groupsCount: Int = 0,
    onOpenReminderScreen: (() -> Unit)? = null,
    onOpenDashboardTab: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortAlphabetically by remember { mutableStateOf(true) } // Default A to Z as requested by user

    val playlistMapById = remember(playlists) {
        playlists.associateBy { it.playlistId }
    }

    val orderedPlaylists = remember(playlists, sortAlphabetically) {
        if (sortAlphabetically) {
            playlists.sortedWith(
                compareByDescending<PlaylistEntity> { it.isPinnedHome }
                    .thenBy { it.title.lowercase() }
            )
        } else {
            playlists
        }
    }

    val filteredPlaylists = remember(orderedPlaylists, searchQuery) {
        if (searchQuery.isBlank()) orderedPlaylists
        else orderedPlaylists.filter {
            com.example.util.AiSmartSearchEngine.scoreTextAgainstQuery(it.title, searchQuery) >= 0.28f
        }
    }

    // AI Smart Search across both Videos and Notes across all playlists when searchQuery is non-blank
    val aiSearchResultsMap = remember(allSavedVideos, searchQuery, playlistMapById) {
        if (searchQuery.isBlank()) emptyMap()
        else {
            com.example.util.AiSmartSearchEngine
                .searchVideosAndNotes(allSavedVideos.distinctBy { it.videoId }, searchQuery)
                .associateBy { it.video.videoId }
        }
    }

    val matchedVideosAndNotes = remember(aiSearchResultsMap, allSavedVideos, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else aiSearchResultsMap.values.map { it.video }
    }

    val hasSession = remember(userProfile) {
        userProfile.isYouTubeAutoSaveActive || YouTubeSyncHelper.hasYouTubeWebSession()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("✨ AI Smart Search: playlist, video, note, or time (6 min)...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_playlists_search_input"),
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
                                text = "Playlists (A–Z)",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "${filteredPlaylists.size} of ${playlists.size} playlists"
                                else "Total ${playlists.size} playlists (A to Z)",
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
                            modifier = Modifier.testTag("search_button_playlists")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Playlists"
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenBackupDialog,
                        modifier = Modifier.testTag("backup_button_playlists")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup & Restore Data"
                        )
                    }
                    IconButton(
                        onClick = onOpenGoogleAccount,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("google_account_button_playlists")
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
                groupsCount = groupsCount,
                playlistsCount = playlists.size,
                onTriggerSyncAndBackupNow = onSyncYouTubeAccount,
                onOpenGoogleAccountDialog = onOpenGoogleAccount,
                onOpenBackupRestoreDialog = onOpenBackupDialog,
                onOpenReminderScreen = onOpenReminderScreen,
                onOpenDashboardTab = onOpenDashboardTab
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 98.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Banner to sync ALL playlists from YouTube account
                item(key = "google_account_status_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlists_google_account_banner"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (userProfile.isSignedIn || hasSession) Color(0xFF1B5E20).copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenGoogleAccount() },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (userProfile.isSignedIn || hasSession) Icons.Default.CheckCircle else Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = if (userProfile.isSignedIn || hasSession) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (userProfile.isSignedIn) "Google: ${userProfile.email}" else "YouTube Playlists & Account Sync",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Total ${playlists.size} playlists (A to Z) • Nightly Auto-Backup active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Account ⚙️",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (hasSession || userProfile.isSignedIn) {
                                        onSyncYouTubeAccount()
                                    } else {
                                        onOpenGoogleAccount()
                                    }
                                },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("sync_all_youtube_playlists_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Syncing all playlists (A–Z) from YouTube...",
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "🔄 Sync All Playlists (A–Z) from YouTube",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (isSearchActive && searchQuery.isNotBlank()) {
                    item(key = "playlists_unified_search_summary") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✨ AI Smart Search ('${searchQuery}'): ${filteredPlaylists.size} playlists & ${matchedVideosAndNotes.size} matching videos/notes",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (matchedVideosAndNotes.isNotEmpty()) {
                        item(key = "matched_videos_notes_header") {
                            Text(
                                text = "🎬 & 📝 Matched Videos & Scene Notes (${matchedVideosAndNotes.size}):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(
                            items = matchedVideosAndNotes,
                            key = { "pl_search_vid_${it.id}_${it.videoId}" }
                        ) { video ->
                            val plTitle = video.playlistId?.let { playlistMapById[it]?.title } ?: "Saved Video"
                            val aiMatch = aiSearchResultsMap[video.videoId]
                            val parsedNotes = remember(video.notes) {
                                com.example.ui.player.parseSceneNotes(video.notes)
                            }
                            val matchingNotes = remember(parsedNotes, searchQuery, aiMatch) {
                                val aiNotes = aiMatch?.matchedSceneNotes.orEmpty()
                                if (aiNotes.isNotEmpty()) aiNotes else parsedNotes.take(2)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectVideoAtTimestamp(video, null) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (aiMatch != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF4A148C).copy(alpha = 0.12f),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        ) {
                                            Text(
                                                text = "${aiMatch.matchReasonLabel} (${aiMatch.confidencePercent}% Match)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6A1B9A),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = video.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1B5E20).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "📂 $plTitle",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (matchingNotes.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val fallbackTs = remember(parsedNotes) {
                                            parsedNotes.firstOrNull { it.timestampSeconds != null }?.timestampSeconds ?: 0
                                        }
                                        matchingNotes.forEach { entry ->
                                            val hasTs = entry.timestampSeconds != null && entry.timestampLabel != null
                                            val jumpSec = entry.timestampSeconds ?: fallbackTs
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFFFE0B2),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .clickable {
                                                        onSelectVideoAtTimestamp(video, jumpSec)
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                    }
                                                    Text(
                                                        text = entry.noteBody,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFFE65100),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
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

                if (playlists.isNotEmpty()) {
                    item(key = "playlist_sort_row") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = sortAlphabetically,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        sortAlphabetically = true
                                    },
                                    label = {
                                        Text(
                                            text = "🔤 A–Z (${playlists.size})",
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = !sortAlphabetically,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        sortAlphabetically = false
                                    },
                                    label = {
                                        Text(
                                            text = "🕒 Default",
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                )
                            }
                            TextButton(
                                onClick = { isSearchActive = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Search Videos/Notes",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    item(key = "empty_playlists") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No Playlists Added Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap 'Sync All Playlists (A–Z) from YouTube' above or '+ Add YouTube Playlist' below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onOpenCreatePlaylistDialog) {
                                    Text("+ Add YouTube Playlist")
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredPlaylists,
                        key = { it.playlistId },
                        contentType = { "playlist_card" }
                    ) { playlist ->
                        val isPinned = pinnedPlaylist?.playlistId == playlist.playlistId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPlaylistVideos(playlist) }
                                .testTag("playlist_card_${playlist.playlistId}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surface
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
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlaylistPlay,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = playlist.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onSyncPlaylist(playlist.playlistId) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("sync_playlist_${playlist.playlistId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Sync all videos from YouTube playlist",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                playlistToDelete = playlist
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isPinned) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1B5E20).copy(alpha = 0.2f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = null,
                                                    tint = Color(0xFF4CAF50),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Active on Home",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4CAF50)
                                                )
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onSelectPinnedPlaylist(playlist.playlistId) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("pin_playlist_${playlist.playlistId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.PushPin,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Set as Home",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { onOpenAddVideoToPlaylist(playlist.playlistId) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("+ Video", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Button(
                                            onClick = { onOpenPlaylistVideos(playlist) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Open",
                                                style = MaterialTheme.typography.labelMedium
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

        // FAB to add playlist
        FloatingActionButton(
            onClick = onOpenCreatePlaylistDialog,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_playlist_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Playlist"
            )
        }
    }

    playlistToDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete '${pl.title}'?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete the '${pl.title}' playlist?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDeletePlaylist(pl.playlistId)
                        playlistToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
