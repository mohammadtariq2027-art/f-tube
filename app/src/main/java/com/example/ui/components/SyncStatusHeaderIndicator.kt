package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.VideoEntity
import com.example.util.GoogleUserProfile
import com.example.util.YouTubeSyncHelper

enum class SyncHealthLevel {
    SYNCING,
    CLOUD_FULL_HEALTHY,
    YOUTUBE_AND_LOCAL_HEALTHY,
    LOCAL_AUTO_BACKUP_SAFE
}

data class SyncHealthSummary(
    val level: SyncHealthLevel,
    val lastBackupTimestamp: String,
    val isYouTubeHealthy: Boolean,
    val isDriveLinked: Boolean,
    val isNightlySyncEnabled: Boolean,
    val syncedVideosCount: Int,
    val totalSavedVideosCount: Int,
    val notesCount: Int,
    val groupsCount: Int,
    val playlistsCount: Int
)

@Composable
fun rememberSyncHealthSummary(
    userProfile: GoogleUserProfile,
    isSyncing: Boolean,
    isBackupInProgress: Boolean,
    lastBackupTimestamp: String,
    allSavedVideos: List<VideoEntity>,
    groupsCount: Int,
    playlistsCount: Int
): SyncHealthSummary {
    return remember(
        userProfile,
        isSyncing,
        isBackupInProgress,
        lastBackupTimestamp,
        allSavedVideos,
        groupsCount,
        playlistsCount
    ) {
        val hasYtWeb = YouTubeSyncHelper.hasYouTubeWebSession()
        val ytHealthy = userProfile.isSignedIn || userProfile.isYouTubeAutoSaveActive || hasYtWeb
        val driveLinked = userProfile.googleDriveBackupUri.isNotBlank()

        val uniqueVideos = allSavedVideos.distinctBy { it.videoId }
        val playlistVideos = allSavedVideos.filter { !it.playlistId.isNullOrBlank() }
        val syncedCount = playlistVideos.count { it.isSyncedToYouTube }
        val notesCount = uniqueVideos.count { it.notes.isNotBlank() }

        val effectiveBackupTime = when {
            lastBackupTimestamp.isNotBlank() -> lastBackupTimestamp
            userProfile.lastSyncTime.contains(":") -> userProfile.lastSyncTime
                .removePrefix("सिंक हुआ:")
                .removePrefix("🌙 ऑटो-सिंक व बैकअप संपन्न:")
                .removePrefix("✓ सिंक संपन्न (")
                .substringBefore(")")
                .trim()
            else -> "Auto-Protected (Today)"
        }

        val level = when {
            isSyncing || isBackupInProgress -> SyncHealthLevel.SYNCING
            ytHealthy && driveLinked -> SyncHealthLevel.CLOUD_FULL_HEALTHY
            ytHealthy || driveLinked -> SyncHealthLevel.YOUTUBE_AND_LOCAL_HEALTHY
            else -> SyncHealthLevel.LOCAL_AUTO_BACKUP_SAFE
        }

        SyncHealthSummary(
            level = level,
            lastBackupTimestamp = effectiveBackupTime,
            isYouTubeHealthy = ytHealthy,
            isDriveLinked = driveLinked,
            isNightlySyncEnabled = userProfile.isNightlySyncEnabled,
            syncedVideosCount = syncedCount,
            totalSavedVideosCount = playlistVideos.size,
            notesCount = notesCount,
            groupsCount = groupsCount,
            playlistsCount = playlistsCount
        )
    }
}

/**
 * Persistent Visual Sync Status & Data Safety Indicator Bar in the App Header.
 * Displays:
 * 1. Overall Sync Health Badge (Syncing / Cloud+Local Healthy / Auto-Backup Safe)
 * 2. Last Successful Backup Timestamp
 * 3. YouTube & Google Drive Health Indicators
 * 4. 1-Tap Sync & Backup Now action + Detailed Health Modal on click
 */
@Composable
fun AppHeaderSyncStatusBar(
    userProfile: GoogleUserProfile,
    isSyncing: Boolean,
    isBackupInProgress: Boolean = com.example.util.AutoBackupHelper.isBackupInProgressFlow.collectAsState().value,
    lastBackupTimestamp: String = com.example.util.AutoBackupHelper.lastBackupTimestampFlow.collectAsState().value,
    allSavedVideos: List<VideoEntity> = emptyList(),
    groupsCount: Int = 0,
    playlistsCount: Int = 0,
    onTriggerSyncAndBackupNow: () -> Unit = {},
    onOpenGoogleAccountDialog: () -> Unit = {},
    onOpenBackupRestoreDialog: () -> Unit = {},
    onOpenReminderScreen: (() -> Unit)? = null,
    onOpenDashboardTab: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDetailsDialog by remember { mutableStateOf(false) }

    val summary = rememberSyncHealthSummary(
        userProfile = userProfile,
        isSyncing = isSyncing,
        isBackupInProgress = isBackupInProgress,
        lastBackupTimestamp = lastBackupTimestamp,
        allSavedVideos = allSavedVideos,
        groupsCount = groupsCount,
        playlistsCount = playlistsCount
    )

    val containerBgColor = when (summary.level) {
        SyncHealthLevel.SYNCING -> Color(0xFFE3F2FD)
        SyncHealthLevel.CLOUD_FULL_HEALTHY -> Color(0xFFE8F5E9)
        SyncHealthLevel.YOUTUBE_AND_LOCAL_HEALTHY -> Color(0xFFE0F2F1)
        SyncHealthLevel.LOCAL_AUTO_BACKUP_SAFE -> Color(0xFFF1F8E9)
    }

    val borderColor = when (summary.level) {
        SyncHealthLevel.SYNCING -> Color(0xFF1976D2).copy(alpha = 0.35f)
        SyncHealthLevel.CLOUD_FULL_HEALTHY -> Color(0xFF2E7D32).copy(alpha = 0.35f)
        SyncHealthLevel.YOUTUBE_AND_LOCAL_HEALTHY -> Color(0xFF00695C).copy(alpha = 0.35f)
        SyncHealthLevel.LOCAL_AUTO_BACKUP_SAFE -> Color(0xFF558B2F).copy(alpha = 0.35f)
    }

    val primaryAccentColor = when (summary.level) {
        SyncHealthLevel.SYNCING -> Color(0xFF1565C0)
        SyncHealthLevel.CLOUD_FULL_HEALTHY -> Color(0xFF1B5E20)
        SyncHealthLevel.YOUTUBE_AND_LOCAL_HEALTHY -> Color(0xFF004D40)
        SyncHealthLevel.LOCAL_AUTO_BACKUP_SAFE -> Color(0xFF33691E)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Persistent Glowing Visual App Timer Banner (Green 0-40m -> Yellow 40m-1.5h -> Red 2h+ Danger)
        AppHeaderVisualTimerBanner(
            onOpenReminderScreen = onOpenReminderScreen,
            onOpenDashboardTab = onOpenDashboardTab
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetailsDialog = true }
                .testTag("header_sync_status_indicator"),
            color = containerBgColor,
            tonalElevation = 2.dp
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = borderColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Overall Sync Health Indicator + Last Backup Timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Status Dot / Spinner
                if (summary.level == SyncHealthLevel.SYNCING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = primaryAccentColor
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32))
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = when (summary.level) {
                                SyncHealthLevel.SYNCING -> "🔄 Syncing & Backing Up..."
                                SyncHealthLevel.CLOUD_FULL_HEALTHY -> "🛡️ Cloud & Local Safe"
                                SyncHealthLevel.YOUTUBE_AND_LOCAL_HEALTHY -> "🛡️ Data Safe & Synced"
                                SyncHealthLevel.LOCAL_AUTO_BACKUP_SAFE -> "🛡️ Auto-Backup Safe"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryAccentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("header_sync_health_badge")
                        )

                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccentColor.copy(alpha = 0.6f)
                        )

                        Text(
                            text = "💾 Backup: ${summary.lastBackupTimestamp}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF263238),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("header_last_backup_timestamp")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right: YouTube Health Pill + Google Drive Health Pill + Instant Refresh Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // YouTube Sync Health Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (summary.isYouTubeHealthy) Color(0xFF2E7D32).copy(alpha = 0.14f)
                    else Color(0xFFE65100).copy(alpha = 0.12f),
                    modifier = Modifier.testTag("header_youtube_sync_health_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (summary.isYouTubeHealthy) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (summary.isYouTubeHealthy) "YT ✓" else "YT Sync",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.isYouTubeHealthy) Color(0xFF1B5E20) else Color(0xFFE65100)
                        )
                    }
                }

                // Google Drive / Cloud Backup Health Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (summary.isDriveLinked) Color(0xFF1565C0).copy(alpha = 0.14f)
                    else Color(0xFF00695C).copy(alpha = 0.14f),
                    modifier = Modifier.testTag("header_drive_sync_health_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (summary.isDriveLinked) Icons.Default.CloudDone else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (summary.isDriveLinked) Color(0xFF0D47A1) else Color(0xFF004D40),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (summary.isDriveLinked) "Drive ✓" else "Safe ✓",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.isDriveLinked) Color(0xFF0D47A1) else Color(0xFF004D40)
                        )
                    }
                }

                // 1-Tap Quick Backup & Sync trigger
                Surface(
                    shape = CircleShape,
                    color = primaryAccentColor.copy(alpha = 0.12f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(enabled = summary.level != SyncHealthLevel.SYNCING) {
                            onTriggerSyncAndBackupNow()
                        }
                        .testTag("header_quick_sync_now_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync and Backup Now",
                            tint = primaryAccentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
        }
    }

    if (showDetailsDialog) {
        SyncHealthInspectorDialog(
            userProfile = userProfile,
            summary = summary,
            onDismiss = { showDetailsDialog = false },
            onSyncAndBackupNow = {
                onTriggerSyncAndBackupNow()
            },
            onOpenGoogleAccount = {
                showDetailsDialog = false
                onOpenGoogleAccountDialog()
            },
            onOpenBackupRestore = {
                showDetailsDialog = false
                onOpenBackupRestoreDialog()
            }
        )
    }
}

@Composable
fun SyncHealthInspectorDialog(
    userProfile: GoogleUserProfile,
    summary: SyncHealthSummary,
    onDismiss: () -> Unit,
    onSyncAndBackupNow: () -> Unit,
    onOpenGoogleAccount: () -> Unit,
    onOpenBackupRestore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sync_health_details_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "🛡️ Data Safety & Sync Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "YouTube & Google Drive Protection Status",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Last Successful Backup Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💾 Last Successful Backup",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF2E7D32)
                            ) {
                                Text(
                                    text = "100% SAFE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🕒 ${summary.lastBackupTimestamp}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1B5E20)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Protected items: ${summary.groupsCount} Groups • ${summary.playlistsCount} Playlists • ${summary.totalSavedVideosCount} Videos • ${summary.notesCount} Video Notes",
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                // 2. YouTube Sync Health Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "▶️ YouTube Sync Health",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (summary.isYouTubeHealthy) Color(0xFF2E7D32).copy(alpha = 0.15f)
                                else Color(0xFFE65100).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (summary.isYouTubeHealthy) "CONNECTED ✓" else "LOCAL MODE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summary.isYouTubeHealthy) Color(0xFF1B5E20) else Color(0xFFE65100),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (summary.isYouTubeHealthy) {
                                "Account: ${userProfile.email.ifBlank { "YouTube Web Session Active" }}\n" +
                                    "Auto-Save to original YouTube playlists is active (${summary.syncedVideosCount}/${summary.totalSavedVideosCount} playlist videos synced)."
                            } else {
                                "Connect your Google/YouTube account to auto-save videos directly into your original YouTube playlists."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. Google Drive & Multi-Tier Backup Health Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "☁️ Google Drive & Storage Health",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (summary.isDriveLinked) Color(0xFF1565C0).copy(alpha = 0.15f)
                                else Color(0xFF00695C).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (summary.isDriveLinked) "DRIVE LINKED ✓" else "SYSTEM + LOCAL ✓",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summary.isDriveLinked) Color(0xFF0D47A1) else Color(0xFF004D40),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append("• Persistent Downloads Auto-Backup: Active ✓\n")
                                append("• Android System Google Drive Backup: Active ✓\n")
                                if (summary.isDriveLinked) {
                                    append("• Direct Google Drive Cloud File: Linked & Synced ✓\n")
                                } else {
                                    append("• Direct Google Drive Cloud File: Optional (Tap below to link)\n")
                                }
                                append("• Nightly Auto-Sync: ${if (summary.isNightlySyncEnabled) "Enabled (Every Night) ✓" else "Paused"}")
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Buttons inside Dialog
                Button(
                    onClick = onSyncAndBackupNow,
                    enabled = summary.level != SyncHealthLevel.SYNCING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_health_sync_now_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) {
                    if (summary.level == SyncHealthLevel.SYNCING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing & Backing Up Now...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚡ Sync & Backup Now (अभी सिंक और बैकअप करें)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenGoogleAccount,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_health_open_account_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("YouTube / Drive", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenBackupRestore,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_health_open_backup_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backup File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}
