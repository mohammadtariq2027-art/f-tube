package com.example.ui.components

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.GoogleAccountManager
import com.example.util.GoogleUserProfile
import com.example.util.YouTubeSyncHelper
import kotlinx.coroutines.launch

@Composable
fun GoogleAccountDialog(
    userProfile: GoogleUserProfile,
    isSyncing: Boolean = false,
    onDismiss: () -> Unit,
    onGoogleAccountPicked: (String) -> Unit,
    onYouTubeSessionConnected: (String) -> Unit = {},
    onLinkGoogleDriveFile: (Uri) -> Unit,
    onRestoreFromGoogleDriveFile: (Uri) -> Unit,
    onSignOut: () -> Unit,
    onToggleNightlySync: (Boolean) -> Unit,
    onSyncAllNow: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showYouTubeWebLoginDialog by remember { mutableStateOf(false) }

    // OAuth Consent Intent Launcher (if Android AccountManager requests user permission for YouTube scope)
    val oauthConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && userProfile.email.isNotBlank()) {
            scope.launch {
                val (token, _) = YouTubeSyncHelper.requestYouTubeOAuthToken(context, userProfile.email)
                if (!token.isNullOrBlank()) {
                    onYouTubeSessionConnected(token)
                }
            }
        }
    }

    // 1. Official Android System Google Account Picker Launcher
    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                onGoogleAccountPicked(selectedEmail)
                // Also immediately attempt to obtain YouTube OAuth token for auto-saving playlists
                scope.launch {
                    val (token, consentIntent) = YouTubeSyncHelper.requestYouTubeOAuthToken(context, selectedEmail)
                    if (!token.isNullOrBlank()) {
                        onYouTubeSessionConnected(token)
                    } else if (consentIntent != null) {
                        try {
                            oauthConsentLauncher.launch(consentIntent)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    // 2. Google Drive Persistent Auto-Backup File Creator (SAF)
    val createDriveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            onLinkGoogleDriveFile(uri)
        }
    }

    // 3. Google Drive Backup Restore Picker (SAF)
    val openDriveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onRestoreFromGoogleDriveFile(uri)
        }
    }

    val isDriveLinked = userProfile.googleDriveBackupUri.isNotBlank()
    val isYouTubeAutoSaveActive = userProfile.isYouTubeAutoSaveActive || YouTubeSyncHelper.hasYouTubeWebSession()

    if (showYouTubeWebLoginDialog) {
        YouTubeAutoSaveLoginDialog(
            initialEmail = userProfile.email,
            onDismiss = { showYouTubeWebLoginDialog = false },
            onSessionConnected = {
                showYouTubeWebLoginDialog = false
                onYouTubeSessionConnected("")
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (userProfile.isSignedIn) "Google & YouTube Auto-Save" else "Google अकाउंट लॉगिन",
                    style = MaterialTheme.typography.titleLarge,
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
                if (userProfile.isSignedIn) {
                    // === SIGNED-IN STATE ===
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userProfile.displayName.take(1).uppercase().ifBlank { "G" },
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = userProfile.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1B5E20).copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "स्थायी रूप से कनेक्टेड (Official Google Account)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                } else {
                    // === NOT SIGNED-IN STATE (Official System Account Picker Only) ===
                    Text(
                        text = "अपने फोन में लॉगिन किए हुए असली Google अकाउंट को 1 टैप में चुनें:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("✓ फोन के असली Google Account Picker से लॉगिन", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("✓ असली YouTube प्लेलिस्ट में वीडियो ऑटो-सेव", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("✓ रात को वीडियो + नोट्स का Google Drive ऑटो-बैकअप", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            try {
                                googleAccountPickerLauncher.launch(GoogleAccountManager.createChooseGoogleAccountIntent())
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Google अकाउंट चुनें (Sign in with Google) ↗", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // === YOUTUBE ORIGINAL PLAYLIST AUTO-SAVE CARD ===
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isYouTubeAutoSaveActive) Color(0xFF1B5E20).copy(alpha = 0.14f)
                        else Color(0xFFE65100).copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isYouTubeAutoSaveActive) Icons.Default.CheckCircle else Icons.Default.PlaylistAddCheck,
                                contentDescription = null,
                                tint = if (isYouTubeAutoSaveActive) Color(0xFF4CAF50) else Color(0xFFFF6D00),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ओरिजिनल YouTube प्लेलिस्ट Auto-Save",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isYouTubeAutoSaveActive) {
                                "✅ सक्रिय! अब आप इस ऐप में किसी भी YouTube प्लेलिस्ट (PL...) में वीडियो सेव करेंगे, तो वह अपने-आप आपके असली YouTube ऐप की ओरिजिनल प्लेलिस्ट में भी तुरंत सेव हो जाएगी।"
                            } else {
                                "⚠️ केवल Google ईमेल चुनने से YouTube अपनी असली प्लेलिस्ट बदलने की अनुमति नहीं देता। अपनी ओरिजिनल YouTube प्लेलिस्ट में ऑटो-सेव चालू करने के लिए नीचे बटन दबाकर 1 बार YouTube सेशन कनेक्ट करें:"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                showYouTubeWebLoginDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connect_youtube_autosave_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isYouTubeAutoSaveActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isYouTubeAutoSaveActive) "✅ YouTube Auto-Save कनेक्टेड (रीफ्रेश करें)" else "🔗 YouTube ओरिजिनल प्लेलिस्ट Auto-Save चालू करें",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // === GOOGLE DRIVE AUTO-BACKUP CARD ===
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDriveLinked) Color(0xFF1B5E20).copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDriveLinked) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = if (isDriveLinked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Drive ऑटो-बैकअप (वीडियो + नोट्स)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isDriveLinked) {
                                "✓ आपकी Google Drive बैकअप फ़ाइल लिंक है। रोज़ रात को सारे ग्रुप्स, प्लेलिस्ट और हर वीडियो के नोट्स सीधे Google Drive में सेव होते हैं।"
                            } else {
                                "अपनी Google Drive में ऑटो-बैकअप फ़ाइल सेट करें ताकि फोन बदलने या ऐप डिलीट होने पर भी सारा डेटा और नोट्स सुरक्षित रहें।"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = {
                                createDriveBackupLauncher.launch("FocusTube_GoogleDrive_Backup.json")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("link_google_drive_backup_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isDriveLinked) "☁️ Google Drive फ़ाइल बदलें / अपडेट करें" else "☁️ Google Drive में ऑटो-बैकअप सेट करें",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                openDriveBackupLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("restore_google_drive_backup_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📂 Google Drive से बैकअप रीस्टोर करें", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // === NIGHTLY AUTO-SYNC TOGGLE ===
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Nightlight,
                                    contentDescription = null,
                                    tint = Color(0xFFFFA000),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "रात को ऑटो-सिंक व बैकअप",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = userProfile.isNightlySyncEnabled,
                                onCheckedChange = onToggleNightlySync,
                                modifier = Modifier.testTag("toggle_nightly_sync")
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "रोज़ रात को आपकी सभी प्लेलिस्ट, ग्रुप्स, चैनल्स और वीडियो के साथ लिंक किए गए नोट्स ऑटोमैटिक बैकअप व YouTube से सिंक हो जाते हैं।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = userProfile.lastSyncTime,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSyncAllNow,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_all_now_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("सिंक व बैकअप हो रहा है...")
                    } else {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("अभी तुरंत सिंक व बैकअप करें")
                    }
                }

                if (userProfile.isSignedIn) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("अकाउंट से लॉगआउट करें (Sign Out)")
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

/**
 * 1-Time In-App YouTube Account Login Dialog:
 * Allows the user to sign in once to YouTube (`accounts.google.com` -> `m.youtube.com`).
 * As soon as YouTube sets the authenticated `SAPISID` cookie in Android's CookieManager,
 * it flushes the cookie to persistent storage and automatically closes, enabling 100%
 * background auto-save to real YouTube playlists (`PL...`)!
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeAutoSaveLoginDialog(
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onSessionConnected: () -> Unit
) {
    val loginUrl = remember(initialEmail) {
        val base = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fm.youtube.com%2F"
        if (initialEmail.isNotBlank() && initialEmail.contains("@")) {
            "$base&Email=${Uri.encode(initialEmail.trim())}"
        } else {
            base
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "YouTube ओरिजिनल प्लेलिस्ट Auto-Save लॉगिन",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "सिर्फ 1 बार लॉगिन करें — इसके बाद हर वीडियो अपने-आप YouTube प्लेलिस्ट में सेव होगी",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (YouTubeSyncHelper.hasYouTubeWebSession()) {
                                        CookieManager.getInstance().flush()
                                        onSessionConnected()
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    CookieManager.getInstance().flush()
                                    if (YouTubeSyncHelper.hasYouTubeWebSession()) {
                                        onSessionConnected()
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    return false
                                }
                            }

                            loadUrl(loginUrl)
                        }
                    }
                )
            }
        }
    }
}
