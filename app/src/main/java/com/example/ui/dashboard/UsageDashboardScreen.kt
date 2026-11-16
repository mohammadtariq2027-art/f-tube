package com.example.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity
import com.example.ui.components.AppHeaderSyncStatusBar
import com.example.ui.components.CategoryMostLeastSummary
import com.example.ui.components.DailyZoneUsage
import com.example.ui.components.EntityWatchStat
import com.example.ui.components.StartupReminderManager
import com.example.ui.components.VisualScreenTimerManager
import com.example.ui.components.WatchEntityType
import com.example.util.GoogleUserProfile
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageDashboardScreen(
    allSavedVideos: List<VideoEntity> = emptyList(),
    allPlaylists: List<PlaylistEntity> = emptyList(),
    allGroups: List<SubscriptionGroupEntity> = emptyList(),
    allChannels: List<ChannelEntity> = emptyList(),
    groupsCount: Int = 0,
    channelsCount: Int = 0,
    playlistsCount: Int = 0,
    isSyncing: Boolean = false,
    userProfile: GoogleUserProfile = GoogleUserProfile(),
    onOpenGoogleAccount: () -> Unit = {},
    onOpenBackupDialog: () -> Unit = {},
    onSyncAllNowWithAccount: () -> Unit = {},
    onOpenStartupReminderScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        VisualScreenTimerManager.ensureInitialized(context)
        VisualScreenTimerManager.refreshWeeklyUsage(context)
        StartupReminderManager.ensureLoaded(context)
    }

    val elapsedSeconds by VisualScreenTimerManager.elapsedSecondsFlow.collectAsState()
    val weeklyUsage by VisualScreenTimerManager.weeklyUsageFlow.collectAsState()
    val reminderText by StartupReminderManager.reminderTextFlow.collectAsState()

    // Refresh weekly usage whenever elapsedSeconds changes
    LaunchedEffect(elapsedSeconds) {
        if (weeklyUsage.isEmpty() || elapsedSeconds % 2L == 0L) {
            VisualScreenTimerManager.refreshWeeklyUsage(context)
        }
    }

    var selectedDayIndex by remember(weeklyUsage.size) {
        mutableIntStateOf((weeklyUsage.size - 1).coerceAtLeast(0))
    }

    var selectedAnalyticsType by remember { mutableStateOf(WatchEntityType.CHANNEL) }
    var sortLeastFirst by remember { mutableStateOf(false) }

    val entityAnalyticsMap = remember(allSavedVideos, allPlaylists, allGroups, allChannels, elapsedSeconds / 3L) {
        VisualScreenTimerManager.computeEntityWatchAnalytics(
            context = context,
            allSavedVideos = allSavedVideos,
            allPlaylists = allPlaylists,
            allGroups = allGroups,
            allChannels = allChannels
        )
    }

    val totalWeeklyGreen = remember(weeklyUsage) { weeklyUsage.sumOf { it.greenSeconds } }
    val totalWeeklyYellow = remember(weeklyUsage) { weeklyUsage.sumOf { it.yellowSeconds } }
    val totalWeeklyRed = remember(weeklyUsage) { weeklyUsage.sumOf { it.redSeconds } }
    val totalWeeklyAll = remember(totalWeeklyGreen, totalWeeklyYellow, totalWeeklyRed) {
        totalWeeklyGreen + totalWeeklyYellow + totalWeeklyRed
    }

    val greenPct = remember(totalWeeklyGreen, totalWeeklyAll) {
        if (totalWeeklyAll > 0L) ((totalWeeklyGreen * 100.0) / totalWeeklyAll).roundToInt() else 100
    }
    val yellowPct = remember(totalWeeklyYellow, totalWeeklyAll) {
        if (totalWeeklyAll > 0L) ((totalWeeklyYellow * 100.0) / totalWeeklyAll).roundToInt() else 0
    }
    val redPct = remember(totalWeeklyRed, totalWeeklyAll) {
        if (totalWeeklyAll > 0L) (100 - greenPct - yellowPct).coerceIn(0, 100) else 0
    }

    val uniqueVideos = remember(allSavedVideos) { allSavedVideos.distinctBy { it.videoId } }
    val completedVideosCount = remember(uniqueVideos) { uniqueVideos.count { it.isCompleted } }
    val videosWithNotesCount = remember(uniqueVideos) { uniqueVideos.count { it.notes.isNotBlank() } }

    val currentPalette = remember(elapsedSeconds) {
        VisualScreenTimerManager.getPaletteForSeconds(elapsedSeconds)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("usage_dashboard_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Focus & Usage Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "पिछले 7 दिनों का Green • Yellow • Red ज़ोन विश्लेषण",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenStartupReminderScreen,
                    modifier = Modifier.testTag("dashboard_open_reminder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Open Fullscreen Goal Reminder",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onOpenBackupDialog,
                    modifier = Modifier.testTag("dashboard_backup_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = "Backup & Restore"
                    )
                }
                IconButton(
                    onClick = onOpenGoogleAccount,
                    modifier = Modifier.testTag("dashboard_google_account_button")
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

        // Persistent App Header Visual Timer & Sync Status Bar
        AppHeaderSyncStatusBar(
            userProfile = userProfile,
            isSyncing = isSyncing,
            allSavedVideos = allSavedVideos,
            groupsCount = groupsCount,
            playlistsCount = playlistsCount,
            onTriggerSyncAndBackupNow = onSyncAllNowWithAccount,
            onOpenGoogleAccountDialog = onOpenGoogleAccount,
            onOpenBackupRestoreDialog = onOpenBackupDialog,
            onOpenReminderScreen = onOpenStartupReminderScreen
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // === 1. WEEKLY TOTAL & 3-ZONE DONUT BREAKDOWN CARD ===
            item(key = "weekly_summary_hero_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weekly_usage_summary_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1722))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.2.dp,
                                color = currentPalette.primaryGlowColor.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "📊 पिछले 7 दिनों का कुल ऐप समय (Weekly Total)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF90CAF9)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = VisualScreenTimerManager.formatDurationHms(totalWeeklyAll),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.testTag("weekly_total_time_text")
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = currentPalette.badgeBgColor,
                                modifier = Modifier.border(
                                    width = 1.dp,
                                    color = currentPalette.primaryGlowColor.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            ) {
                                Text(
                                    text = currentPalette.zoneShortBadge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = currentPalette.textColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Donut Ring + 3 Zone Breakdown Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Multi-Segment Donut Chart
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(116.dp)
                            ) {
                                Canvas(modifier = Modifier.size(104.dp)) {
                                    val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Butt)
                                    val total = max(1L, totalWeeklyAll).toFloat()
                                    val gSweep = (totalWeeklyGreen / total) * 360f
                                    val ySweep = (totalWeeklyYellow / total) * 360f
                                    val rSweep = (totalWeeklyRed / total) * 360f

                                    if (totalWeeklyAll == 0L) {
                                        drawArc(
                                            color = Color(0xFF00E676).copy(alpha = 0.35f),
                                            startAngle = -90f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = stroke
                                        )
                                    } else {
                                        var start = -90f
                                        if (gSweep > 0f) {
                                            drawArc(
                                                color = Color(0xFF00E676),
                                                startAngle = start,
                                                sweepAngle = gSweep,
                                                useCenter = false,
                                                style = stroke
                                            )
                                            start += gSweep
                                        }
                                        if (ySweep > 0f) {
                                            drawArc(
                                                color = Color(0xFFFFEA00),
                                                startAngle = start,
                                                sweepAngle = ySweep,
                                                useCenter = false,
                                                style = stroke
                                            )
                                            start += ySweep
                                        }
                                        if (rSweep > 0f) {
                                            drawArc(
                                                color = Color(0xFFFF1744),
                                                startAngle = start,
                                                sweepAngle = rSweep,
                                                useCenter = false,
                                                style = stroke
                                            )
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$greenPct%",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF00E676)
                                    )
                                    Text(
                                        text = "Safe Focus",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB0BEC5)
                                    )
                                }
                            }

                            // 3 Zone Cards (Green, Yellow, Red)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ZoneStatRow(
                                    dotColor = Color(0xFF00E676),
                                    bgColor = Color(0xFF062312),
                                    label = "🟢 Green Zone (0–40m)",
                                    durationText = VisualScreenTimerManager.formatDurationCompact(totalWeeklyGreen),
                                    pctText = "$greenPct%",
                                    testTag = "weekly_green_zone_stat"
                                )
                                ZoneStatRow(
                                    dotColor = Color(0xFFFFEA00),
                                    bgColor = Color(0xFF2B2103),
                                    label = "🟡 Yellow Zone (40m–2h)",
                                    durationText = VisualScreenTimerManager.formatDurationCompact(totalWeeklyYellow),
                                    pctText = "$yellowPct%",
                                    testTag = "weekly_yellow_zone_stat"
                                )
                                ZoneStatRow(
                                    dotColor = Color(0xFFFF1744),
                                    bgColor = Color(0xFF3A040C),
                                    label = "🔴 Red Danger (2h+)",
                                    durationText = VisualScreenTimerManager.formatDurationCompact(totalWeeklyRed),
                                    pctText = "$redPct%",
                                    testTag = "weekly_red_zone_stat"
                                )
                            }
                        }
                    }
                }
            }

            // === 2. 7-DAY INTERACTIVE STACKED BAR CHART ===
            item(key = "weekly_bar_chart_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weekly_stacked_bar_chart_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "📅 पिछले 7 दिनों का डेली ज़ोन ग्राफ (Past 7 Days Breakdown)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "किसी भी दिन के बार पर टैप करके उस दिन का Green, Yellow और Red समय देखें",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val maxDaySeconds = remember(weeklyUsage) {
                            max(3600L, weeklyUsage.maxOfOrNull { it.totalSeconds } ?: 3600L)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklyUsage.forEachIndexed { index, day ->
                                val isSelected = index == selectedDayIndex
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedDayIndex = index }
                                        .padding(horizontal = 3.dp)
                                        .testTag("weekly_chart_bar_$index")
                                ) {
                                    Text(
                                        text = if (day.totalSeconds > 0L) {
                                            VisualScreenTimerManager.formatDurationCompact(day.totalSeconds)
                                        } else "0m",
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Stacked Bar (Red on top, Yellow middle, Green bottom)
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(104.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            ),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val fullH = size.height
                                            val w = size.width
                                            val maxSec = maxDaySeconds.toFloat()

                                            val gH = ((day.greenSeconds / maxSec) * fullH).coerceAtLeast(if (day.greenSeconds > 0) 4.dp.toPx() else 0f)
                                            val yH = ((day.yellowSeconds / maxSec) * fullH).coerceAtLeast(if (day.yellowSeconds > 0) 4.dp.toPx() else 0f)
                                            val rH = ((day.redSeconds / maxSec) * fullH).coerceAtLeast(if (day.redSeconds > 0) 4.dp.toPx() else 0f)

                                            var currentBottom = fullH
                                            if (gH > 0f) {
                                                val segH = gH.coerceAtMost(currentBottom)
                                                drawRect(
                                                    color = Color(0xFF00C853),
                                                    topLeft = Offset(0f, currentBottom - segH),
                                                    size = Size(w, segH)
                                                )
                                                currentBottom -= segH
                                            }
                                            if (yH > 0f && currentBottom > 0f) {
                                                val segH = yH.coerceAtMost(currentBottom)
                                                drawRect(
                                                    color = Color(0xFFFFD600),
                                                    topLeft = Offset(0f, currentBottom - segH),
                                                    size = Size(w, segH)
                                                )
                                                currentBottom -= segH
                                            }
                                            if (rH > 0f && currentBottom > 0f) {
                                                val segH = rH.coerceAtMost(currentBottom)
                                                drawRect(
                                                    color = Color(0xFFFF1744),
                                                    topLeft = Offset(0f, currentBottom - segH),
                                                    size = Size(w, segH)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = if (day.isToday) "आज" else day.dayShortLabel.take(3),
                                        fontSize = 10.sp,
                                        fontWeight = if (day.isToday || isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (day.isToday) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Selected Day Spotlight Banner
                        weeklyUsage.getOrNull(selectedDayIndex)?.let { selDay ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${selDay.dayShortLabel} (${selDay.dateDisplayLabel}): कुल ${VisualScreenTimerManager.formatDurationCompact(selDay.totalSeconds)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "🟢 ${VisualScreenTimerManager.formatDurationCompact(selDay.greenSeconds)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "🟡 ${VisualScreenTimerManager.formatDurationCompact(selDay.yellowSeconds)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF57F17)
                                        )
                                        Text(
                                            text = "🔴 ${VisualScreenTimerManager.formatDurationCompact(selDay.redSeconds)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD50000)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // === 2B. MOST vs LEAST TIME SPENT ANALYTICS (CHANNELS • PLAYLISTS • GROUPS) ===
            item(key = "entity_most_least_analytics_card") {
                EntityMostLeastTimeAnalyticsCard(
                    entityAnalyticsMap = entityAnalyticsMap,
                    selectedType = selectedAnalyticsType,
                    onSelectType = { selectedAnalyticsType = it },
                    sortLeastFirst = sortLeastFirst,
                    onToggleSortOrder = { sortLeastFirst = !sortLeastFirst }
                )
            }

            // === 3. FULLSCREEN STARTUP REMINDER PREVIEW & EDIT CARD ===
            item(key = "startup_reminder_dashboard_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStartupReminderScreen() }
                        .testTag("dashboard_startup_reminder_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1D31))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📌 ऐप खुलते ही दिखने वाला फुल-स्क्रीन रिमाइंडर",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E676)
                            )
                            FilledTonalButton(
                                onClick = onOpenStartupReminderScreen,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF00C853),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("फुल स्क्रीन में खोलें / लिखें", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = reminderText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // === 4. DAY-BY-DAY DETAILED LOG (PAST 7 DAYS) ===
            item(key = "daily_log_header") {
                Text(
                    text = "🗓️ दिन-प्रतिदिन का ज़ोन रिकॉर्ड (Past 7 Days Log)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            itemsIndexed(
                items = weeklyUsage.reversed(),
                key = { _, item -> "day_row_${item.dateKey}" }
            ) { _, day ->
                DailyUsageBreakdownRow(day = day)
            }

            // === 5. STUDY & WORK LIBRARY OVERVIEW ===
            item(key = "library_overview_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "📚 आपकी पढ़ाई और फोकस लाइब्रेरी",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("📁 ग्रुप्स: $groupsCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("📺 चैनल्स: $channelsCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("🎵 प्लेलिस्ट: $playlistsCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🎬 सेव वीडियो: ${uniqueVideos.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("✅ पूरे देखे गए: $completedVideosCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("📝 सीन नोट्स: $videosWithNotesCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneStatRow(
    dotColor: Color,
    bgColor: Color,
    label: String,
    durationText: String,
    pctText: String,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, dotColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = dotColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$durationText ($pctText)",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DailyUsageBreakdownRow(
    day: DailyZoneUsage
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (day.isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${day.dayShortLabel} • ${day.dateDisplayLabel}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "कुल समय: ${VisualScreenTimerManager.formatDurationHms(day.totalSeconds)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF062312)
                ) {
                    Text(
                        text = "🟢 ${VisualScreenTimerManager.formatDurationCompact(day.greenSeconds)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF2B2103)
                ) {
                    Text(
                        text = "🟡 ${VisualScreenTimerManager.formatDurationCompact(day.yellowSeconds)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFEA00),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF3A040C)
                ) {
                    Text(
                        text = "🔴 ${VisualScreenTimerManager.formatDurationCompact(day.redSeconds)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Analytical section displaying which YouTube Channel, Playlist, or Group has the MOST time spent
 * and which Playlist, Group, or Channel has the LEAST time spent, with interactive category rankings.
 */
@Composable
private fun EntityMostLeastTimeAnalyticsCard(
    entityAnalyticsMap: Map<WatchEntityType, CategoryMostLeastSummary>,
    selectedType: WatchEntityType,
    onSelectType: (WatchEntityType) -> Unit,
    sortLeastFirst: Boolean,
    onToggleSortOrder: () -> Unit
) {
    val channelSummary = entityAnalyticsMap[WatchEntityType.CHANNEL]
    val playlistSummary = entityAnalyticsMap[WatchEntityType.PLAYLIST]
    val groupSummary = entityAnalyticsMap[WatchEntityType.GROUP]
    val activeSummary = entityAnalyticsMap[selectedType]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_entity_time_analytics_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1621))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Header
            Column {
                Text(
                    text = "📈 सबसे ज़्यादा vs सबसे कम टाइम स्पेंड एनालिटिक्स",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF81D4FA)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "किस YouTube चैनल, प्लेलिस्ट और ग्रुप पर सबसे ज़्यादा और सबसे कम समय बिताया गया है:",
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5)
                )
            }

            // 3-Row Most vs Least Comparison Grid (1. Channel, 2. Playlist, 3. Group)
            MostVsLeastComparisonRow(
                categoryLabel = "📺 YouTube चैनल",
                mostItem = channelSummary?.mostSpent,
                leastItem = channelSummary?.leastSpent,
                isSelected = selectedType == WatchEntityType.CHANNEL,
                onClick = { onSelectType(WatchEntityType.CHANNEL) },
                testTagPrefix = "analytics_summary_channel"
            )
            MostVsLeastComparisonRow(
                categoryLabel = "🎵 प्लेलिस्ट (Playlist)",
                mostItem = playlistSummary?.mostSpent,
                leastItem = playlistSummary?.leastSpent,
                isSelected = selectedType == WatchEntityType.PLAYLIST,
                onClick = { onSelectType(WatchEntityType.PLAYLIST) },
                testTagPrefix = "analytics_summary_playlist"
            )
            MostVsLeastComparisonRow(
                categoryLabel = "📁 चैनल ग्रुप (Group)",
                mostItem = groupSummary?.mostSpent,
                leastItem = groupSummary?.leastSpent,
                isSelected = selectedType == WatchEntityType.GROUP,
                onClick = { onSelectType(WatchEntityType.GROUP) },
                testTagPrefix = "analytics_summary_group"
            )

            // Interactive Category Tabs + Most/Least Sort Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnalyticsCategoryTabPill(
                        label = "📺 चैनल्स (${channelSummary?.rankedAll?.size ?: 0})",
                        selected = selectedType == WatchEntityType.CHANNEL,
                        onClick = { onSelectType(WatchEntityType.CHANNEL) },
                        testTag = "analytics_tab_channels"
                    )
                    AnalyticsCategoryTabPill(
                        label = "🎵 प्लेलिस्ट (${playlistSummary?.rankedAll?.size ?: 0})",
                        selected = selectedType == WatchEntityType.PLAYLIST,
                        onClick = { onSelectType(WatchEntityType.PLAYLIST) },
                        testTag = "analytics_tab_playlists"
                    )
                    AnalyticsCategoryTabPill(
                        label = "📁 ग्रुप्स (${groupSummary?.rankedAll?.size ?: 0})",
                        selected = selectedType == WatchEntityType.GROUP,
                        onClick = { onSelectType(WatchEntityType.GROUP) },
                        testTag = "analytics_tab_groups"
                    )
                }
            }

            // Detailed Ranked Breakdown Header + Sort Order Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${activeSummary?.categoryTitleHindi.orEmpty()} का पूरा टाइम डेटा:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCFD8DC)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .clickable { onToggleSortOrder() }
                        .testTag("analytics_sort_order_toggle")
                ) {
                    Text(
                        text = if (sortLeastFirst) "❄️ कम से ज़्यादा (Least First) ⇅" else "🔥 ज़्यादा से कम (Most First) ⇅",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sortLeastFirst) Color(0xFF80DEEA) else Color(0xFFFFB74D),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            val itemsList = remember(activeSummary, sortLeastFirst) {
                val base = activeSummary?.rankedAll.orEmpty()
                if (sortLeastFirst) base.reversed() else base
            }
            val maxItemSec = remember(activeSummary) {
                max(1L, activeSummary?.mostSpent?.timeSpentSeconds ?: 1L)
            }

            if (itemsList.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "इस सेक्शन में अभी कोई डेटा उपलब्ध नहीं है। जैसे ही आप चैनल, प्लेलिस्ट या ग्रुप जोड़ेंगे, यहाँ सबसे ज़्यादा और सबसे कम टाइम स्पेंड का पूरा विश्लेषण दिखेगा।",
                        fontSize = 11.sp,
                        color = Color(0xFFB0BEC5),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    itemsList.take(8).forEachIndexed { idx, stat ->
                        val isMost = stat.idKey == activeSummary?.mostSpent?.idKey
                        val isLeast = itemsList.size >= 2 && stat.idKey == activeSummary?.leastSpent?.idKey
                        val barRatio = (stat.timeSpentSeconds.toFloat() / maxItemSec.toFloat()).coerceIn(0.06f, 1f)
                        val barColor = when {
                            isMost -> Color(0xFF00E676)
                            isLeast -> Color(0xFFFF8A80)
                            else -> Color(0xFF4FC3F7)
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF16202C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isMost || isLeast) 1.dp else 0.5.dp,
                                    color = barColor.copy(alpha = if (isMost || isLeast) 0.65f else 0.25f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .testTag("analytics_ranked_row_${stat.idKey}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = barColor.copy(alpha = 0.20f)
                                        ) {
                                            Text(
                                                text = when {
                                                    isMost -> "🔥 सर्वाधिक (MOST)"
                                                    isLeast -> "❄️ न्यूनतम (LEAST)"
                                                    else -> "#${idx + 1}"
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = barColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stat.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${VisualScreenTimerManager.formatDurationCompact(stat.timeSpentSeconds)} (${stat.sharePercent}%)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = barColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = stat.subtitle,
                                    fontSize = 10.sp,
                                    color = Color(0xFF90A4AE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(5.dp))

                                // Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = 0.10f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(barRatio)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(barColor)
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

@Composable
private fun MostVsLeastComparisonRow(
    categoryLabel: String,
    mostItem: EntityWatchStat?,
    leastItem: EntityWatchStat?,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTagPrefix: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF172738) else Color(0xFF131D2A),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 1.2.dp else 0.6.dp,
                color = if (isSelected) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag(testTagPrefix)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = categoryLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF81D4FA)
                )
                Text(
                    text = "विस्तार से देखें ➔",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90CAF9)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Most Time Spent Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF062312),
                    modifier = Modifier
                        .weight(1f)
                        .border(0.8.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("${testTagPrefix}_most")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "🔥 सबसे ज़्यादा टाइम",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E676)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mostItem?.title ?: "—",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (mostItem != null) {
                                "${VisualScreenTimerManager.formatDurationCompact(mostItem.timeSpentSeconds)} (${mostItem.sharePercent}%)"
                            } else "0m",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF69F0AE)
                        )
                    }
                }

                // Least Time Spent Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2B1216),
                    modifier = Modifier
                        .weight(1f)
                        .border(0.8.dp, Color(0xFFFF8A80).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("${testTagPrefix}_least")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "❄️ सबसे कम टाइम",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF8A80)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = leastItem?.title ?: "—",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (leastItem != null) {
                                "${VisualScreenTimerManager.formatDurationCompact(leastItem.timeSpentSeconds)} (${leastItem.sharePercent}%)"
                            } else "0m",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFCCBC)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCategoryTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF0288D1) else Color(0xFF1E293B),
        modifier = Modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

