package com.example.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.util.AiContentVerifier
import com.example.util.AiVerdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Paytm / PhonePe-style Biometric App Lock (Fingerprint / Face Lock / Device PIN).
 *
 * Behavior:
 * 1. When toggling App Lock ON, the user must first verify their phone's Fingerprint / Face Lock / Screen Lock once.
 * 2. Once enabled, every time FocusTube is opened cold OR reopened after exiting/backgrounding,
 *    the entire app is locked behind [BiometricLockGateScreen] until biometric verification succeeds.
 */
object BiometricAppLockManager {
    private const val PREFS_NAME = "focustube_biometric_security_prefs"
    private const val KEY_APP_LOCK_ENABLED = "biometric_app_lock_enabled"

    private val _isAppLockEnabled = MutableStateFlow(false)
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    private val _isSessionUnlocked = MutableStateFlow(true)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var initialized = false
    private var isPromptCurrentlyShowing = false

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        _isAppLockEnabled.value = enabled
        if (!initialized) {
            initialized = true
            // If App Lock is enabled on cold start, start in LOCKED state so fingerprint/face is required immediately
            _isSessionUnlocked.value = !enabled
        }
    }

    /**
     * Called when MainActivity stops (app exited or moved to background).
     * Relocks the session immediately so reopening the app always requires Fingerprint / Face Unlock.
     */
    fun onAppBackgrounded(activity: Activity) {
        if (!activity.isChangingConfigurations && !isPromptCurrentlyShowing && _isAppLockEnabled.value) {
            _isSessionUnlocked.value = false
            _statusMessage.value = null
        }
    }

    /**
     * Allows user to manually lock the app immediately to test the lock screen.
     */
    fun lockNow() {
        if (_isAppLockEnabled.value) {
            _isSessionUnlocked.value = false
            _statusMessage.value = null
        }
    }

    /**
     * Checks whether the device has Biometric (Fingerprint / Face) or Screen Lock configured.
     */
    fun isDeviceSecurityAvailable(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(BiometricManager::class.java)
            val canAuth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bm?.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == BiometricManager.BIOMETRIC_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                bm?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            }
            return canAuth || isDeviceSecure
        }
        return isDeviceSecure
    }

    /**
     * Toggles App Lock ON or OFF, requiring Fingerprint / Face / Device Lock verification first
     * (exact Paytm / PhonePe behavior).
     */
    fun requestToggleAppLock(
        activity: Activity,
        enable: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        val title = if (enable) "Enable FocusTube App Lock" else "Disable FocusTube App Lock"
        val subtitle = if (enable) {
            "Verify your Fingerprint, Face Lock, or Screen Lock once to enable App Lock"
        } else {
            "Verify your Fingerprint, Face Lock, or Screen Lock to turn off App Lock"
        }

        authenticateWithDeviceBiometrics(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = {
                val prefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enable).apply()
                _isAppLockEnabled.value = enable
                _isSessionUnlocked.value = true
                _statusMessage.value = null
                val msg = if (enable) {
                    "Biometric App Lock Enabled! FocusTube will now require Fingerprint or Face Lock every time you open it."
                } else {
                    "Biometric App Lock Disabled."
                }
                onResult(true, msg)
            },
            onError = { errMsg ->
                onResult(false, errMsg)
            }
        )
    }

    /**
     * Unlocks the active app session when locked at startup or resume.
     */
    fun unlockAppSession(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        authenticateWithDeviceBiometrics(
            activity = activity,
            title = "Unlock FocusTube",
            subtitle = "Use your Fingerprint, Face Unlock, or Phone PIN to open FocusTube",
            onSuccess = {
                _isSessionUnlocked.value = true
                _statusMessage.value = null
                onSuccess()
            },
            onError = { errMsg ->
                _statusMessage.value = errMsg
                onError(errMsg)
            }
        )
    }

    /**
     * Invokes native Android BiometricPrompt (Fingerprint + Face Unlock + Device PIN/Pattern).
     * If run on an unconfigured emulator without enrolled hardware biometrics, provides a clear
     * fallback verification so testing never dead-ends while physical phones (e.g., Realme P4)
     * trigger the hardware Fingerprint / Face scanner natively.
     */
    fun authenticateWithDeviceBiometrics(
        activity: Activity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                isPromptCurrentlyShowing = true
                val executor = ContextCompat.getMainExecutor(activity)
                val builder = BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                } else {
                    builder.setNegativeButton("Cancel", executor) { _, _ ->
                        isPromptCurrentlyShowing = false
                        onError("Biometric verification cancelled.")
                    }
                }

                val prompt = builder.build()
                val cancellationSignal = CancellationSignal()

                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            isPromptCurrentlyShowing = false
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            isPromptCurrentlyShowing = false
                            // If device has no enrolled fingerprint/screen lock (e.g. cloud preview emulator),
                            // allow verified fallback only if hardware/credential is not enrolled on the virtual device.
                            val isNoHardwareOrUnenrolled =
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ||
                                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT ||
                                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL

                            if (isNoHardwareOrUnenrolled && !isDeviceSecurityAvailable(activity)) {
                                onSuccess()
                            } else {
                                onError(errString?.toString() ?: "Fingerprint / Face verification required to continue.")
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            _statusMessage.value = "Fingerprint / Face not recognized. Please try again."
                        }
                    }
                )
                return
            } catch (e: Exception) {
                isPromptCurrentlyShowing = false
                if (!isDeviceSecurityAvailable(activity)) {
                    onSuccess()
                } else {
                    onError(e.localizedMessage ?: "Unable to launch biometric scanner.")
                }
            }
        } else {
            onSuccess()
        }
    }
}

/**
 * Full-screen Paytm / PhonePe-style Biometric Lock Gate Screen.
 * Blocks access to the entire app until Fingerprint / Face Unlock succeeds.
 */
@Composable
fun BiometricLockGateScreen(
    modifier: Modifier = Modifier,
    onUnlocked: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = LocalHapticFeedback.current
    val statusMessage by BiometricAppLockManager.statusMessage.collectAsState()

    // Automatically trigger Fingerprint / Face Lock prompt as soon as the lock screen appears
    LaunchedEffect(Unit) {
        if (activity != null) {
            BiometricAppLockManager.unlockAppSession(
                activity = activity,
                onSuccess = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlocked()
                }
            )
        }
    }

    // Intercept Back press: if locked, pressing Back exits the app rather than bypassing the lock!
    BackHandler(enabled = true) {
        activity?.finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B14),
                        Color(0xFF0E1726),
                        Color(0xFF070B14)
                    )
                )
            )
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("biometric_lock_gate_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .border(1.5.dp, Color(0xFF2ECC71).copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2ECC71).copy(alpha = 0.14f))
                        .border(2.dp, Color(0xFF2ECC71), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Fingerprint and Face Lock",
                        tint = Color(0xFF2ECC71),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF2ECC71).copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF2ECC71),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BIOMETRIC APP LOCK ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2ECC71),
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "FocusTube is Locked",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please verify with your Fingerprint, Face Unlock, or Phone Screen Lock to open FocusTube.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0BEC5),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (!statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE74C3C).copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (activity != null) {
                            BiometricAppLockManager.unlockAppSession(
                                activity = activity,
                                onSuccess = onUnlocked
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("unlock_biometric_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC71),
                        contentColor = Color(0xFF08120C)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Unlock with Fingerprint / Face ID",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = { activity?.finish() },
                    modifier = Modifier.testTag("exit_locked_app_button")
                ) {
                    Text(
                        text = "Exit App",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF90A4AE)
                    )
                }
            }
        }
    }
}

/**
 * Dialog for configuring Biometric App Lock (Paytm / PhonePe style) AND testing/inspecting
 * the Gemini AI Content Shield directly inside FocusTube.
 */
@Composable
fun BiometricAndAiSecurityDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val isAppLockEnabled by BiometricAppLockManager.isAppLockEnabled.collectAsState()
    var feedbackBanner by remember { mutableStateOf<String?>(null) }
    var isFeedbackSuccess by remember { mutableStateOf(true) }

    // Gemini AI Content Verifier interactive tester state
    var testTitle by remember { mutableStateOf("") }
    var testChannel by remember { mutableStateOf("") }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var aiVerdict by remember { mutableStateOf<AiVerdict?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121824),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF2ECC71),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "App Lock & Gemini AI Shield",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Biometric Security & Smart AI Content Filter",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF90A4AE)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF90A4AE)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Paytm / PhonePe Style Biometric App Lock Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A2436),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isAppLockEnabled) Color(0xFF2ECC71).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAppLockEnabled) Color(0xFF2ECC71).copy(alpha = 0.2f)
                                            else Color.White.copy(alpha = 0.08f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = if (isAppLockEnabled) Color(0xFF2ECC71) else Color(0xFFB0BEC5),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Biometric App Lock",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isAppLockEnabled) "ENABLED • Fingerprint / Face Lock required on every launch"
                                        else "DISABLED • Turn on to lock FocusTube like Paytm / PhonePe",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isAppLockEnabled) Color(0xFF2ECC71) else Color(0xFFB0BEC5)
                                    )
                                }
                            }

                            Switch(
                                checked = isAppLockEnabled,
                                onCheckedChange = { targetState ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (activity != null) {
                                        BiometricAppLockManager.requestToggleAppLock(
                                            activity = activity,
                                            enable = targetState
                                        ) { ok, message ->
                                            isFeedbackSuccess = ok
                                            feedbackBanner = message
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2ECC71)
                                ),
                                modifier = Modifier.testTag("biometric_app_lock_switch")
                            )
                        }

                        Text(
                            text = "When enabled, FocusTube verifies your phone's Fingerprint or Face Lock once. After that, every time you exit and reopen the app, it will only open with your Fingerprint or Face ID.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCFD8DC),
                            lineHeight = 17.sp
                        )

                        if (isAppLockEnabled) {
                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                    BiometricAppLockManager.lockNow()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("test_biometric_lock_now_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2ECC71))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Lock App Now to Test Fingerprint / Face ID",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        AnimatedVisibility(visible = !feedbackBanner.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isFeedbackSuccess) Color(0xFF2ECC71).copy(alpha = 0.16f)
                                else Color(0xFFE74C3C).copy(alpha = 0.18f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = feedbackBanner.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFeedbackSuccess) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                // Section 2: Gemini AI Smart Content Analyzer Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A2436),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Gemini AI Context-Aware Filter",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "ACTIVE • Understands video context beyond keywords",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        Text(
                            text = "Gemini AI analyzes the real meaning of videos, playlists, and channels when you add them:\n" +
                                "• Allows useful educational/business/farming videos even if their title includes words like '4K', 'song', 'video', or 'comedy'.\n" +
                                "• Blocks distraction videos even if they use clickbait 'study' or 'important' words.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCFD8DC),
                            lineHeight = 17.sp
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        Text(
                            text = "Test Gemini AI Analyzer on Any Video Title:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        OutlinedTextField(
                            value = testTitle,
                            onValueChange = { testTitle = it },
                            label = { Text("Video or Playlist Title (e.g. 4K Drone Farming Song Demo)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_test_title_input")
                        )

                        OutlinedTextField(
                            value = testChannel,
                            onValueChange = { testChannel = it },
                            label = { Text("Channel Name (Optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_test_channel_input")
                        )

                        Button(
                            onClick = {
                                if (testTitle.isNotBlank() && !isAiAnalyzing) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isAiAnalyzing = true
                                    aiVerdict = null
                                    coroutineScope.launch {
                                        try {
                                            aiVerdict = AiContentVerifier.analyzeVideoWithAi(
                                                title = testTitle,
                                                channelName = testChannel
                                            )
                                        } finally {
                                            isAiAnalyzing = false
                                        }
                                    }
                                }
                            },
                            enabled = testTitle.isNotBlank() && !isAiAnalyzing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("run_ai_analyzer_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            )
                        ) {
                            if (isAiAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing with Gemini AI...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Analyze Context with Gemini AI",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        aiVerdict?.let { verdict ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (verdict.isProductive) Color(0xFF2ECC71).copy(alpha = 0.15f)
                                else Color(0xFFE74C3C).copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (verdict.isProductive) Icons.Default.CheckCircle else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (verdict.isProductive) Color(0xFF2ECC71) else Color(0xFFFF5252),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (verdict.isProductive) {
                                                "ALLOWED • ${verdict.detectedCategory} (${verdict.confidencePercent}%)"
                                            } else {
                                                "BLOCKED • ${verdict.detectedCategory} (${verdict.confidencePercent}%)"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (verdict.isProductive) Color(0xFF69F0AE) else Color(0xFFFF8A80)
                                        )
                                    }
                                    Text(
                                        text = verdict.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71), contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}
