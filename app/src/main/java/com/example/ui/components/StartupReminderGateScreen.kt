package com.example.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the Fullscreen Startup Focus Reminder / Intention Note.
 * Persists whatever the user writes so that every time the app is opened,
 * their reminder is displayed in giant fullscreen text until they have read it.
 */
object StartupReminderManager {
    private const val PREFS_NAME = "focustube_startup_reminder_prefs"
    private const val KEY_REMINDER_TEXT = "fullscreen_reminder_text"
    private const val KEY_FONT_BOOST_LEVEL = "fullscreen_font_boost_level" // 0 = Auto Giant, 1 = Extra Giant, 2 = Medium
    private const val KEY_QUICK_CHIPS = "saved_quick_reminders"

    private const val DEFAULT_REMINDER_TEXT =
        "🎯 आज मुझे इस ऐप में सिर्फ अपने काम और पढ़ाई की वीडियो देखनी है और ज़रूरी सीन के नोट्स बनाने हैं!"

    private val _reminderTextFlow = MutableStateFlow(DEFAULT_REMINDER_TEXT)
    val reminderTextFlow: StateFlow<String> = _reminderTextFlow.asStateFlow()

    private val _fontBoostFlow = MutableStateFlow(0)
    val fontBoostFlow: StateFlow<Int> = _fontBoostFlow.asStateFlow()

    private val _savedQuickNotesFlow = MutableStateFlow<List<String>>(emptyList())
    val savedQuickNotesFlow: StateFlow<List<String>> = _savedQuickNotesFlow.asStateFlow()

    private var isLoaded = false

    fun ensureLoaded(context: Context) {
        if (!isLoaded) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedText = prefs.getString(KEY_REMINDER_TEXT, null)
            _reminderTextFlow.value = if (!savedText.isNullOrBlank()) savedText else DEFAULT_REMINDER_TEXT
            _fontBoostFlow.value = prefs.getInt(KEY_FONT_BOOST_LEVEL, 0)
            val rawChips = prefs.getString(KEY_QUICK_CHIPS, null)
            if (!rawChips.isNullOrBlank()) {
                _savedQuickNotesFlow.value = rawChips.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                _savedQuickNotesFlow.value = listOf(
                    "🎯 आज मुझे लेक्चर पूरा देख कर उसके सीन नोट्स बनाने हैं।",
                    "📚 परीक्षा की तैयारी: पहले Pinned Playlist के 2 वीडियो पूरे करने हैं।",
                    "⏳ सिर्फ 40 मिनट (हरे ज़ोन) के अंदर आज का टॉपिक खत्म करना है!"
                )
            }
            isLoaded = true
        }
    }

    fun saveReminderText(context: Context, newText: String) {
        ensureLoaded(context)
        val trimmed = newText.trim().ifBlank { DEFAULT_REMINDER_TEXT }
        _reminderTextFlow.value = trimmed
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = _savedQuickNotesFlow.value.toMutableList()
        if (trimmed !in currentList) {
            currentList.add(0, trimmed)
        }
        val updatedChips = currentList.take(6)
        _savedQuickNotesFlow.value = updatedChips
        prefs.edit()
            .putString(KEY_REMINDER_TEXT, trimmed)
            .putString(KEY_QUICK_CHIPS, updatedChips.joinToString("|||"))
            .apply()
    }

    fun cycleFontBoost(context: Context) {
        ensureLoaded(context)
        val next = (_fontBoostFlow.value + 1) % 3
        _fontBoostFlow.value = next
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FONT_BOOST_LEVEL, next).apply()
    }
}

/**
 * Fullscreen Startup Reminder / Focus Intention Gate Screen.
 * - Appears whenever the user opens the app.
 * - Displays the user's written text in HUGE, easy-to-read Fullscreen typography.
 * - Includes a few-second focus reading pause and NEVER disappears until the user has read it
 *   and tapped "✅ मैंने पढ़ लिया — ऐप खोलें".
 * - Allows writing/editing the reminder right on the fullscreen canvas at any time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartupReminderGateScreen(
    onConfirmReadAndDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        StartupReminderManager.ensureLoaded(context)
    }

    val savedReminderText by StartupReminderManager.reminderTextFlow.collectAsState()
    val fontBoostLevel by StartupReminderManager.fontBoostFlow.collectAsState()
    val savedQuickNotes by StartupReminderManager.savedQuickNotesFlow.collectAsState()

    var isEditing by remember { mutableStateOf(false) }
    var draftText by remember(savedReminderText) { mutableStateOf(savedReminderText) }

    // 3-second gentle reading focus countdown when the screen first opens so the user's eyes
    // naturally read the fullscreen reminder before dismissing. Screen STILL stays open after 3s
    // until the user explicitly taps "✅ मैंने पढ़ लिया — ऐप खोलें"!
    var readingCountdownSeconds by remember { mutableIntStateOf(3) }
    LaunchedEffect(isEditing) {
        if (!isEditing) {
            while (readingCountdownSeconds > 0) {
                delay(1000L)
                readingCountdownSeconds--
            }
        }
    }

    // Intercept system back button: if editing, exit edit mode; otherwise keep gate open until read
    BackHandler(enabled = true) {
        if (isEditing) {
            isEditing = false
            draftText = savedReminderText
        } else if (readingCountdownSeconds <= 0) {
            onConfirmReadAndDismiss()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "startupGateGlow")
    val borderGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderGlowAlpha"
    )

    // Dynamically compute giant fullscreen font size based on character count & user's font boost choice
    val baseFontSizeSp = remember(savedReminderText.length, fontBoostLevel) {
        val len = savedReminderText.length
        val autoSize = when {
            len <= 40 -> 40
            len <= 85 -> 34
            len <= 150 -> 28
            len <= 260 -> 24
            else -> 20
        }
        when (fontBoostLevel) {
            1 -> (autoSize + 6).coerceAtMost(48) // Extra Giant
            2 -> (autoSize - 4).coerceAtLeast(18) // Compact
            else -> autoSize
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF06101E),
                        Color(0xFF0B1D33),
                        Color(0xFF071422)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("startup_fullscreen_reminder_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // === TOP BAR: Title Badge + Font Size Booster + Edit Toggle ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF00E676).copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "📌 आज का मुख्य लक्ष्य / रिमाइंडर",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "जब तक आप इसे पढ़ न लें, यह स्क्रीन यहीं रुकी रहेगी",
                            fontSize = 11.sp,
                            color = Color(0xFF80CBC4)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Font Size Toggle Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clickable { StartupReminderManager.cycleFontBoost(context) }
                            .testTag("startup_reminder_font_size_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Change Text Size",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (fontBoostLevel) {
                                    1 -> "XXL बड़ा"
                                    2 -> "Normal"
                                    else -> "XL फुल"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                        }
                    }

                    // Edit / Write Button
                    FilledTonalButton(
                        onClick = {
                            if (isEditing) {
                                StartupReminderManager.saveReminderText(context, draftText)
                                isEditing = false
                            } else {
                                draftText = savedReminderText
                                isEditing = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isEditing) Color(0xFF00C853) else Color(0xFF1E88E5),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("startup_reminder_edit_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Save else Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isEditing) "सेव करें" else "✏️ लिखें / बदलें",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // === CENTER FULLSCREEN CANVAS: GIANT EASY-TO-READ REMINDER TEXT OR FULLSCREEN EDITOR ===
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F253E).copy(alpha = 0.92f),
                                Color(0xFF091728).copy(alpha = 0.96f)
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = if (isEditing) Color(0xFF42A5F5) else Color(0xFF00E676).copy(alpha = borderGlowAlpha),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable(enabled = !isEditing) {
                        draftText = savedReminderText
                        isEditing = true
                    }
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .testTag("startup_reminder_fullscreen_canvas"),
                contentAlignment = Alignment.Center
            ) {
                if (isEditing) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✍️ यहां अपना लक्ष्य या याद रखने वाली बात लिखें (फुल स्क्रीन पर बड़ा दिखेगा):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF90CAF9)
                            )
                            if (draftText.isNotEmpty()) {
                                Text(
                                    text = "साफ़ करें (Clear)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF8A80),
                                    modifier = Modifier
                                        .clickable { draftText = "" }
                                        .padding(4.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            placeholder = {
                                Text(
                                    text = "उदाहरण: आज मुझे Physics का Chapter 4 पूरा करना है और उसके सीन नोट्स बनाने हैं...",
                                    fontSize = 20.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 34.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .testTag("startup_reminder_text_input"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF06101E),
                                unfocusedContainerColor = Color(0xFF06101E),
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color(0xFF42A5F5).copy(alpha = 0.6f),
                                cursorColor = Color(0xFF00E676)
                            )
                        )

                        // Quick Saved Goal Templates
                        if (savedQuickNotes.isNotEmpty()) {
                            Text(
                                text = "⚡ आपके पिछले / सेव किए गए रिमाइंडर (1 टैप में चुनें):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB0BEC5)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                savedQuickNotes.forEach { preset ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier.clickable {
                                            draftText = preset
                                        }
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 11.sp,
                                            color = Color(0xFFE0F7FA),
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isEditing = false
                                    draftText = savedReminderText
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("रद्द करें (Cancel)", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    StartupReminderManager.saveReminderText(context, draftText)
                                    isEditing = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("startup_reminder_save_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00C853),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("सेव करें (Save)", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                } else {
                    // GIANT FULLSCREEN DISPLAY MODE — Maximum Legibility!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = savedReminderText,
                            fontSize = baseFontSizeSp.sp,
                            lineHeight = (baseFontSizeSp * 1.38f).sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .testTag("startup_reminder_giant_text")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.clickable {
                                draftText = savedReminderText
                                isEditing = true
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFF80CBC4),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "टेक्स्ट बदलने के लिए यहां टैप करें",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF80CBC4)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // === BOTTOM BAR: Reading Focus Countdown + Big "I Have Read It — Open App" Button ===
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (readingCountdownSeconds > 0 && !isEditing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockClock,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📖 कृपया पहले अपना लक्ष्य ध्यान से पढ़ें (${readingCountdownSeconds} सेकंड)...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (3 - readingCountdownSeconds) / 3f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00E676),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        if (isEditing) {
                            StartupReminderManager.saveReminderText(context, draftText)
                            isEditing = false
                        }
                        onConfirmReadAndDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("startup_reminder_confirm_read_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (readingCountdownSeconds > 0 && !isEditing) Color(0xFF2E7D32) else Color(0xFF00C853),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (readingCountdownSeconds > 0 && !isEditing) {
                            "✅ मैंने पढ़ लिया — ऐप खोलें (${readingCountdownSeconds}s)"
                        } else {
                            "✅ मैंने पढ़ लिया — अब नॉर्मल ऐप खोलें"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
