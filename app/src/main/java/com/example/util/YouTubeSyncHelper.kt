package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.local.entity.VideoEntity

object YouTubeSyncHelper {

    /**
     * Opens a specific video (optionally within a playlist) in the official YouTube app.
     * Uses the user's logged-in YouTube account to let them save/bookmark with 1 tap.
     */
    fun openInYouTubeApp(context: Context, videoId: String, playlistId: String? = null): Boolean {
        val cleanVideoId = videoId.trim()
        val cleanPlaylistId = playlistId?.trim().orEmpty()

        val uriString = if (cleanPlaylistId.isNotBlank() && isRealYouTubePlaylist(cleanPlaylistId)) {
            "https://www.youtube.com/watch?v=$cleanVideoId&list=$cleanPlaylistId"
        } else {
            "https://www.youtube.com/watch?v=$cleanVideoId"
        }

        // Try launching official YouTube app first
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to default browser / custom tabs if YouTube app is disabled or uninstalled
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (err: Exception) {
                Toast.makeText(context, "Could not open YouTube: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    /**
     * Opens the full playlist in official YouTube app.
     */
    fun openPlaylistInYouTubeApp(context: Context, playlistId: String): Boolean {
        val cleanId = playlistId.trim()
        val uriString = if (isRealYouTubePlaylist(cleanId)) {
            "https://www.youtube.com/playlist?list=$cleanId"
        } else {
            "https://www.youtube.com"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (err: Exception) {
                Toast.makeText(context, "Could not open YouTube playlist", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    /**
     * Copies all video links to clipboard so the user can paste or save in bulk.
     */
    fun copyAllVideoLinks(context: Context, playlistTitle: String, videos: List<VideoEntity>) {
        if (videos.isEmpty()) {
            Toast.makeText(context, "No videos to copy", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("📋 FocusTube Playlist: $playlistTitle\n")
        sb.append("Total Videos: ${videos.size}\n\n")
        videos.forEachIndexed { index, v ->
            sb.append("${index + 1}. ${v.title}\nhttps://youtu.be/${v.videoId}\n\n")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("FocusTube Playlist Videos", sb.toString().trim())
        clipboard?.setPrimaryClip(clip)

        Toast.makeText(context, "Copied ${videos.size} video link(s) to clipboard", Toast.LENGTH_LONG).show()
    }

    /**
     * Shares video links via Android Share Sheet.
     */
    fun sharePlaylistVideos(context: Context, playlistTitle: String, videos: List<VideoEntity>) {
        if (videos.isEmpty()) return

        val sb = StringBuilder()
        sb.append("FocusTube Playlist: $playlistTitle\n\n")
        videos.forEachIndexed { index, v ->
            sb.append("${index + 1}. ${v.title} - https://youtu.be/${v.videoId}\n")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
            putExtra(Intent.EXTRA_TITLE, playlistTitle)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share or Save Playlist Videos").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }

    /**
     * Opens all video IDs together as a single batch queue in the official YouTube app.
     * YouTube's `watch_videos?video_ids=ID1,ID2,...` URL creates an instant temporary playlist
     * queue with a prominent '+' (Save) button at the top so the user can save all videos
     * into their official YouTube account playlist in just 1-2 clicks!
     */
    fun openBatchInYouTubeApp(context: Context, videoIds: List<String>): Boolean {
        val cleanIds = videoIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) {
            Toast.makeText(context, "No videos to sync", Toast.LENGTH_SHORT).show()
            return false
        }

        // Take up to 50 videos per batch for URL safety
        val batchIds = cleanIds.take(50)
        val url = "https://www.youtube.com/watch_videos?video_ids=" + batchIds.joinToString(",")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (err: Exception) {
                Toast.makeText(context, "Could not open YouTube: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    fun isRealYouTubePlaylist(playlistId: String): Boolean {
        val trimmed = playlistId.trim()
        return trimmed.startsWith("PL") || trimmed.startsWith("UU") || trimmed.startsWith("RD") || trimmed.startsWith("OL")
    }
}
