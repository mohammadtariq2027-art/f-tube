package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptchaenterprise.ReCaptchaEnterpriseProviderFactory

private const val TAG = "AppCheck"

private const val DEBUG_APP_CHECK_PREFS = "com.google.firebase.appcheck.debug.store"
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

/**
 * Configures Firebase App Check before any Firebase AI Logic call.
 *
 * Uses DebugAppCheckProviderFactory if a debug token was passed via intent extras,
 * and falls back to ReCaptchaEnterpriseProviderFactory otherwise.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    val debugToken = intent?.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")

    val factory = if (!debugToken.isNullOrEmpty()) {
        Log.i(TAG, "Debug token found in Intent extras — using DebugAppCheckProviderFactory.")
        val prefsName = "$DEBUG_APP_CHECK_PREFS.${Firebase.appCheck.firebaseApp.persistenceKey}"
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(DEBUG_SECRET_KEY, debugToken)
            .commit()
        DebugAppCheckProviderFactory.getInstance()
    } else {
        Log.i(TAG, "No debug token in Intent extras — using ReCaptchaEnterpriseProviderFactory.")
        val siteKey = context.getString(R.string.recaptcha_site_key)
        ReCaptchaEnterpriseProviderFactory.getInstance(siteKey)
    }

    Firebase.appCheck.installAppCheckProviderFactory(factory)
}
