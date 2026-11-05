package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.repository.FocusTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AutoBackupHelper {
    private const val TAG = "AutoBackupHelper"
    private const val BACKUP_FILENAME = "focustube_auto_backup.json"

    /**
     * Silently saves an up-to-date backup JSON file to internal storage and external storage.
     * Prevents data loss if app storage is cleared or app is reinstalled.
     */
    suspend fun triggerAutoBackup(context: Context, repository: FocusTubeRepository) {
        withContext(Dispatchers.IO) {
            try {
                val json = repository.exportBackupJson()
                if (json.isBlank()) return@withContext

                // 1. Write to internal files directory (private)
                val internalFile = File(context.filesDir, BACKUP_FILENAME)
                internalFile.writeText(json)

                // 2. Write to external files directory (survives app cache clear)
                try {
                    val extDir = context.getExternalFilesDir(null)
                    if (extDir != null && extDir.exists()) {
                        val extFile = File(extDir, BACKUP_FILENAME)
                        extFile.writeText(json)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "External backup write failed: ${e.message}")
                }

                Log.d(TAG, "Auto-backup successful (${json.length} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup failed: ${e.message}")
            }
        }
    }

    /**
     * Returns an existing auto-backup file if one is available.
     */
    fun getExistingBackupFile(context: Context): File? {
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
}
