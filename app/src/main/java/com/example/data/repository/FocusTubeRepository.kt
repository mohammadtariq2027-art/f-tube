package com.example.data.repository

import com.example.data.local.dao.FocusTubeDao
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity
import com.example.data.remote.YouTubeDataFetcher
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
        val result = YouTubeDataFetcher.fetchChannel(rawInput)
        val channelId = dao.insertChannel(
            ChannelEntity(
                name = result.channelName,
                handleOrUrl = result.handle.ifBlank { rawInput.trim() },
                groupId = groupId
            )
        )

        if (result.videos.isNotEmpty()) {
            val videoEntities = result.videos.mapIndexed { index, v ->
                VideoEntity(
                    videoId = v.videoId,
                    groupId = groupId,
                    channelDbId = channelId,
                    title = v.title,
                    channelName = v.channelName.ifBlank { result.channelName },
                    description = v.description,
                    duration = v.duration,
                    thumbnailUrl = v.thumbnailUrl,
                    orderIndex = index
                )
            }
            dao.insertVideos(videoEntities)
        }
        return "${result.channelName} added with ${result.videos.size} videos"
    }

    suspend fun deleteChannel(id: Long) {
        dao.deleteChannel(id)
    }

    suspend fun moveChannelToGroup(channelId: Long, newGroupId: Long) {
        dao.updateChannelGroup(channelId, newGroupId)
    }

    /**
     * Real fetch and add of playlist + its videos
     */
    suspend fun fetchAndAddPlaylist(rawInput: String, customTitle: String = ""): String {
        val result = YouTubeDataFetcher.fetchPlaylist(rawInput)
        val finalTitle = customTitle.ifBlank { result.title }
        val isFirst = false

        dao.insertPlaylist(
            PlaylistEntity(
                playlistId = result.playlistId,
                title = finalTitle,
                isPinnedHome = isFirst
            )
        )

        if (result.videos.isNotEmpty()) {
            val videoEntities = result.videos.mapIndexed { index, v ->
                VideoEntity(
                    videoId = v.videoId,
                    playlistId = result.playlistId,
                    title = v.title,
                    channelName = v.channelName,
                    description = v.description,
                    duration = v.duration,
                    thumbnailUrl = v.thumbnailUrl,
                    orderIndex = index
                )
            }
            dao.insertVideos(videoEntities)
        }
        return "'$finalTitle' added with ${result.videos.size} videos"
    }

    suspend fun deletePlaylist(playlistId: String) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun insertVideo(video: VideoEntity): Long {
        return dao.insertVideo(video)
    }

    suspend fun deleteVideo(id: Long) {
        dao.deleteVideo(id)
    }

    suspend fun toggleVideoCompleted(id: Long, completed: Boolean) {
        dao.setVideoCompleted(id, completed)
    }

    suspend fun copyVideoToPlaylist(video: VideoEntity, targetPlaylistId: String): Long {
        return dao.insertVideo(
            VideoEntity(
                videoId = video.videoId,
                playlistId = targetPlaylistId,
                title = video.title,
                channelName = video.channelName,
                description = video.description,
                duration = video.duration,
                thumbnailUrl = video.thumbnailUrl,
                notes = video.notes
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
