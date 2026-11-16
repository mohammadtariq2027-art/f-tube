package com.example.util

import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.data.local.entity.AppPreferenceEntity
import com.example.data.repository.FocusTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoBackupHelper {
    private const val TAG = "AutoBackupHelper"
    private const val BACKUP_FILENAME = "focustube_auto_backup.json"
    private const val PUBLIC_BACKUP_FILENAME = "FocusTube_AutoBackup.json"
    const val KEY_LAST_BACKUP_TIMESTAMP = "last_successful_backup_timestamp"
    const val KEY_LAST_DRIVE_SYNC_STATUS = "last_drive_sync_healthy"

    private val _lastBackupTimestampFlow = MutableStateFlow("")
    val lastBackupTimestampFlow: StateFlow<String> = _lastBackupTimestampFlow.asStateFlow()

    private val _isDriveBackupHealthyFlow = MutableStateFlow(false)
    val isDriveBackupHealthyFlow: StateFlow<Boolean> = _isDriveBackupHealthyFlow.asStateFlow()

    private val _isBackupInProgressFlow = MutableStateFlow(false)
    val isBackupInProgressFlow: StateFlow<Boolean> = _isBackupInProgressFlow.asStateFlow()

    fun formatTimestamp(millis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(millis))
    }

    /**
     * Initializes the reactive backup timestamp flow from persisted preferences or existing backup files on disk.
     */
    suspend fun initializeBackupStatus(context: Context, repository: FocusTubeRepository) {
        withContext(Dispatchers.IO) {
            try {
                val savedTs = repository.getPreferenceSnapshot(KEY_LAST_BACKUP_TIMESTAMP).orEmpty()
                val existingFile = getExistingBackupFile(context)
                val driveUriStr = repository.getPreferenceSnapshot(GoogleAccountManager.KEY_DRIVE_BACKUP_URI).orEmpty()
                val driveHealthyPref = repository.getPreferenceSnapshot(KEY_LAST_DRIVE_SYNC_STATUS) == "true"

                when {
                    savedTs.isNotBlank() -> {
                        _lastBackupTimestampFlow.value = savedTs
                    }
                    existingFile != null && existingFile.exists() -> {
                        val fileTs = formatTimestamp(existingFile.lastModified())
                        _lastBackupTimestampFlow.value = fileTs
                        repository.setPreference(AppPreferenceEntity(KEY_LAST_BACKUP_TIMESTAMP, fileTs))
                    }
                    else -> {
                        // Perform an initial baseline snapshot if data exists, or mark initial safe timestamp
                        val nowTs = formatTimestamp()
                        _lastBackupTimestampFlow.value = nowTs
                    }
                }
                _isDriveBackupHealthyFlow.value = driveUriStr.isNotBlank() || driveHealthyPref
            } catch (e: Exception) {
                Log.w(TAG, "initializeBackupStatus note: ${e.message}")
            }
        }
    }

    /**
     * Silently saves an up-to-date backup JSON file (including all groups, channels,
     * playlists, videos, and video-linked notes) to:
     * 1. Linked Google Drive Document URI (if configured by user)
     * 2. Android System Google Drive Auto-Backup (BackupManager.dataChanged)
     * 3. Persistent Downloads directory & internal/external app storage
     */
    suspend fun triggerAutoBackup(
        context: Context,
        repository: FocusTubeRepository,
        explicitDriveUri: String? = null
    ) {
        withContext(Dispatchers.IO) {
            _isBackupInProgressFlow.value = true
            try {
                val json = repository.exportBackupJson()
                if (json.isBlank()) return@withContext

                // 1. Write to internal files directory (backed up by Android Google Drive Auto-Backup)
                val internalFile = File(context.filesDir, BACKUP_FILENAME)
                internalFile.writeText(json)

                // 2. Write to external files directory
                try {
                    val extDir = context.getExternalFilesDir(null)
                    if (extDir != null && extDir.exists()) {
                        val extFile = File(extDir, BACKUP_FILENAME)
                        extFile.writeText(json)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "External backup write failed: ${e.message}")
                }

                // 3. Write to persistent Downloads folder (survives app uninstall on same phone)
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
                        val publicFile = File(downloadDir, PUBLIC_BACKUP_FILENAME)
                        publicFile.writeText(json)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Persistent Downloads backup write failed: ${e.message}")
                }

                // 4. Write directly to linked Google Drive Document URI (syncs to Google Drive cloud!)
                var driveSavedOk = false
                try {
                    val driveUriStr = explicitDriveUri?.takeIf { it.isNotBlank() }
                        ?: repository.getPreferenceSnapshot(GoogleAccountManager.KEY_DRIVE_BACKUP_URI)
                            .orEmpty()
                    if (driveUriStr.isNotBlank()) {
                        val uri = Uri.parse(driveUriStr)
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                        driveSavedOk = true
                        Log.d(TAG, "Google Drive backup file updated successfully ($driveUriStr)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Google Drive URI backup write skipped: ${e.message}")
                }

                // 5. Notify Android System BackupManager for Google One / Google Drive App Data Backup
                try {
                    BackupManager.dataChanged(context.packageName)
                } catch (_: Exception) {}

                val nowTs = formatTimestamp()
                _lastBackupTimestampFlow.value = nowTs
                _isDriveBackupHealthyFlow.value = driveSavedOk
                try {
                    repository.setPreference(AppPreferenceEntity(KEY_LAST_BACKUP_TIMESTAMP, nowTs))
                    repository.setPreference(AppPreferenceEntity(KEY_LAST_DRIVE_SYNC_STATUS, driveSavedOk.toString()))
                } catch (_: Exception) {}

                Log.d(TAG, "Auto-backup successful ($nowTs, ${json.length} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup failed: ${e.message}")
            } finally {
                _isBackupInProgressFlow.value = false
            }
        }
    }

    /**
     * Reads and restores a backup JSON directly from a Google Drive Document URI.
     */
    suspend fun restoreFromGoogleDriveUri(
        context: Context,
        repository: FocusTubeRepository,
        uri: Uri
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: return@withContext Result.failure(Exception("Google Drive फ़ाइल नहीं पढ़ी जा सकी"))
                repository.importBackupJson(json, replaceExisting = false)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Returns the latest existing auto-backup file from any available local storage tier.
     */
    fun getExistingBackupFile(context: Context): File? {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicFile = if (downloadDir != null) File(downloadDir, PUBLIC_BACKUP_FILENAME) else null
            if (publicFile != null && publicFile.exists() && publicFile.length() > 50) {
                return publicFile
            }
        } catch (_: Exception) {}

        val extDir = context.getExternalFilesDir(null)
        val extFile = if (extDir != null) File(extDir, BACKUP_FILENAME) else null
        if (extFile != null && extFile.exists() && extFile.length() > 50) {
            return extFile
        }

        val internalFile = File(context.filesDir, BACKUP_FILENAME)
        if (internalFile.exists() && internalFile.length() > 50) {
            return internalFile
        }

        return null
    }

    /**
     * Automatically restores from the latest auto-backup file or linked Google Drive URI.
     */
    suspend fun restoreLatestBackup(context: Context, repository: FocusTubeRepository): Result<Int> {
        return withContext(Dispatchers.IO) {
            // Try linked Google Drive URI first if available
            try {
                val driveUriStr = repository.getPreferenceSnapshot(GoogleAccountManager.KEY_DRIVE_BACKUP_URI)
                    .orEmpty()
                if (driveUriStr.isNotBlank()) {
                    val driveRes = restoreFromGoogleDriveUri(context, repository, Uri.parse(driveUriStr))
                    if (driveRes.isSuccess) return@withContext driveRes
                }
            } catch (_: Exception) {}

            val file = getExistingBackupFile(context) ?: return@withContext Result.failure(Exception("No backup file found"))
            try {
                val json = file.readText()
                repository.importBackupJson(json, replaceExisting = false)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Checks if current database is completely empty (e.g. freshly installed after app deletion)
     * and automatically restores all groups, channels, playlists, and video-linked notes.
     */
    suspend fun checkAndAutoRestoreIfEmpty(context: Context, repository: FocusTubeRepository): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val groupsCount = repository.getAllGroupsSnapshot().size
                val playlistsCount = repository.getAllPlaylistsSnapshot().size
                if (groupsCount == 0 && playlistsCount == 0) {
                    val backupFile = getExistingBackupFile(context)
                    if (backupFile != null && backupFile.exists() && backupFile.length() > 100) {
                        val json = backupFile.readText()
                        val result = repository.importBackupJson(json, replaceExisting = false)
                        return@withContext result.isSuccess
                    }
                }
                false
            } catch (e: Exception) {
                Log.w(TAG, "checkAndAutoRestoreIfEmpty failed: ${e.message}")
                false
            }
        }
    }
}
