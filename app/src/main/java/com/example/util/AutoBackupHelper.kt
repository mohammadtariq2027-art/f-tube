package com.example.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.repository.FocusTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AutoBackupHelper {
    private const val TAG = "AutoBackupHelper"
    private const val BACKUP_FILENAME = "focustube_auto_backup.json"
    private const val PUBLIC_BACKUP_FILENAME = "FocusTube_AutoBackup.json"

    /**
     * Silently saves an up-to-date backup JSON file to internal storage, external files,
     * and the persistent Downloads directory so data survives app uninstallation.
     */
    suspend fun triggerAutoBackup(context: Context, repository: FocusTubeRepository) {
        withContext(Dispatchers.IO) {
            try {
                val json = repository.exportBackupJson()
                if (json.isBlank()) return@withContext

                // 1. Write to internal files directory (private)
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

                // 3. Write to persistent Downloads folder (survives app uninstall!)
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
                        val publicFile = File(downloadDir, PUBLIC_BACKUP_FILENAME)
                        publicFile.writeText(json)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Persistent Downloads backup write failed: ${e.message}")
                }

                Log.d(TAG, "Auto-backup successful (${json.length} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup failed: ${e.message}")
            }
        }
    }

    /**
     * Returns the latest existing auto-backup file from any available storage tier.
     */
    fun getExistingBackupFile(context: Context): File? {
        // 1. Check persistent Downloads folder first (survives app reinstallation)
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicFile = if (downloadDir != null) File(downloadDir, PUBLIC_BACKUP_FILENAME) else null
            if (publicFile != null && publicFile.exists() && publicFile.length() > 50) {
                return publicFile
            }
        } catch (_: Exception) {}

        // 2. Check external files directory
        val extDir = context.getExternalFilesDir(null)
        val extFile = if (extDir != null) File(extDir, BACKUP_FILENAME) else null
        if (extFile != null && extFile.exists() && extFile.length() > 50) {
            return extFile
        }

        // 3. Check internal files directory
        val internalFile = File(context.filesDir, BACKUP_FILENAME)
        if (internalFile.exists() && internalFile.length() > 50) {
            return internalFile
        }

        return null
    }

    /**
     * Automatically restores from the latest auto-backup file.
     */
    suspend fun restoreLatestBackup(context: Context, repository: FocusTubeRepository): Result<Int> {
        return withContext(Dispatchers.IO) {
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
     * and automatically restores all groups, channels, and playlists from the detected backup file.
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
