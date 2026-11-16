package com.example.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.SearchHistoryEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.ui.components.SearchHistoryChips
import com.example.util.FocusShield
import com.example.util.GoogleUserProfile
import com.example.util.YouTubeSyncHelper
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

data class MasterChannelItem(
    val name: String,
    val handleOrUrl: String,
    val assignedGroupIds: Set<Long>
)

private data class ChannelDragPayload(
    val channelName: String,
    val handleOrUrl: String,
    val sourceChannelId: Long? = null,
    val sourceGroupId: Long? = null
)

private val GROUP_BADGE_PALETTE = listOf(
    Pair(Color(0xFFE3F2FD), Color(0xFF0D47A1)), // Blue
    Pair(Color(0xFFE8F5E9), Color(0xFF1B5E20)), // Green
    Pair(Color(0xFFF3E5F5), Color(0xFF4A148C)), // Purple
    Pair(Color(0xFFFFF3E0), Color(0xFFE65100)), // Orange
    Pair(Color(0xFFE0F2F1), Color(0xFF004D40)), // Teal
    Pair(Color(0xFFFCE4EC), Color(0xFF880E4F)), // Pink/Rose
    Pair(Color(0xFFE8EAF6), Color(0xFF1A237E)), // Indigo
    Pair(Color(0xFFFFF8E1), Color(0xFFF57F17))  // Amber
)

private fun getGroupBadgeColors(groupId: Long, groupName: String): Pair<Color, Color> {
    val index = ((if (groupId > 0L) groupId.toInt() else groupName.hashCode()) and Int.MAX_VALUE) % GROUP_BADGE_PALETTE.size
    return GROUP_BADGE_PALETTE[index]
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelGroupBadges(
    assignedGroups: List<SubscriptionGroupEntity>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (assignedGroups.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFECEFF1),
                modifier = Modifier.testTag("channel_badge_uncategorized")
            ) {
                Text(
                    text = "Uncategorized",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF546E7A),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            assignedGroups.forEach { grp ->
                val (bgColor, textColor) = getGroupBadgeColors(grp.id, grp.name)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = bgColor,
                    modifier = Modifier.testTag("channel_badge_group_${grp.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(textColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = grp.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupsScreen(
    groups: List<SubscriptionGroupEntity>,
    pinnedGroup: SubscriptionGroupEntity?,
    allChannels: List<ChannelEntity> = emptyList(),
    searchHistory: List<SearchHistoryEntity> = emptyList(),
    getChannelsForGroup: (Long) -> Flow<List<ChannelEntity>>,
    onSelectPinnedGroup: (Long) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onAddChannel: (String, Long) -> Unit,
    onDeleteChannel: (Long) -> Unit,
    onMoveChannel: (Long, Long) -> Unit,
    onToggleChannelInGroup: (String, String, Long, Boolean) -> Unit = { _, _, _, _ -> },
    onOpenAddVideoToGroup: (Long) -> Unit,
    onDeleteSearchHistoryItem: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onOpenBackupDialog: () -> Unit = {},
    onSyncGroup: (Long) -> Unit = {},
    isSyncing: Boolean = false,
    userProfile: GoogleUserProfile = GoogleUserProfile(),
    onOpenGoogleAccount: () -> Unit = {},
    onSyncYouTubeAccount: () -> Unit = {},
    allSavedVideos: List<com.example.data.local.entity.VideoEntity> = emptyList(),
    playlistsCount: Int = 0,
    onOpenReminderScreen: (() -> Unit)? = null,
    onOpenDashboardTab: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState()
) {
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var channelTargetGroupId by remember { mutableStateOf<Long?>(null) }
    var tickChannelsTargetGroup by remember { mutableStateOf<SubscriptionGroupEntity?>(null) }
    var selectedChannelForGroupTick by remember { mutableStateOf<MasterChannelItem?>(null) }
    var groupToDelete by remember { mutableStateOf<SubscriptionGroupEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    // false = My Groups tab, true = All Subscribed Channels (A-Z) PocketTube tab
    var showAllChannelsTab by remember { mutableStateOf(false) }

    // Filter mode for All Channels A-Z tab: "ALL", "UNASSIGNED", "SINGLE", "DOUBLE", or "GROUP_<id>"
    var channelMembershipFilter by remember { mutableStateOf("ALL") }

    // Build complete A-to-Z master channel list (combining user's subscribed channels in DB + valid group assignments)
    val validGroupIds = remember(groups) { groups.map { it.id }.toSet() }
    val allMasterChannels = remember(allChannels, validGroupIds) {
        val map = LinkedHashMap<String, MasterChannelItem>()
        for (ch in allChannels) {
            val key = ch.name.trim().lowercase()
            if (key.isBlank()) continue
            val existing = map[key]
            val groupSet = if (ch.groupId > 0L && ch.groupId in validGroupIds) setOf(ch.groupId) else emptySet()
            if (existing == null) {
                map[key] = MasterChannelItem(
                    name = ch.name.trim(),
                    handleOrUrl = ch.handleOrUrl.trim(),
                    assignedGroupIds = groupSet
                )
            } else {
                map[key] = existing.copy(
                    handleOrUrl = existing.handleOrUrl.ifBlank { ch.handleOrUrl.trim() },
                    assignedGroupIds = existing.assignedGroupIds + groupSet
                )
            }
        }
        map.values.sortedBy { it.name.lowercase() }
    }

    val unassignedChannelsCount = remember(allMasterChannels) {
        allMasterChannels.count { it.assignedGroupIds.isEmpty() }
    }
    val singleGroupChannelsCount = remember(allMasterChannels) {
        allMasterChannels.count { it.assignedGroupIds.size == 1 }
    }
    val doubleGroupChannelsCount = remember(allMasterChannels) {
        allMasterChannels.count { it.assignedGroupIds.size >= 2 }
    }

    val filteredGroups = remember(groups, searchQuery) {
        if (searchQuery.isBlank()) groups
        else groups.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredMasterChannels = remember(allMasterChannels, searchQuery, channelMembershipFilter, groups) {
        val bySearch = if (searchQuery.isBlank()) allMasterChannels
        else allMasterChannels.filter { ch ->
            val groupNames = groups.filter { it.id in ch.assignedGroupIds }.joinToString(" ") { it.name }
            ch.name.contains(searchQuery, ignoreCase = true) ||
                ch.handleOrUrl.contains(searchQuery, ignoreCase = true) ||
                groupNames.contains(searchQuery, ignoreCase = true)
        }
        when {
            channelMembershipFilter == "UNASSIGNED" -> bySearch.filter { it.assignedGroupIds.isEmpty() }
            channelMembershipFilter == "SINGLE" -> bySearch.filter { it.assignedGroupIds.size == 1 }
            channelMembershipFilter == "DOUBLE" -> bySearch.filter { it.assignedGroupIds.size >= 2 }
            channelMembershipFilter.startsWith("GROUP_") -> {
                val gId = channelMembershipFilter.removePrefix("GROUP_").toLongOrNull()
                if (gId != null) bySearch.filter { gId in it.assignedGroupIds } else bySearch
            }
            else -> bySearch
        }
    }

    // Drag-and-Drop State for Channels -> Groups
    var activeDragChannel by remember { mutableStateOf<ChannelDragPayload?>(null) }
    var dragPointerInRoot by remember { mutableStateOf(Offset.Zero) }
    var rootBoxOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val groupDropDockBounds = remember { mutableStateMapOf<Long, Rect>() }
    val groupCardBounds = remember { mutableStateMapOf<Long, Rect>() }

    val hoveredDropGroupId: Long? = remember(
        activeDragChannel,
        dragPointerInRoot,
        groupDropDockBounds.toMap(),
        groupCardBounds.toMap()
    ) {
        val dragging = activeDragChannel ?: return@remember null
        groupDropDockBounds.entries.firstOrNull { (gId, rect) ->
            gId != dragging.sourceGroupId && rect.contains(dragPointerInRoot)
        }?.key ?: groupCardBounds.entries.firstOrNull { (gId, rect) ->
            gId != dragging.sourceGroupId && rect.contains(dragPointerInRoot)
        }?.key
    }

    fun finishChannelDrop() {
        val dragging = activeDragChannel
        val targetGroupId = hoveredDropGroupId
        if (dragging != null && targetGroupId != null && targetGroupId != dragging.sourceGroupId) {
            if (dragging.sourceChannelId != null) {
                onMoveChannel(dragging.sourceChannelId, targetGroupId)
            } else {
                onToggleChannelInGroup(dragging.channelName, dragging.handleOrUrl, targetGroupId, true)
            }
        }
        activeDragChannel = null
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
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(if (showAllChannelsTab) "चैनल खोजें (A to Z)..." else "ग्रुप खोजें...")
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_groups_search_input"),
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
                                text = "Groups & Channels (A–Z)",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${groups.size} ग्रुप्स • ${allMasterChannels.size} चैनल्स (PocketTube Mode)",
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
                            modifier = Modifier.testTag("search_button_groups")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Groups or Channels"
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenBackupDialog,
                        modifier = Modifier.testTag("backup_button_groups")
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
                            .testTag("google_account_button_groups")
                    ) {
                        Icon(
                            imageVector = if (userProfile.isSignedIn) Icons.Default.AccountCircle else Icons.Default.CloudSync,
                            contentDescription = "Google Account",
                            tint = if (userProfile.isSignedIn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                groupsCount = groups.size,
                playlistsCount = playlistsCount,
                onTriggerSyncAndBackupNow = onSyncYouTubeAccount,
                onOpenGoogleAccountDialog = onOpenGoogleAccount,
                onOpenBackupRestoreDialog = onOpenBackupDialog,
                onOpenReminderScreen = onOpenReminderScreen,
                onOpenDashboardTab = onOpenDashboardTab
            )

            // === POCKETTUBE MODE SWITCHER: [📁 मेरे ग्रुप्स] vs [📺 सारे चैनल्स (A–Z)] ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { showAllChannelsTab = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_my_groups"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (!showAllChannelsTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (!showAllChannelsTab) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "📁 मेरे ग्रुप्स (${groups.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                FilledTonalButton(
                    onClick = { showAllChannelsTab = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_all_channels_az"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (showAllChannelsTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (showAllChannelsTab) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "📺 सारे चैनल्स A–Z (${allMasterChannels.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // === DRAG & DROP GROUP TARGET DOCK (Always available when groups exist; expands/glows during drag) ===
            if (groups.isNotEmpty() && (showAllChannelsTab || activeDragChannel != null)) {
                val isDragging = activeDragChannel != null
                Surface(
                    color = if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("channel_group_drop_dock")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = if (isDragging) 8.dp else 4.dp)
                    ) {
                        Text(
                            text = if (isDragging) {
                                "📥 '${activeDragChannel?.channelName}' को जिस ग्रुप में जोड़ना/मूव करना है उस पर ड्रॉप करें:"
                            } else {
                                "🖐️ Drag & Drop: किसी भी चैनल के '⠿' हैंडल को पकड़कर नीचे किसी भी ग्रुप पर ड्रॉप करें:"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDragging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(groups, key = { "drop_dock_grp_${it.id}" }) { grp ->
                                val isHovered = hoveredDropGroupId == grp.id
                                val (bgColor, fgColor) = getGroupBadgeColors(grp.id, grp.name)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isHovered) Color(0xFF2E7D32) else bgColor,
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            groupDropDockBounds[grp.id] = coords.boundsInRoot()
                                        }
                                        .border(
                                            width = if (isHovered) 2.dp else 1.dp,
                                            color = if (isHovered) Color.White else fgColor.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .testTag("drop_target_group_${grp.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(
                                            horizontal = if (isDragging) 12.dp else 10.dp,
                                            vertical = if (isDragging) 8.dp else 5.dp
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isHovered) Color.White else fgColor,
                                            modifier = Modifier.size(if (isDragging) 16.dp else 14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isHovered) "📥 Drop in ${grp.name}" else grp.name,
                                            fontSize = if (isDragging) 12.sp else 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHovered) Color.White else fgColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAllChannelsTab) {
                // =========================================================================
                // VIEW 2: POCKETTUBE STYLE "ALL SUBSCRIBED CHANNELS (A to Z)" LIST
                // =========================================================================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "sync_youtube_subscriptions_card") {
                        YouTubeAccountChannelsSyncBanner(
                            userProfile = userProfile,
                            isSyncing = isSyncing,
                            totalChannelsCount = allMasterChannels.size,
                            onSyncYouTubeAccount = onSyncYouTubeAccount,
                            onOpenGoogleAccount = onOpenGoogleAccount
                        )
                    }

                    // === CHANNEL GROUP STATUS FILTER BAR (All / Unassigned / 1 Group / Double Group / By Group) ===
                    item(key = "channel_membership_filter_bar") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "📊 चैनल ग्रुप स्टेटस फ़िल्टर (टैप करके छांटें):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item {
                                        FilterChip(
                                            selected = channelMembershipFilter == "ALL",
                                            onClick = { channelMembershipFilter = "ALL" },
                                            label = { Text("🔤 सभी (${allMasterChannels.size})", fontSize = 11.sp) }
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = channelMembershipFilter == "UNASSIGNED",
                                            onClick = { channelMembershipFilter = "UNASSIGNED" },
                                            label = { Text("⚠️ Uncategorized / बिना ग्रुप ($unassignedChannelsCount)", fontSize = 11.sp) }
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = channelMembershipFilter == "DOUBLE",
                                            onClick = { channelMembershipFilter = "DOUBLE" },
                                            label = { Text("⚡ डबल ग्रुप में ($doubleGroupChannelsCount)", fontSize = 11.sp) }
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = channelMembershipFilter == "SINGLE",
                                            onClick = { channelMembershipFilter = "SINGLE" },
                                            label = { Text("✅ 1 ग्रुप में ($singleGroupChannelsCount)", fontSize = 11.sp) }
                                        )
                                    }
                                }
                                if (groups.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(groups, key = { "filter_grp_${it.id}" }) { grp ->
                                            val countInGrp = allMasterChannels.count { grp.id in it.assignedGroupIds }
                                            FilterChip(
                                                selected = channelMembershipFilter == "GROUP_${grp.id}",
                                                onClick = {
                                                    channelMembershipFilter = if (channelMembershipFilter == "GROUP_${grp.id}") "ALL" else "GROUP_${grp.id}"
                                                },
                                                label = { Text("📁 ${grp.name} ($countInGrp)", fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredMasterChannels.isEmpty()) {
                        item(key = "empty_channels_card") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank() || channelMembershipFilter != "ALL") "इस फ़िल्टर में कोई चैनल नहीं मिला" else "आपके Subscribed Channels यहाँ A से Z तक दिखेंगे",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (channelMembershipFilter != "ALL") {
                                            "सभी चैनल्स देखने के लिए ऊपर '🔤 सभी' पर टैप करें।"
                                        } else {
                                            "ऊपर 'मेरे सारे Subscribed Channels व Playlists (A–Z) सिंक करें' बटन दबाएं ताकि आपके YouTube अकाउंट के सभी सब्सक्राइब किए हुए चैनल्स A–Z आ जाएं।"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredMasterChannels,
                            key = { it.name.lowercase() },
                            contentType = { "master_channel_row" }
                        ) { chItem ->
                            val assignedGroups = remember(chItem.assignedGroupIds, groups) {
                                groups.filter { it.id in chItem.assignedGroupIds }
                            }
                            val isUnassigned = assignedGroups.isEmpty()
                            val isDoubleGroup = assignedGroups.size >= 2

                            val isBeingDragged = activeDragChannel?.channelName == chItem.name
                            var rowCoordsInRoot by remember { mutableStateOf(Offset.Zero) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { rowCoordsInRoot = it.positionInRoot() }
                                    .pointerInput(chItem.name, chItem.handleOrUrl) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { localOffset ->
                                                activeDragChannel = ChannelDragPayload(
                                                    channelName = chItem.name,
                                                    handleOrUrl = chItem.handleOrUrl
                                                )
                                                dragPointerInRoot = rowCoordsInRoot + localOffset
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragPointerInRoot += dragAmount
                                            },
                                            onDragEnd = { finishChannelDrop() },
                                            onDragCancel = { activeDragChannel = null }
                                        )
                                    }
                                    .clickable { selectedChannelForGroupTick = chItem }
                                    .testTag("master_channel_${chItem.name}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isBeingDragged -> MaterialTheme.colorScheme.primaryContainer
                                        isDoubleGroup -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                                        isUnassigned -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Dedicated instant Drag Handle for organizing channel into any group
                                            var handleCoordsInRoot by remember { mutableStateOf(Offset.Zero) }
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .onGloballyPositioned { handleCoordsInRoot = it.positionInRoot() }
                                                    .pointerInput(chItem.name, chItem.handleOrUrl) {
                                                        detectDragGestures(
                                                            onDragStart = { localOffset ->
                                                                activeDragChannel = ChannelDragPayload(
                                                                    channelName = chItem.name,
                                                                    handleOrUrl = chItem.handleOrUrl
                                                                )
                                                                dragPointerInRoot = handleCoordsInRoot + localOffset
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragPointerInRoot += dragAmount
                                                            },
                                                            onDragEnd = { finishChannelDrop() },
                                                            onDragCancel = { activeDragChannel = null }
                                                        )
                                                    }
                                                    .testTag("drag_handle_channel_${chItem.name}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DragIndicator,
                                                    contentDescription = "Drag channel to group",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = chItem.name.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = chItem.name,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    ChannelGroupBadges(assignedGroups = assignedGroups)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                // Status Summary: Uncategorized vs 1 Group vs Double/Multi Group
                                                when {
                                                    isUnassigned -> {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFFE65100).copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = "⚠️ किसी भी ग्रुप में शामिल नहीं (Uncategorized)",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color(0xFFE65100),
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                    }
                                                    isDoubleGroup -> {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFF6A1B9A).copy(alpha = 0.16f)
                                                        ) {
                                                            Text(
                                                                text = "⚡ डबल ग्रुप (${assignedGroups.size} ग्रुप्स में शामिल)",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color(0xFF6A1B9A),
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFF1B5E20).copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = "✅ 1 ग्रुप में शामिल",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color(0xFF2E7D32),
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        FilledTonalButton(
                                            onClick = { selectedChannelForGroupTick = chItem },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckBox,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "ग्रुप ☑ (${assignedGroups.size})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // =========================================================================
                // VIEW 1: MY CHANNEL GROUPS (with PocketTube A-Z Channel Tick Button on each Group)
                // =========================================================================
                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            YouTubeAccountChannelsSyncBanner(
                                userProfile = userProfile,
                                isSyncing = isSyncing,
                                totalChannelsCount = allMasterChannels.size,
                                onSyncYouTubeAccount = onSyncYouTubeAccount,
                                onOpenGoogleAccount = onOpenGoogleAccount
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "कोई चैनल ग्रुप नहीं बना है",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "नया ग्रुप बनाएं और अपने सभी Subscribed YouTube चैनल्स को टिक (☑) करके ग्रुप में शामिल करें।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showCreateGroupDialog = true }) {
                                Text("+ नया ग्रुप बनाएं (Create Group)")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "sync_youtube_subscriptions_banner") {
                            YouTubeAccountChannelsSyncBanner(
                                userProfile = userProfile,
                                isSyncing = isSyncing,
                                totalChannelsCount = allMasterChannels.size,
                                onSyncYouTubeAccount = onSyncYouTubeAccount,
                                onOpenGoogleAccount = onOpenGoogleAccount
                            )
                        }

                        items(
                            items = filteredGroups,
                            key = { it.id },
                            contentType = { "group_card" }
                        ) { group ->
                            val isPinned = pinnedGroup?.id == group.id
                            val isDropHovered = hoveredDropGroupId == group.id
                            val channelsState = remember(group.id) { getChannelsForGroup(group.id) }
                            val channels by channelsState.collectAsState(initial = emptyList())

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        groupCardBounds[group.id] = coords.boundsInRoot()
                                    }
                                    .then(
                                        if (isDropHovered) {
                                            Modifier.border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(16.dp))
                                        } else Modifier
                                    )
                                    .testTag("group_card_${group.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isDropHovered -> Color(0xFFE8F5E9)
                                        isPinned -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (isDropHovered && activeDragChannel != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        ) {
                                            Text(
                                                text = "📥 यहाँ छोड़ें: '${activeDragChannel?.channelName}' को '${group.name}' में मूव करें",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                    // Header: Group Title + Sync + Delete
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (channels.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { onSyncGroup(group.id) },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .testTag("sync_group_${group.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Sync all channel videos in group",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { groupToDelete = group },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete group",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // PocketTube Style Prominent Button: Tick Channels from A-Z List
                                    FilledTonalButton(
                                        onClick = { tickChannelsTargetGroup = group },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("tick_channels_group_${group.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckBox,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "☑️ A–Z लिस्ट से चैनल्स टिक करके जोड़ें (${allMasterChannels.size} चैनल्स)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Channels in this group
                                    Text(
                                        text = "इस ग्रुप के चैनल्स (${channels.size}) • 🖐️ ड्रैग करके दूसरे ग्रुप में भेजें:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (channels.isEmpty()) {
                                        Text(
                                            text = "अभी कोई चैनल नहीं जुड़ा है। ऊपर '☑️ A–Z लिस्ट से चैनल्स टिक करके जोड़ें' या '+ Channel' दबाएं।",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    } else {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            channels.forEach { ch ->
                                                var showMoveMenu by remember { mutableStateOf(false) }
                                                var chipPosInRoot by remember { mutableStateOf(Offset.Zero) }

                                                Box(
                                                    modifier = Modifier
                                                        .onGloballyPositioned { chipPosInRoot = it.positionInRoot() }
                                                        .pointerInput(ch.id, group.id) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = { localOffset ->
                                                                    activeDragChannel = ChannelDragPayload(
                                                                        channelName = ch.name,
                                                                        handleOrUrl = ch.handleOrUrl,
                                                                        sourceChannelId = ch.id,
                                                                        sourceGroupId = group.id
                                                                    )
                                                                    dragPointerInRoot = chipPosInRoot + localOffset
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dragPointerInRoot += dragAmount
                                                                },
                                                                onDragEnd = { finishChannelDrop() },
                                                                onDragCancel = { activeDragChannel = null }
                                                            )
                                                        }
                                                ) {
                                                    InputChip(
                                                        selected = activeDragChannel?.sourceChannelId == ch.id,
                                                        onClick = { showMoveMenu = true },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Default.DragIndicator,
                                                                contentDescription = "Drag channel to another group",
                                                                modifier = Modifier
                                                                    .size(15.dp)
                                                                    .pointerInput(ch.id, group.id) {
                                                                        detectDragGestures(
                                                                            onDragStart = { localOffset ->
                                                                                activeDragChannel = ChannelDragPayload(
                                                                                    channelName = ch.name,
                                                                                    handleOrUrl = ch.handleOrUrl,
                                                                                    sourceChannelId = ch.id,
                                                                                    sourceGroupId = group.id
                                                                                )
                                                                                dragPointerInRoot = chipPosInRoot + localOffset
                                                                            },
                                                                            onDrag = { change, dragAmount ->
                                                                                change.consume()
                                                                                dragPointerInRoot += dragAmount
                                                                            },
                                                                            onDragEnd = { finishChannelDrop() },
                                                                            onDragCancel = { activeDragChannel = null }
                                                                        )
                                                                    }
                                                            )
                                                        },
                                                        label = { Text(ch.name) },
                                                        trailingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Remove channel",
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .clickable { onDeleteChannel(ch.id) }
                                                            )
                                                        }
                                                    )

                                                    DropdownMenu(
                                                        expanded = showMoveMenu,
                                                        onDismissRequest = { showMoveMenu = false }
                                                    ) {
                                                        Text(
                                                            text = "Move channel to:",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        groups.filter { it.id != group.id }.forEach { otherGroup ->
                                                            DropdownMenuItem(
                                                                text = { Text(otherGroup.name) },
                                                                onClick = {
                                                                    onMoveChannel(ch.id, otherGroup.id)
                                                                    showMoveMenu = false
                                                                }
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text("ग्रुप से हटाएं (Remove)", color = MaterialTheme.colorScheme.error) },
                                                            onClick = {
                                                                onDeleteChannel(ch.id)
                                                                showMoveMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Action Buttons
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
                                                onClick = { onSelectPinnedGroup(group.id) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("pin_group_${group.id}")
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
                                                onClick = { channelTargetGroupId = group.id },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("+ Channel", style = MaterialTheme.typography.labelSmall)
                                            }

                                            Button(
                                                onClick = { onOpenAddVideoToGroup(group.id) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("+ Video", style = MaterialTheme.typography.labelSmall)
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

        // FAB to create a new group
        FloatingActionButton(
            onClick = { showCreateGroupDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_group_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Group"
            )
        }

        // Floating Drag Preview Ghost for Channel Drag-and-Drop
        activeDragChannel?.let { dragging ->
            val targetGroupName = groups.find { it.id == hoveredDropGroupId }?.name
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (targetGroupName != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 10.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .zIndex(50f)
                    .offset {
                        IntOffset(
                            x = (dragPointerInRoot.x - rootBoxOriginInRoot.x - 100f).roundToInt().coerceAtLeast(8),
                            y = (dragPointerInRoot.y - rootBoxOriginInRoot.y - 64f).roundToInt().coerceAtLeast(8)
                        )
                    }
                    .testTag("channel_drag_preview_ghost")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "📺 ${dragging.channelName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (targetGroupName != null) {
                                "📥 Drop to add/move to '$targetGroupName'"
                            } else {
                                "🖐️ किसी भी ग्रुप पर ले जाकर छोड़ें"
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

    // =========================================================================
    // DIALOG 1: POCKETTUBE-STYLE "TICK CHANNELS (A to Z) FOR A GROUP"
    // =========================================================================
    tickChannelsTargetGroup?.let { targetGroup ->
        var channelSearch by remember { mutableStateOf("") }
        var dialogFilter by remember { mutableStateOf("ALL") } // ALL, UNASSIGNED, DOUBLE, IN_THIS_GROUP
        // Combine user's subscribed channels in DB + curated channels if user wants more options
        val combinedChannels = remember(allMasterChannels) {
            val map = LinkedHashMap<String, MasterChannelItem>()
            for (m in allMasterChannels) {
                map[m.name.lowercase()] = m
            }
            for (c in FocusShield.CURATED_GROWTH_CHANNELS) {
                val key = c.name.lowercase()
                if (key !in map) {
                    map[key] = MasterChannelItem(
                        name = c.name,
                        handleOrUrl = "https://www.youtube.com/${c.handle}",
                        assignedGroupIds = emptySet()
                    )
                }
            }
            map.values.sortedBy { it.name.lowercase() }
        }

        val visibleChannels = remember(combinedChannels, channelSearch, dialogFilter, targetGroup.id) {
            val bySearch = if (channelSearch.isBlank()) combinedChannels
            else combinedChannels.filter {
                it.name.contains(channelSearch, ignoreCase = true) ||
                    it.handleOrUrl.contains(channelSearch, ignoreCase = true)
            }
            when (dialogFilter) {
                "UNASSIGNED" -> bySearch.filter { it.assignedGroupIds.isEmpty() }
                "DOUBLE" -> bySearch.filter { it.assignedGroupIds.size >= 2 }
                "IN_THIS_GROUP" -> bySearch.filter { targetGroup.id in it.assignedGroupIds }
                else -> bySearch
            }
        }

        AlertDialog(
            onDismissRequest = { tickChannelsTargetGroup = null },
            title = {
                Column {
                    Text(
                        text = "☑️ '${targetGroup.name}' में चैनल्स जोड़ें (A–Z)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "पॉकेट-ट्यूब की तरह जिस चैनल पर टिक (☑) करेंगे, वह इस ग्रुप में शामिल हो जाएगा:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = channelSearch,
                        onValueChange = { channelSearch = it },
                        placeholder = { Text("A से Z चैनल्स में खोजें...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (channelSearch.isNotEmpty()) {
                                IconButton(onClick = { channelSearch = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Filter Chips inside Dialog (All / Unassigned / Double Group / In This Group)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = dialogFilter == "ALL",
                                onClick = { dialogFilter = "ALL" },
                                label = { Text("🔤 सभी (${combinedChannels.size})", fontSize = 11.sp) }
                            )
                        }
                        item {
                            val unassignedCnt = combinedChannels.count { it.assignedGroupIds.isEmpty() }
                            FilterChip(
                                selected = dialogFilter == "UNASSIGNED",
                                onClick = { dialogFilter = "UNASSIGNED" },
                                label = { Text("⚠️ Uncategorized ($unassignedCnt)", fontSize = 11.sp) }
                            )
                        }
                        item {
                            val doubleCnt = combinedChannels.count { it.assignedGroupIds.size >= 2 }
                            FilterChip(
                                selected = dialogFilter == "DOUBLE",
                                onClick = { dialogFilter = "DOUBLE" },
                                label = { Text("⚡ डबल ग्रुप ($doubleCnt)", fontSize = 11.sp) }
                            )
                        }
                        item {
                            val inThisCnt = combinedChannels.count { targetGroup.id in it.assignedGroupIds }
                            FilterChip(
                                selected = dialogFilter == "IN_THIS_GROUP",
                                onClick = { dialogFilter = "IN_THIS_GROUP" },
                                label = { Text("✅ इस ग्रुप में ($inThisCnt)", fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↕ ${visibleChannels.size} चैनल्स दिखाए जा रहे हैं",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                tickChannelsTargetGroup = null
                                channelTargetGroupId = targetGroup.id
                            }
                        ) {
                            Text("+ URL/Handle से जोड़ें", fontSize = 11.sp)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = visibleChannels,
                            key = { it.name.lowercase() }
                        ) { item ->
                            val isChecked = targetGroup.id in item.assignedGroupIds
                            val itemGroups = groups.filter { it.id in item.assignedGroupIds }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onToggleChannelInGroup(item.name, item.handleOrUrl, targetGroup.id, !isChecked)
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            onToggleChannelInGroup(item.name, item.handleOrUrl, targetGroup.id, checked)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            ChannelGroupBadges(assignedGroups = itemGroups)
                                        }
                                        if (itemGroups.size >= 2) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "⚡ डबल ग्रुप (${itemGroups.size} ग्रुप्स में शामिल)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF6A1B9A),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { tickChannelsTargetGroup = null }) {
                    Text("Done / सेव हो गया")
                }
            }
        )
    }

    // =========================================================================
    // DIALOG 2: POCKETTUBE-STYLE "TICK GROUPS FOR A SELECTED CHANNEL"
    // =========================================================================
    selectedChannelForGroupTick?.let { targetChannel ->
        // Keep live state of this channel's assigned groups from allMasterChannels
        val liveChannel = allMasterChannels.find { it.name.equals(targetChannel.name, ignoreCase = true) } ?: targetChannel
        var newGroupNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedChannelForGroupTick = null },
            title = {
                Column {
                    Text(
                        text = "📺 ${liveChannel.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "इस चैनल को जिन-जिन ग्रुप्स में रखना चाहते हैं उन पर टिक (☑) करें:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (groups.isEmpty()) {
                        Text(
                            text = "अभी कोई ग्रुप नहीं बना है। नीचे नाम लिखकर तुरंत ग्रुप बनाएं:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(groups, key = { it.id }) { grp ->
                                val isAssigned = grp.id in liveChannel.assignedGroupIds
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onToggleChannelInGroup(
                                                liveChannel.name,
                                                liveChannel.handleOrUrl,
                                                grp.id,
                                                !isAssigned
                                            )
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAssigned) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isAssigned,
                                            onCheckedChange = { checked ->
                                                onToggleChannelInGroup(
                                                    liveChannel.name,
                                                    liveChannel.handleOrUrl,
                                                    grp.id,
                                                    checked
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = grp.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isAssigned) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Create Group inline
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newGroupNameInput,
                            onValueChange = { newGroupNameInput = it },
                            placeholder = { Text("+ नया ग्रुप नाम...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                if (newGroupNameInput.isNotBlank()) {
                                    onCreateGroup(newGroupNameInput.trim())
                                    newGroupNameInput = ""
                                }
                            },
                            enabled = newGroupNameInput.isNotBlank(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("बनाएं")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedChannelForGroupTick = null }) {
                    Text("Done")
                }
            }
        )
    }

    // Dialog to Create Group
    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Channel Group") },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name *") },
                    placeholder = { Text("e.g. Farming, Business, Coding") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            onCreateGroup(groupName)
                            showCreateGroupDialog = false
                        }
                    },
                    enabled = groupName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog to Add Channel to Group (Real URL & Handle support) with Focus Shield Curated Directory
    channelTargetGroupId?.let { targetGroupId ->
        var channelInput by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf("All") }
        val categories = remember {
            listOf(
                "All",
                "Business Strategy",
                "Marketing",
                "Branding",
                "E-Commerce",
                "Product Rank",
                "Farming",
                "Manufacturing",
                "Business",
                "Coding",
                "Science",
                "Productivity"
            )
        }
        val filteredCurated = remember(selectedCategory) {
            if (selectedCategory == "All") FocusShield.CURATED_GROWTH_CHANNELS
            else FocusShield.CURATED_GROWTH_CHANNELS.filter { it.category == selectedCategory }
        }

        AlertDialog(
            onDismissRequest = { channelTargetGroupId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add Channel")
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2E7D32)
                    ) {
                        Text(
                            text = "🛡️ SHIELD ON",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = channelInput,
                        onValueChange = { channelInput = it },
                        label = { Text("Channel URL or @handle *") },
                        placeholder = { Text("e.g. @khanacademy or @freecodecamp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (searchHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SearchHistoryChips(
                            historyList = searchHistory,
                            onSelectQuery = { channelInput = it },
                            onDeleteItem = onDeleteSearchHistoryItem,
                            onClearAll = onClearSearchHistory
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "✨ Verified Growth Channels (1-Tap Add):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredCurated.forEach { curated ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    channelInput = curated.handle
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "+",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = curated.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (channelInput.isNotBlank()) {
                            onAddChannel(channelInput.trim(), targetGroupId)
                            channelTargetGroupId = null
                        }
                    },
                    enabled = channelInput.isNotBlank()
                ) {
                    Text("Fetch & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { channelTargetGroupId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation
    groupToDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("Delete '${g.name}'?") },
            text = { Text("Are you sure you want to delete this channel group?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteGroup(g.id)
                        groupToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun YouTubeAccountChannelsSyncBanner(
    userProfile: GoogleUserProfile,
    isSyncing: Boolean,
    totalChannelsCount: Int,
    onSyncYouTubeAccount: () -> Unit,
    onOpenGoogleAccount: () -> Unit
) {
    val hasSession = remember(userProfile) {
        userProfile.isYouTubeAutoSaveActive || YouTubeSyncHelper.hasYouTubeWebSession()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (hasSession) Icons.Default.CheckCircle else Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (hasSession) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "पॉकेट-ट्यूब (PocketTube) मोड • कुल $totalChannelsCount चैनल्स",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "अपने YouTube अकाउंट के सभी Subscribed चैनल्स (A–Z) लाएं और टिक (☑) करके ग्रुप्स में रखें।",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_youtube_subscriptions_button")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("YouTube से सारे चैनल्स व प्लेलिस्ट आ रहे हैं...", fontSize = 12.sp)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasSession || userProfile.isSignedIn) {
                            "🔄 मेरे सारे Subscribed Channels व Playlists (A–Z) सिंक करें"
                        } else {
                            "🔗 YouTube लॉगिन करें व सारे चैनल्स (A–Z) लाएं"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
