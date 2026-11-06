package com.example.util

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.data.local.dao.FocusTubeDao
import com.example.data.local.entity.AppPreferenceEntity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GoogleUserProfile(
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isSignedIn: Boolean = false,
    val isNightlySyncEnabled: Boolean = true,
    val lastSyncTime: String = "रात को ऑटो-सिंक सक्रिय (Scheduled)"
)

class GoogleAccountManager(private val dao: FocusTubeDao) {

    private val _userProfile = MutableStateFlow(GoogleUserProfile())
    val userProfile: StateFlow<GoogleUserProfile> = _userProfile.asStateFlow()

    companion object {
        private const val TAG = "GoogleAccountManager"
        private const val KEY_EMAIL = "google_user_email"
        private const val KEY_NAME = "google_user_name"
        private const val KEY_PHOTO = "google_user_photo"
        private const val KEY_SIGNED_IN = "google_is_signed_in"
        private const val KEY_NIGHTLY_SYNC = "google_nightly_sync_enabled"
        private const val KEY_LAST_SYNC = "google_last_sync_time"
    }

    /**
     * Permanent session restore: Called on app launch.
     * Keeps the user permanently signed in (auto-login) without asking for login again.
     */
    suspend fun restoreSession() {
        withContext(Dispatchers.IO) {
            try {
                val isSignedIn = dao.getPreferenceSnapshot(KEY_SIGNED_IN) == "true"
                val email = dao.getPreferenceSnapshot(KEY_EMAIL).orEmpty()
                val name = dao.getPreferenceSnapshot(KEY_NAME).orEmpty()
                val photo = dao.getPreferenceSnapshot(KEY_PHOTO).orEmpty()
                val nightlySync = dao.getPreferenceSnapshot(KEY_NIGHTLY_SYNC) != "false" // default true
                val lastSync = dao.getPreferenceSnapshot(KEY_LAST_SYNC) ?: "रात को ऑटो-सिंक सक्रिय (10:00 PM)"

                if (isSignedIn && email.isNotBlank()) {
                    _userProfile.value = GoogleUserProfile(
                        email = email,
                        displayName = name.ifBlank { email.substringBefore("@") },
                        photoUrl = photo,
                        isSignedIn = true,
                        isNightlySyncEnabled = nightlySync,
                        lastSyncTime = lastSync
                    )
                    Log.d(TAG, "Permanent Google session auto-restored for $email")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore Google session: ${e.message}")
            }
        }
    }

    /**
     * Signs in using Android Credential Manager (Native Google Play Services).
     */
    suspend fun signInWithCredentialManager(context: Context): Result<GoogleUserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setServerClientId("682499840492-google-client.apps.googleusercontent.com")
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val email = googleIdTokenCredential.id
                    val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                    val photoUrl = googleIdTokenCredential.profilePictureUri?.toString().orEmpty()

                    saveUserProfile(email, displayName, photoUrl)
                    return@withContext Result.success(_userProfile.value)
                } else {
                    return@withContext Result.failure(Exception("Could not extract Google credentials"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Credential Manager native sign-in: ${e.message}")
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Save user profile permanently to Room and update state.
     */
    suspend fun saveUserProfile(email: String, displayName: String, photoUrl: String = "") {
        withContext(Dispatchers.IO) {
            val cleanEmail = email.trim()
            val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@") }
            val nowStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

            dao.setPreference(AppPreferenceEntity(KEY_EMAIL, cleanEmail))
            dao.setPreference(AppPreferenceEntity(KEY_NAME, cleanName))
            dao.setPreference(AppPreferenceEntity(KEY_PHOTO, photoUrl))
            dao.setPreference(AppPreferenceEntity(KEY_SIGNED_IN, "true"))
            dao.setPreference(AppPreferenceEntity(KEY_LAST_SYNC, "सिंक हुआ: $nowStr"))

            _userProfile.value = GoogleUserProfile(
                email = cleanEmail,
                displayName = cleanName,
                photoUrl = photoUrl,
                isSignedIn = true,
                isNightlySyncEnabled = _userProfile.value.isNightlySyncEnabled,
                lastSyncTime = "सिंक हुआ: $nowStr"
            )
        }
    }

    /**
     * Toggle nightly auto-sync setting.
     */
    suspend fun setNightlySync(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            dao.setPreference(AppPreferenceEntity(KEY_NIGHTLY_SYNC, enabled.toString()))
            _userProfile.value = _userProfile.value.copy(isNightlySyncEnabled = enabled)
        }
    }

    /**
     * Update last sync time.
     */
    suspend fun updateLastSyncTime(timeStr: String) {
        withContext(Dispatchers.IO) {
            dao.setPreference(AppPreferenceEntity(KEY_LAST_SYNC, timeStr))
            _userProfile.value = _userProfile.value.copy(lastSyncTime = timeStr)
        }
    }

    /**
     * Signs out manually.
     */
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            dao.setPreference(AppPreferenceEntity(KEY_SIGNED_IN, "false"))
            _userProfile.value = GoogleUserProfile(isSignedIn = false)
        }
    }
}
