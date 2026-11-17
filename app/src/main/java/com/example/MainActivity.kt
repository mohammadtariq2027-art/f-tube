package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.MainApp
import com.example.ui.components.BiometricAppLockManager
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase App Check before any Firebase AI Logic / ViewModel access
        configureAppCheck(this, intent)
        // Initialize Paytm/PhonePe-style Biometric App Lock state
        BiometricAppLockManager.init(this)

        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainApp(
                        initialSharedUrl = sharedUrl,
                        onClearSharedUrl = { sharedUrl = null }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Relock session when exiting or backgrounding the app so reopening requires Fingerprint / Face Lock
        BiometricAppLockManager.onAppBackgrounded(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                sharedUrl = text
            }
        }
    }
}
