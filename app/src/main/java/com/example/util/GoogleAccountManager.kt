package com.example.util

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import com.example.data.local.dao.FocusTubeDao
import com.example.data.local.entity.AppPreferenceEntity
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
    val lastSyncTime: String = "Nightly Auto-Sync Active (Scheduled)",
    val googleDriveBackupUri: String = "",
    val youtubeOAuthToken: String = "",
    val isYouTubeAutoSaveActive: Boolean = false
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
        const val KEY_DRIVE_BACKUP_URI = "google_drive_backup_uri"
        const val KEY_YOUTUBE_OAUTH_TOKEN = "google_youtube_oauth_token"
        const val KEY_YT_WEB_SESSION = "google_yt_web_session_connected"

        /**
         * Creates Android's official system Google Account Picker intent.
         * Works on all Android devices with Google Play Services without requiring manual typing.
         */
        @Suppress("DEPRECATION")
        fun createChooseGoogleAccountIntent(): Intent {
            return AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                false,
                null,
                null,
                null,
                null
            )
        }
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
                val lastSync = dao.getPreferenceSnapshot(KEY_LAST_SYNC) ?: "Nightly Auto-Sync Active (10:00 PM)"
                val driveUri = dao.getPreferenceSnapshot(KEY_DRIVE_BACKUP_URI).orEmpty()
                val oauthToken = dao.getPreferenceSnapshot(KEY_YOUTUBE_OAUTH_TOKEN).orEmpty()
                val savedWebSession = dao.getPreferenceSnapshot(KEY_YT_WEB_SESSION) == "true"
                val hasWebSession = if (savedWebSession) {
                    YouTubeSyncHelper.hasYouTubeWebSession(allowColdInit = true)
                } else {
                    false
                }
                val ytAutoSave = hasWebSession || oauthToken.isNotBlank()

                if ((isSignedIn && email.isNotBlank()) || hasWebSession) {
                    val effectiveEmail = email.ifBlank { "YouTube Connected Account" }
                    _userProfile.value = GoogleUserProfile(
                        email = effectiveEmail,
                        displayName = name.ifBlank { effectiveEmail.substringBefore("@") },
                        photoUrl = photo,
                        isSignedIn = true,
                        isNightlySyncEnabled = nightlySync,
                        lastSyncTime = lastSync,
                        googleDriveBackupUri = driveUri,
                        youtubeOAuthToken = oauthToken,
                        isYouTubeAutoSaveActive = ytAutoSave
                    )
                    Log.d(TAG, "Permanent Google session auto-restored for $effectiveEmail (ytAutoSave=$ytAutoSave)")
                } else {
                    _userProfile.value = GoogleUserProfile(
                        isSignedIn = false,
                        isNightlySyncEnabled = nightlySync,
                        lastSyncTime = lastSync,
                        googleDriveBackupUri = driveUri,
                        youtubeOAuthToken = oauthToken,
                        isYouTubeAutoSaveActive = ytAutoSave
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore Google session: ${e.message}")
            }
        }
    }

    /**
     * Save user profile permanently to Room after selecting from Official Google Account Picker.
     */
    suspend fun saveUserProfile(email: String, displayName: String = "", photoUrl: String = "", oauthToken: String = "") {
        withContext(Dispatchers.IO) {
            val cleanEmail = email.trim()
            val cleanName = displayName.trim().ifBlank {
                cleanEmail.substringBefore("@")
                    .replace(".", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            val nowStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
            val driveUri = dao.getPreferenceSnapshot(KEY_DRIVE_BACKUP_URI).orEmpty()
            val savedToken = oauthToken.ifBlank { dao.getPreferenceSnapshot(KEY_YOUTUBE_OAUTH_TOKEN).orEmpty() }
            val ytAutoSave = YouTubeSyncHelper.hasYouTubeWebSession() || savedToken.isNotBlank()

            dao.setPreference(AppPreferenceEntity(KEY_EMAIL, cleanEmail))
            dao.setPreference(AppPreferenceEntity(KEY_NAME, cleanName))
            dao.setPreference(AppPreferenceEntity(KEY_PHOTO, photoUrl))
            dao.setPreference(AppPreferenceEntity(KEY_SIGNED_IN, "true"))
            dao.setPreference(AppPreferenceEntity(KEY_LAST_SYNC, "Synced: $nowStr"))
            if (savedToken.isNotBlank()) {
                dao.setPreference(AppPreferenceEntity(KEY_YOUTUBE_OAUTH_TOKEN, savedToken))
            }

            _userProfile.value = GoogleUserProfile(
                email = cleanEmail,
                displayName = cleanName,
                photoUrl = photoUrl,
                isSignedIn = true,
                isNightlySyncEnabled = _userProfile.value.isNightlySyncEnabled,
                lastSyncTime = "Synced: $nowStr",
                googleDriveBackupUri = driveUri,
                youtubeOAuthToken = savedToken,
                isYouTubeAutoSaveActive = ytAutoSave
            )
        }
    }

    /**
     * Saves OAuth token or refreshes YouTube Web Session status so real YouTube playlists auto-save.
     */
    suspend fun updateYouTubeAutoSaveSession(oauthToken: String = "") {
        withContext(Dispatchers.IO) {
            if (oauthToken.isNotBlank()) {
                dao.setPreference(AppPreferenceEntity(KEY_YOUTUBE_OAUTH_TOKEN, oauthToken.trim()))
            }
            try {
                YouTubeSyncHelper.markCookieManagerReady()
                CookieManager.getInstance().flush()
            } catch (_: Exception) {}

            val savedToken = oauthToken.ifBlank { dao.getPreferenceSnapshot(KEY_YOUTUBE_OAUTH_TOKEN).orEmpty() }
            val hasWeb = YouTubeSyncHelper.hasYouTubeWebSession(allowColdInit = true)
            if (hasWeb) {
                dao.setPreference(AppPreferenceEntity(KEY_YT_WEB_SESSION, "true"))
            }
            val active = hasWeb || savedToken.isNotBlank()

            val currentEmail = _userProfile.value.email.ifBlank {
                dao.getPreferenceSnapshot(KEY_EMAIL).orEmpty().ifBlank {
                    if (hasWeb) "YouTube Account (Connected)" else ""
                }
            }
            if (active && currentEmail.isNotBlank()) {
                dao.setPreference(AppPreferenceEntity(KEY_SIGNED_IN, "true"))
                dao.setPreference(AppPreferenceEntity(KEY_EMAIL, currentEmail))
            }

            _userProfile.value = _userProfile.value.copy(
                email = currentEmail,
                displayName = _userProfile.value.displayName.ifBlank { currentEmail.substringBefore("@") },
                isSignedIn = _userProfile.value.isSignedIn || active,
                youtubeOAuthToken = savedToken,
                isYouTubeAutoSaveActive = active
            )
        }
    }

    /**
     * Saves a persistent Google Drive Document URI with read/write permissions
     * so Nightly Auto-Backup writes directly to Google Drive.
     */
    suspend fun saveGoogleDriveBackupUri(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                Log.w(TAG, "Persistable URI permission note: ${e.message}")
            }
            val uriStr = uri.toString()
            dao.setPreference(AppPreferenceEntity(KEY_DRIVE_BACKUP_URI, uriStr))
            _userProfile.value = _userProfile.value.copy(googleDriveBackupUri = uriStr)
        }
    }

    suspend fun getSavedDriveBackupUri(): String {
        return withContext(Dispatchers.IO) {
            dao.getPreferenceSnapshot(KEY_DRIVE_BACKUP_URI).orEmpty()
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
            dao.setPreference(AppPreferenceEntity(KEY_YOUTUBE_OAUTH_TOKEN, ""))
            dao.setPreference(AppPreferenceEntity(KEY_YT_WEB_SESSION, "false"))
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (_: Exception) {}
            _userProfile.value = _userProfile.value.copy(
                email = "",
                displayName = "",
                photoUrl = "",
                isSignedIn = false,
                youtubeOAuthToken = "",
                isYouTubeAutoSaveActive = false
            )
        }
    }
}
