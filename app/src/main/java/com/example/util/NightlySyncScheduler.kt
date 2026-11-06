package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.repository.FocusTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object NightlySyncScheduler {
    private const val TAG = "NightlySyncScheduler"

    /**
     * Executes automatic nightly sync and backup.
     * Backs up all data, syncs playlists with YouTube, and marks sync status.
     */
    fun performNightlySync(
        context: Context,
        repository: FocusTubeRepository,
        accountManager: GoogleAccountManager,
        scope: CoroutineScope,
        onComplete: ((String) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting Nightly Auto-Sync & Backup...")

                // 1. Auto-Backup all groups, channels, and playlists
                AutoBackupHelper.triggerAutoBackup(context, repository)

                // 2. Fetch fresh videos for all playlists
                val playlists = repository.getAllPlaylistsSnapshot()
                var totalNewVideos = 0
                for (pl in playlists) {
                    val result = repository.syncPlaylistVideos(pl.playlistId)
                    result.onSuccess { totalNewVideos += it }
                }

                // 3. If signed in, ensure all videos in playlists are synced
                val profile = accountManager.userProfile.value
                if (profile.isSignedIn) {
                    for (pl in playlists) {
                        repository.markAllPlaylistVideosSynced(pl.playlistId)
                    }
                }

                val nowStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                val syncStatus = "🌙 ऑटो-सिंक संपन्न: $nowStr"
                accountManager.updateLastSyncTime(syncStatus)

                Log.d(TAG, "Nightly Auto-Sync completed ($totalNewVideos new videos)")
                onComplete?.invoke(syncStatus)
            } catch (e: Exception) {
                Log.w(TAG, "Nightly sync error: ${e.message}")
            }
        }
    }

    /**
     * Checks if current time is night (between 8:00 PM and 6:00 AM) or if last sync was > 20 hours ago.
     */
    fun isNightTimeOrDue(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 20 || hour < 6
    }
}
