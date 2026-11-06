package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.AppPreferenceEntity
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusTubeDao {

    // === Groups ===
    @Query("SELECT * FROM subscription_groups ORDER BY id ASC")
    fun getAllGroups(): Flow<List<SubscriptionGroupEntity>>

    @Query("SELECT * FROM subscription_groups WHERE isPinnedHome = 1 LIMIT 1")
    fun getPinnedGroup(): Flow<SubscriptionGroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: SubscriptionGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<SubscriptionGroupEntity>)

    @Query("DELETE FROM subscription_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("UPDATE subscription_groups SET isPinnedHome = 0")
    suspend fun clearPinnedGroups()

    @Query("UPDATE subscription_groups SET isPinnedHome = 1 WHERE id = :groupId")
    suspend fun markGroupAsPinned(groupId: Long)

    @Transaction
    suspend fun setPinnedGroup(groupId: Long) {
        clearPinnedGroups()
        clearPinnedPlaylists()
        markGroupAsPinned(groupId)
    }

    // === Channels in Groups ===
    @Query("SELECT * FROM channels WHERE groupId = :groupId ORDER BY name ASC")
    fun getChannelsForGroup(groupId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY name ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteChannel(id: Long)

    @Query("UPDATE channels SET groupId = :newGroupId WHERE id = :channelId")
    suspend fun updateChannelGroup(channelId: Long, newGroupId: Long)

    // === Playlists ===
    @Query("SELECT * FROM playlists ORDER BY isPinnedHome DESC, id ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isPinnedHome = 1 LIMIT 1")
    fun getPinnedPlaylist(): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("UPDATE playlists SET title = :title WHERE playlistId = :playlistId")
    suspend fun updatePlaylistTitle(playlistId: String, title: String)

    @Query("UPDATE playlists SET isPinnedHome = 0")
    suspend fun clearPinnedPlaylists()

    @Query("UPDATE playlists SET isPinnedHome = 1 WHERE playlistId = :playlistId")
    suspend fun markPlaylistAsPinned(playlistId: String)

    @Transaction
    suspend fun setPinnedPlaylist(playlistId: String) {
        clearPinnedGroups()
        clearPinnedPlaylists()
        markPlaylistAsPinned(playlistId)
    }

    // === Videos ===
    @Query("SELECT * FROM videos WHERE playlistId = :playlistId ORDER BY orderIndex ASC, id ASC")
    fun getVideosForPlaylist(playlistId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE playlistId = :playlistId ORDER BY orderIndex ASC, id ASC")
    suspend fun getVideosForPlaylistSnapshot(playlistId: String): List<VideoEntity>

    // Group videos: Strictly reverse chronological by upload date across all channels in that group
    @Query("""
        SELECT * FROM videos 
        WHERE groupId = :groupId 
        ORDER BY 
            CASE WHEN publishedTimestamp > 0 THEN publishedTimestamp ELSE addedAt END DESC, 
            id DESC
    """)
    fun getVideosForGroup(groupId: Long): Flow<List<VideoEntity>>

    @Query("""
        SELECT * FROM videos 
        WHERE groupId = :groupId 
        ORDER BY 
            CASE WHEN publishedTimestamp > 0 THEN publishedTimestamp ELSE addedAt END DESC, 
            id DESC
    """)
    suspend fun getVideosForGroupSnapshot(groupId: Long): List<VideoEntity>

    @Query("SELECT * FROM channels WHERE groupId = :groupId ORDER BY name ASC")
    suspend fun getChannelsForGroupSnapshot(groupId: Long): List<ChannelEntity>

    @Query("UPDATE channels SET handleOrUrl = :handleOrUrl WHERE id = :channelId")
    suspend fun updateChannelHandle(channelId: Long, handleOrUrl: String)

    @Query("""
        SELECT * FROM videos 
        WHERE channelDbId = :channelId 
        ORDER BY 
            CASE WHEN publishedTimestamp > 0 THEN publishedTimestamp ELSE addedAt END DESC, 
            id DESC
    """)
    fun getVideosForChannel(channelId: Long): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Query("SELECT COUNT(*) FROM videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun countVideoInPlaylist(playlistId: String, videoId: String): Int

    @Query("SELECT COUNT(*) FROM videos WHERE groupId = :groupId AND videoId = :videoId")
    suspend fun countVideoInGroup(groupId: Long, videoId: String): Int

    @Query("DELETE FROM videos WHERE playlistId IS NOT NULL AND id NOT IN (SELECT MIN(id) FROM videos WHERE playlistId IS NOT NULL GROUP BY playlistId, videoId)")
    suspend fun purgeDuplicateVideosInPlaylists(): Int

    @Query("DELETE FROM videos WHERE groupId IS NOT NULL AND id NOT IN (SELECT MIN(id) FROM videos WHERE groupId IS NOT NULL GROUP BY groupId, videoId)")
    suspend fun purgeDuplicateVideosInGroups(): Int

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideo(id: Long)

    @Query("UPDATE videos SET isCompleted = :completed WHERE id = :id")
    suspend fun setVideoCompleted(id: Long, completed: Boolean)

    @Query("UPDATE videos SET notes = :notes WHERE id = :id")
    suspend fun updateVideoNotes(id: Long, notes: String)

    @Query("UPDATE videos SET duration = :duration WHERE videoId = :videoId")
    suspend fun updateVideoDuration(videoId: String, duration: String)

    @Query("UPDATE videos SET uploadDate = :uploadDate, publishedTimestamp = :timestamp WHERE videoId = :videoId")
    suspend fun updateVideoUploadDate(videoId: String, uploadDate: String, timestamp: Long)

    @Query("UPDATE videos SET duration = :duration, uploadDate = :uploadDate, publishedTimestamp = :timestamp WHERE videoId = :videoId")
    suspend fun updateVideoMetadata(videoId: String, duration: String, uploadDate: String, timestamp: Long)

    @Query("UPDATE videos SET title = :title, duration = :duration WHERE id = :id")
    suspend fun updateVideoTitleAndDuration(id: Long, title: String, duration: String)

    @Query("UPDATE videos SET isSyncedToYouTube = :synced, syncedAt = :timestamp WHERE id = :id")
    suspend fun updateVideoYouTubeSync(id: Long, synced: Boolean, timestamp: Long?)

    @Query("UPDATE videos SET isSyncedToYouTube = 1, syncedAt = :timestamp WHERE playlistId = :playlistId")
    suspend fun markAllPlaylistVideosSynced(playlistId: String, timestamp: Long)

    @Query("SELECT * FROM videos WHERE playlistId = :playlistId AND isSyncedToYouTube = 0")
    fun getUnsyncedVideosForPlaylist(playlistId: String): Flow<List<VideoEntity>>

    @Query("UPDATE videos SET orderIndex = orderIndex + :shift WHERE groupId = :groupId")
    suspend fun shiftGroupVideosOrderIndex(groupId: Long, shift: Int)

    @Query("UPDATE videos SET orderIndex = orderIndex + :shift WHERE playlistId = :playlistId")
    suspend fun shiftPlaylistVideosOrderIndex(playlistId: String, shift: Int)

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR channelName LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY id DESC LIMIT 200")
    fun searchAllVideos(query: String): Flow<List<VideoEntity>>

    @Query("""
        SELECT * FROM videos 
        ORDER BY 
            CASE WHEN publishedTimestamp > 0 THEN publishedTimestamp ELSE addedAt END DESC, 
            id DESC
        LIMIT 500
    """)
    fun getAllVideosFlow(): Flow<List<VideoEntity>>

    // === Preferences ===
    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun getPreference(key: String): Flow<String?>

    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreferenceSnapshot(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(pref: AppPreferenceEntity)

    // === Search History ===
    @Query("SELECT * FROM search_history WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getSearchHistory(type: String, limit: Int = 10): Flow<List<com.example.data.local.entity.SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entry: com.example.data.local.entity.SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE `query` = :query AND type = :type")
    suspend fun deleteSearchHistoryItem(query: String, type: String)

    @Query("DELETE FROM search_history WHERE type = :type")
    suspend fun clearSearchHistory(type: String)

    // === Export / Backup Queries (Snapshot lists) ===
    @Query("SELECT * FROM subscription_groups ORDER BY id ASC")
    suspend fun getAllGroupsSnapshot(): List<SubscriptionGroupEntity>

    @Query("SELECT * FROM channels ORDER BY id ASC")
    suspend fun getAllChannelsSnapshot(): List<ChannelEntity>

    @Query("SELECT * FROM playlists ORDER BY id ASC")
    suspend fun getAllPlaylistsSnapshot(): List<PlaylistEntity>

    @Query("SELECT * FROM videos ORDER BY id ASC")
    suspend fun getAllVideosSnapshot(): List<VideoEntity>

    @Query("DELETE FROM subscription_groups")
    suspend fun clearAllGroups()

    @Query("DELETE FROM channels")
    suspend fun clearAllChannels()

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()

    @Query("DELETE FROM videos")
    suspend fun clearAllVideos()
}
