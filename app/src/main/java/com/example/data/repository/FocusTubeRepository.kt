package com.example.data.repository

import com.example.data.local.dao.FocusTubeDao
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity
import com.example.data.remote.YouTubeDataFetcher
import com.example.util.FocusShield
import com.example.util.ValidationResult
import kotlinx.coroutines.flow.Flow
import java.util.regex.Pattern

class FocusTubeRepository(private val dao: FocusTubeDao) {

    val allGroups: Flow<List<SubscriptionGroupEntity>> = dao.getAllGroups()
    val pinnedGroup: Flow<SubscriptionGroupEntity?> = dao.getPinnedGroup()

    val allPlaylists: Flow<List<PlaylistEntity>> = dao.getAllPlaylists()
    val pinnedPlaylist: Flow<PlaylistEntity?> = dao.getPinnedPlaylist()

    fun getChannelsForGroup(groupId: Long): Flow<List<ChannelEntity>> = dao.getChannelsForGroup(groupId)

    fun getVideosForPlaylist(playlistId: String): Flow<List<VideoEntity>> = dao.getVideosForPlaylist(playlistId)

    fun getVideosForGroup(groupId: Long): Flow<List<VideoEntity>> = dao.getVideosForGroup(groupId)

    suspend fun setPinnedGroup(groupId: Long) {
        dao.setPinnedGroup(groupId)
    }

    suspend fun setPinnedPlaylist(playlistId: String) {
        dao.setPinnedPlaylist(playlistId)
    }

    suspend fun insertGroup(name: String): Long {
        return dao.insertGroup(SubscriptionGroupEntity(name = name))
    }

    suspend fun deleteGroup(id: Long) {
        dao.deleteGroup(id)
    }

    /**
     * Real fetch and add of channel + its videos
     */
    suspend fun fetchAndAddChannelToGroup(rawInput: String, groupId: Long): String {
        val result = YouTubeDataFetcher.fetchChannel(rawInput, maxContinuationPages = 35)

        // Strict Focus Shield validation: Block entertainment, movies, music, prank, roasts, etc.
        val sampleTitles = result.videos.take(20).map { it.title }
        val validation = FocusShield.validateChannel(
            channelName = result.channelName,
            handle = result.handle.ifBlank { rawInput },
            description = result.description,
            sampleVideoTitles = sampleTitles
        )
        if (validation is ValidationResult.Blocked) {
            throw IllegalArgumentException(validation.reason)
        }

        val cleanHandleOrUrl = when {
            result.channelId.startsWith("UC") -> "https://www.youtube.com/channel/${result.channelId}"
            result.handle.isNotBlank() -> "https://www.youtube.com/@${result.handle.removePrefix("@")}"
            rawInput.trim().startsWith("http") -> rawInput.trim()
            else -> "https://www.youtube.com/@${rawInput.trim().removePrefix("@").replace(" ", "")}"
        }
        val channelId = dao.insertChannel(
            ChannelEntity(
                name = result.channelName,
                handleOrUrl = cleanHandleOrUrl,
                groupId = groupId
            )
        )

        // Filter out any casual distraction videos from the feed
        val safeVideos = result.videos.filterNot { FocusShield.isDistractionVideo(it.title, it.channelName) }

        if (safeVideos.isNotEmpty()) {
            val existing = dao.getVideosForGroupSnapshot(groupId)
            val existingIds = existing.map { it.videoId }.toSet()
            val newVideos = safeVideos.distinctBy { it.videoId }.filter { it.videoId !in existingIds }
            if (newVideos.isNotEmpty()) {
                val startOrder = existing.size
                val videoEntities = newVideos.mapIndexed { index, v ->
                    VideoEntity(
                        videoId = v.videoId,
                        groupId = groupId,
                        channelDbId = channelId,
                        title = v.title,
                        channelName = v.channelName.ifBlank { result.channelName },
                        description = v.description,
                        duration = v.duration,
                        thumbnailUrl = v.thumbnailUrl,
                        uploadDate = v.uploadDate,
                        publishedTimestamp = v.publishedTimestamp,
                        orderIndex = startOrder + index
                    )
                }
                videoEntities.chunked(400).forEach { batch ->
                    dao.insertVideos(batch)
                }
            }
        }
        return "${result.channelName} added with ${safeVideos.size} videos (Focus Shield Active)"
    }

    suspend fun deleteChannel(id: Long) {
        dao.deleteChannel(id)
    }

    suspend fun moveChannelToGroup(channelId: Long, newGroupId: Long) {
        dao.updateChannelGroup(channelId, newGroupId)
    }

    /**
     * Real fetch and add of playlist + its videos
     * Strictly validates against FocusShield: Rejects music, songs, movies, comedy, and distraction playlists AT THE GATE.
     */
    /**
     * Real fetch and add of playlist + its videos
     * Strictly validates against FocusShield: Rejects music, songs, movies, comedy, and distraction playlists AT THE GATE.
     * Fully allows and prioritizes all educational, study, and important playlists.
     */
    suspend fun fetchAndAddPlaylist(rawInput: String, customTitle: String = ""): String {
        val trimmedInput = rawInput.trim()

        // Case 1: User wants to create a custom/empty playlist (e.g. "Important Education", "My Study") without a YouTube link
        if (trimmedInput.isBlank()) {
            val title = customTitle.trim().ifBlank { "Important Education" }
            val customPlId = "custom_" + System.currentTimeMillis()
            dao.insertPlaylist(
                PlaylistEntity(
                    playlistId = customPlId,
                    title = title,
                    isPinnedHome = false
                )
            )
            return "Playlist '$title' created successfully. You can now add videos to it."
        }

        // Case 2: User pasted a single YouTube video URL/ID instead of a playlist URL
        val singleVidId = extractVideoId(trimmedInput)
        if (singleVidId != null && !trimmedInput.contains("list=") && !trimmedInput.startsWith("PL")) {
            val info = fetchSingleVideoInfo(singleVidId)
            val vidTitle = info?.title ?: "Video #$singleVidId"
            val vidChannel = info?.channelName ?: "YouTube"

            if (FocusShield.isDistractionVideo(vidTitle, vidChannel)) {
                throw IllegalArgumentException("🛡️ Focus Shield: This video matches music/distraction content and cannot be added.")
            }

            val plTitle = if (customTitle.isNotBlank()) customTitle.trim() else "$vidTitle (Playlist)"
            val customPlId = "custom_" + System.currentTimeMillis()

            dao.insertPlaylist(
                PlaylistEntity(
                    playlistId = customPlId,
                    title = plTitle,
                    isPinnedHome = false
                )
            )

            dao.insertVideo(
                VideoEntity(
                    videoId = singleVidId,
                    playlistId = customPlId,
                    title = vidTitle,
                    channelName = vidChannel,
                    duration = com.example.util.DurationUtils.formatCleanDuration("", singleVidId),
                    thumbnailUrl = "https://img.youtube.com/vi/$singleVidId/hqdefault.jpg",
                    uploadDate = "Recently added",
                    publishedTimestamp = System.currentTimeMillis()
                )
            )
            return "Playlist '$plTitle' created with video '$vidTitle'."
        }

        // Case 3: Standard YouTube playlist URL or Playlist ID
        val result = YouTubeDataFetcher.fetchPlaylist(trimmedInput)
        val rawTitle = if (result.title.isBlank() || result.title.equals("undefined", ignoreCase = true) || result.title.equals("Playlist", ignoreCase = true)) {
            if (result.videos.isNotEmpty()) {
                val firstVidTitle = result.videos.first().title
                if (firstVidTitle.isNotBlank() && !firstVidTitle.equals("undefined", ignoreCase = true)) {
                    "$firstVidTitle (Playlist)"
                } else {
                    "Education & Study Playlist"
                }
            } else {
                "Education & Study Playlist"
            }
        } else {
            result.title
        }

        val finalTitle = if (customTitle.isNotBlank() && !customTitle.equals("undefined", ignoreCase = true)) {
            customTitle.trim()
        } else {
            rawTitle
        }

        // Strict Focus Shield Gate: Validate playlist title, sample video titles, AND channel names
        val sampleTitles = result.videos.take(30).map { it.title }
        val sampleChannels = result.videos.take(30).map { it.channelName }
        val validation = FocusShield.validatePlaylist(
            playlistTitle = finalTitle,
            sampleVideoTitles = sampleTitles,
            sampleChannelNames = sampleChannels
        )
        if (validation is ValidationResult.Blocked) {
            throw IllegalArgumentException(validation.reason)
        }

        // Filter individual videos with both title and channelName to ensure zero music/distraction videos slip in
        val safeVideos = result.videos.filterNot { FocusShield.isDistractionVideo(it.title, it.channelName) }
        if (safeVideos.isEmpty() && result.videos.isNotEmpty()) {
            throw IllegalArgumentException("🛡️ Focus Shield: All videos in this playlist were identified as music/entertainment. Distraction playlists are not allowed.")
        }

        val isFirst = false

        dao.insertPlaylist(
            PlaylistEntity(
                playlistId = result.playlistId,
                title = finalTitle,
                isPinnedHome = isFirst
            )
        )

        if (safeVideos.isNotEmpty()) {
            val existing = dao.getVideosForPlaylistSnapshot(result.playlistId)
            val existingIds = existing.map { it.videoId }.toSet()
            val newVideos = safeVideos.distinctBy { it.videoId }.filter { it.videoId !in existingIds }
            if (newVideos.isNotEmpty()) {
                val startOrder = existing.size
                val videoEntities = newVideos.mapIndexed { index, v ->
                    VideoEntity(
                        videoId = v.videoId,
                        playlistId = result.playlistId,
                        title = v.title,
                        channelName = v.channelName,
                        description = v.description,
                        duration = v.duration,
                        thumbnailUrl = v.thumbnailUrl,
                        uploadDate = v.uploadDate,
                        publishedTimestamp = v.publishedTimestamp,
                        orderIndex = startOrder + index
                    )
                }
                videoEntities.chunked(400).forEach { batch ->
                    dao.insertVideos(batch)
                }
            }
        }
        return "'$finalTitle' added with ${safeVideos.size} videos (Focus Shield Active)"
    }

    suspend fun syncPlaylistVideos(playlistId: String): Result<Int> {
        return try {
            val result = YouTubeDataFetcher.fetchPlaylist(playlistId)
            val existing = dao.getVideosForPlaylistSnapshot(playlistId)
            val existingIds = existing.map { it.videoId }.toSet()

            // Update playlist title if valid title fetched
            if (result.title.isNotBlank() && !result.title.equals("undefined", ignoreCase = true) && !result.title.equals("Playlist", ignoreCase = true)) {
                dao.updatePlaylistTitle(playlistId, result.title)
            }

            // Update real duration for existing videos
            for (v in result.videos) {
                if (v.duration.isNotBlank() && v.videoId in existingIds) {
                    dao.updateVideoDuration(v.videoId, v.duration)
                }
            }

            // Strictly filter out any distraction or music videos and de-duplicate
            val safeVideos = result.videos.filterNot { FocusShield.isDistractionVideo(it.title, it.channelName) }
            val newVideos = safeVideos.distinctBy { it.videoId }.filter { it.videoId !in existingIds }
            if (newVideos.isNotEmpty()) {
                val startOrder = existing.size
                val videoEntities = newVideos.mapIndexed { index, v ->
                    VideoEntity(
                        videoId = v.videoId,
                        playlistId = playlistId,
                        title = v.title,
                        channelName = v.channelName,
                        description = v.description,
                        duration = v.duration,
                        thumbnailUrl = v.thumbnailUrl,
                        uploadDate = v.uploadDate,
                        publishedTimestamp = v.publishedTimestamp,
                        orderIndex = startOrder + index
                    )
                }
                videoEntities.chunked(400).forEach { batch ->
                    dao.insertVideos(batch)
                }
            }
            Result.success(newVideos.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncGroupChannels(groupId: Long): Result<Int> {
        return try {
            val channels = dao.getChannelsForGroupSnapshot(groupId)
            if (channels.isEmpty()) {
                return Result.success(0)
            }
            val existing = dao.getVideosForGroupSnapshot(groupId)
            val existingIds = existing.map { it.videoId }.toMutableSet()
            var totalAdded = 0

            for (ch in channels) {
                try {
                    val query = ch.handleOrUrl.ifBlank { ch.name }
                    val result = YouTubeDataFetcher.fetchChannel(query, maxContinuationPages = 15)

                    // Identify genuinely new videos from this channel, filtering out any casual distraction videos and duplicates
                    val safeVideos = result.videos.filterNot { FocusShield.isDistractionVideo(it.title, it.channelName) }
                    val newVideos = safeVideos.distinctBy { it.videoId }.filter { it.videoId !in existingIds }
                    if (newVideos.isNotEmpty()) {
                        val videoEntities = newVideos.mapIndexed { index, v ->
                            VideoEntity(
                                videoId = v.videoId,
                                groupId = groupId,
                                channelDbId = ch.id,
                                title = v.title,
                                channelName = v.channelName.ifBlank { ch.name },
                                description = v.description,
                                duration = v.duration,
                                thumbnailUrl = v.thumbnailUrl,
                                uploadDate = v.uploadDate,
                                publishedTimestamp = v.publishedTimestamp,
                                orderIndex = index
                            )
                        }
                        videoEntities.chunked(400).forEach { batch ->
                            dao.insertVideos(batch)
                        }
                        newVideos.forEach { existingIds.add(it.videoId) }
                        totalAdded += newVideos.size
                    }
                } catch (channelEx: Exception) {
                    android.util.Log.w("FocusTubeRepo", "Failed to sync channel '${ch.name}': ${channelEx.message}")
                }
            }
            Result.success(totalAdded)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun insertVideo(video: VideoEntity): Long {
        // Prevent duplicate insertion into the same playlist
        if (!video.playlistId.isNullOrBlank()) {
            if (dao.countVideoInPlaylist(video.playlistId, video.videoId) > 0) {
                return -1L // Video already exists in this playlist
            }
        }
        // Prevent duplicate insertion into the same group
        if (video.groupId != null) {
            if (dao.countVideoInGroup(video.groupId, video.videoId) > 0) {
                return -1L // Video already exists in this group
            }
        }
        return dao.insertVideo(video)
    }

    suspend fun checkVideoExistsInPlaylist(playlistId: String, videoId: String): Boolean {
        return dao.countVideoInPlaylist(playlistId, videoId) > 0
    }

    suspend fun purgeDuplicateVideos(): Int {
        val plDupes = dao.purgeDuplicateVideosInPlaylists()
        val grpDupes = dao.purgeDuplicateVideosInGroups()
        return plDupes + grpDupes
    }

    suspend fun deleteVideo(id: Long) {
        dao.deleteVideo(id)
    }

    suspend fun toggleVideoCompleted(id: Long, completed: Boolean) {
        dao.setVideoCompleted(id, completed)
    }

    suspend fun copyVideoToPlaylist(video: VideoEntity, targetPlaylistId: String): Long {
        if (dao.countVideoInPlaylist(targetPlaylistId, video.videoId) > 0) {
            return -1L // Already exists in target playlist
        }
        return dao.insertVideo(
            VideoEntity(
                videoId = video.videoId,
                playlistId = targetPlaylistId,
                title = video.title,
                channelName = video.channelName,
                description = video.description,
                duration = video.duration,
                thumbnailUrl = video.thumbnailUrl,
                notes = video.notes,
                uploadDate = video.uploadDate,
                publishedTimestamp = video.publishedTimestamp
            )
        )
    }

    suspend fun updateVideoNotes(videoId: Long, notes: String) {
        dao.updateVideoNotes(videoId, notes)
    }

    fun searchAllVideos(query: String): Flow<List<VideoEntity>> = dao.searchAllVideos(query)

    fun getAllVideos(): Flow<List<VideoEntity>> = dao.getAllVideosFlow()

    suspend fun createCustomPlaylist(playlistId: String, title: String): Long {
        return dao.insertPlaylist(
            PlaylistEntity(
                playlistId = playlistId,
                title = title,
                isPinnedHome = false
            )
        )
    }

    suspend fun fetchVideoDescription(videoId: String): String {
        return YouTubeDataFetcher.fetchVideoDescription(videoId)
    }

    suspend fun updateVideoYouTubeSync(id: Long, synced: Boolean) {
        val timestamp = if (synced) System.currentTimeMillis() else null
        dao.updateVideoYouTubeSync(id, synced, timestamp)
    }

    suspend fun markAllPlaylistVideosSynced(playlistId: String) {
        dao.markAllPlaylistVideosSynced(playlistId, System.currentTimeMillis())
    }

    /**
     * Purges any verified distraction, music, song, movie, comedy, or gaming videos
     * currently in the database to ensure the app is clean.
     * Never deletes valid user playlists.
     */
    suspend fun purgeAllDistractions(): Int {
        val allVideos = dao.getAllVideosSnapshot()
        var purgedCount = 0
        for (v in allVideos) {
            if (FocusShield.isDistractionVideo(v.title, v.channelName)) {
                dao.deleteVideo(v.id)
                purgedCount++
            }
        }
        // Clean up or rename any playlists with title "undefined"
        val allPlaylists = dao.getAllPlaylistsSnapshot()
        for (pl in allPlaylists) {
            if (pl.title.equals("undefined", ignoreCase = true) || pl.title.isBlank()) {
                dao.insertPlaylist(pl.copy(title = "Study Playlist"))
            }
        }
        return purgedCount
    }

    suspend fun purgeSingleVideoIfDistraction(videoId: Long, title: String, channelName: String = ""): Boolean {
        if (FocusShield.isDistractionVideo(title, channelName)) {
            dao.deleteVideo(videoId)
            return true
        }
        return false
    }

    suspend fun fetchSingleVideoInfo(videoId: String): YouTubeDataFetcher.SingleVideoInfo? {
        return YouTubeDataFetcher.fetchSingleVideoInfo(videoId)
    }

    fun getUnsyncedVideosForPlaylist(playlistId: String): Flow<List<VideoEntity>> {
        return dao.getUnsyncedVideosForPlaylist(playlistId)
    }

    // === Search History ===
    fun getSearchHistory(type: String): Flow<List<com.example.data.local.entity.SearchHistoryEntity>> {
        return dao.getSearchHistory(type)
    }

    suspend fun saveSearchQuery(query: String, type: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            dao.deleteSearchHistoryItem(trimmed, type)
            dao.insertSearchHistory(
                com.example.data.local.entity.SearchHistoryEntity(
                    query = trimmed,
                    type = type,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteSearchHistoryItem(query: String, type: String) {
        dao.deleteSearchHistoryItem(query, type)
    }

    suspend fun clearSearchHistory(type: String) {
        dao.clearSearchHistory(type)
    }

    // === Backup & Restore (Export / Import) ===
    suspend fun exportBackupJson(): String {
        val groups = dao.getAllGroupsSnapshot()
        val channels = dao.getAllChannelsSnapshot()
        val playlists = dao.getAllPlaylistsSnapshot()
        val videos = dao.getAllVideosSnapshot()

        val backup = com.example.data.backup.FocusTubeBackup(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            groups = groups,
            channels = channels,
            playlists = playlists,
            videos = videos
        )

        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(com.example.data.backup.FocusTubeBackup::class.java)
        return adapter.indent("  ").toJson(backup)
    }

    suspend fun importBackupJson(jsonString: String, replaceExisting: Boolean = false): Result<Int> {
        return try {
            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val adapter = moshi.adapter(com.example.data.backup.FocusTubeBackup::class.java)
            val backup = adapter.fromJson(jsonString) ?: return Result.failure(Exception("Invalid backup JSON format"))

            if (replaceExisting) {
                dao.clearAllVideos()
                dao.clearAllChannels()
                dao.clearAllPlaylists()
                dao.clearAllGroups()
            }

            if (backup.groups.isNotEmpty()) {
                dao.insertGroups(backup.groups)
            }
            if (backup.channels.isNotEmpty()) {
                dao.insertChannels(backup.channels)
            }
            if (backup.playlists.isNotEmpty()) {
                dao.insertPlaylists(backup.playlists)
            }
            if (backup.videos.isNotEmpty()) {
                dao.insertVideos(backup.videos)
            }

            val totalItems = backup.groups.size + backup.playlists.size + backup.channels.size + backup.videos.size
            Result.success(totalItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllGroupsSnapshot(): List<SubscriptionGroupEntity> = dao.getAllGroupsSnapshot()
    suspend fun getAllPlaylistsSnapshot(): List<PlaylistEntity> = dao.getAllPlaylistsSnapshot()

    companion object {
        fun extractVideoId(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                return trimmed
            }
            val pattern = Pattern.compile(
                "(?:https?:\\/\\/)?(?:www\\.|m\\.)?(?:youtube\\.com\\/(?:watch\\?.*?v=|embed\\/|v\\/|shorts\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
            )
            val matcher = pattern.matcher(trimmed)
            return if (matcher.find()) {
                matcher.group(1)
            } else {
                null
            }
        }

        fun extractPlaylistId(input: String): String {
            val trimmed = input.trim()
            if (trimmed.startsWith("PL") || trimmed.startsWith("UU") || trimmed.startsWith("RD") || trimmed.startsWith("OL")) {
                return trimmed
            }
            val pattern = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)")
            val matcher = pattern.matcher(trimmed)
            return if (matcher.find()) {
                matcher.group(1) ?: trimmed
            } else {
                trimmed
            }
        }
    }
}
