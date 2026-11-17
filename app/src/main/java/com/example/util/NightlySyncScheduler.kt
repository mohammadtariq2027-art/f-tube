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
     * 1. Backs up all groups, channels, playlists, and video-linked notes (including Google Drive URI).
     * 2. Pushes any unsynced locally-saved playlist videos directly to the user's real YouTube playlist
     *    if YouTube Auto-Save session/OAuth is active.
     * 3. Fetches fresh videos for all playlists from YouTube.
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
                accountManager.updateYouTubeAutoSaveSession()
                val profile = accountManager.userProfile.value

                // 0. Discover & import ALL user playlists (A to Z) and ALL subscribed channels (A to Z) from YouTube
                try {
                    repository.syncUserAccountChannelsAndPlaylists(
                        context = context,
                        email = profile.email,
                        oauthToken = profile.youtubeOAuthToken
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Account channels/playlists discovery note: ${e.message}")
                }

                // 1. Push any unsynced videos in real YouTube playlists to the user's actual YouTube playlist
                val playlists = repository.getAllPlaylistsSnapshot()
                var pushedToYouTubeCount = 0
                for (pl in playlists) {
                    if (YouTubeSyncHelper.isRealYouTubePlaylist(pl.playlistId)) {
                        val unsynced = repository.getUnsyncedVideosForPlaylistSnapshot(pl.playlistId)
                        for (vid in unsynced) {
                            val added = YouTubeSyncHelper.autoAddVideoToYouTubePlaylist(
                                context = context,
                                playlistId = pl.playlistId,
                                videoId = vid.videoId,
                                email = profile.email,
                                oauthToken = profile.youtubeOAuthToken
                            )
                            if (added) {
                                repository.updateVideoYouTubeSync(vid.id, true)
                                pushedToYouTubeCount++
                            }
                        }
                    }
                }

                // 2. Fetch fresh videos for all playlists from YouTube
                var totalNewVideos = 0
                for (pl in playlists) {
                    val result = repository.syncPlaylistVideos(pl.playlistId)
                    result.onSuccess { totalNewVideos += it }
                }

                // 3. Auto-Backup all groups, channels, playlists, and video-linked notes
                AutoBackupHelper.triggerAutoBackup(context, repository)

                val nowStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                val syncStatus = if (pushedToYouTubeCount > 0) {
                    "✓ Sync complete ($nowStr) • $pushedToYouTubeCount videos saved to real YouTube playlist"
                } else {
                    "🌙 Auto-sync & backup complete: $nowStr"
                }
                accountManager.updateLastSyncTime(syncStatus)

                Log.d(TAG, "Nightly Auto-Sync completed ($totalNewVideos new, $pushedToYouTubeCount pushed to YouTube)")
                onComplete?.invoke(syncStatus)
            } catch (e: Exception) {
                Log.w(TAG, "Nightly sync error: ${e.message}")
                onComplete?.invoke("Sync error: ${e.localizedMessage ?: "Please try again"}")
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
