package com.example.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.SearchHistoryEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.ui.components.SearchHistoryChips
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupsScreen(
    groups: List<SubscriptionGroupEntity>,
    pinnedGroup: SubscriptionGroupEntity?,
    searchHistory: List<SearchHistoryEntity> = emptyList(),
    getChannelsForGroup: (Long) -> Flow<List<ChannelEntity>>,
    onSelectPinnedGroup: (Long) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onAddChannel: (String, Long) -> Unit,
    onDeleteChannel: (Long) -> Unit,
    onMoveChannel: (Long, Long) -> Unit,
    onOpenAddVideoToGroup: (Long) -> Unit,
    onDeleteSearchHistoryItem: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onOpenBackupDialog: () -> Unit = {}
) {
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var channelTargetGroupId by remember { mutableStateOf<Long?>(null) }
    var groupToDelete by remember { mutableStateOf<SubscriptionGroupEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = "Channel Groups",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenBackupDialog,
                        modifier = Modifier.testTag("backup_button_groups")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup & Restore Data"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Channel Groups Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create groups to organize your subscribed YouTube channels and watch without distractions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showCreateGroupDialog = true }) {
                            Text("+ Create New Group")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        val isPinned = pinnedGroup?.id == group.id
                        val channelsState = remember(group.id) { getChannelsForGroup(group.id) }
                        val channels by channelsState.collectAsState(initial = emptyList())

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_card_${group.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Header: Group Title + Delete
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

                                Spacer(modifier = Modifier.height(10.dp))

                                // Channels in this group
                                Text(
                                    text = "Channels in group (${channels.size}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                if (channels.isEmpty()) {
                                    Text(
                                        text = "No channels added yet. Tap '+ Add Channel' to paste a channel link or handle.",
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

                                            Box {
                                                InputChip(
                                                    selected = false,
                                                    onClick = { showMoveMenu = true },
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

                                                // Move Channel Menu
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
                                                        text = { Text("Delete Channel", color = MaterialTheme.colorScheme.error) },
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
                    placeholder = { Text("e.g. Science, Coding, Music") },
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

    // Dialog to Add Channel to Group (Real URL & Handle support)
    channelTargetGroupId?.let { targetGroupId ->
        var channelInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { channelTargetGroupId = null },
            title = { Text("Add Channel to Group") },
            text = {
                Column {
                    Text(
                        text = "Paste a YouTube Channel Link, @handle, or Channel ID. The app will automatically fetch the channel and its videos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = channelInput,
                        onValueChange = { channelInput = it },
                        label = { Text("YouTube Channel URL or @handle *") },
                        placeholder = { Text("e.g. @khanacademy or https://youtube.com/@veritasium") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (searchHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SearchHistoryChips(
                            historyList = searchHistory,
                            onSelectQuery = { channelInput = it },
                            onDeleteItem = onDeleteSearchHistoryItem,
                            onClearAll = onClearSearchHistory
                        )
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
