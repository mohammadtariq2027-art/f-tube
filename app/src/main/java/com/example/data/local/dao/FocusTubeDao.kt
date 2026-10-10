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

    @Query("SELECT * FROM videos WHERE groupId = :groupId ORDER BY orderIndex ASC, id ASC")
    fun getVideosForGroup(groupId: Long): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE channelDbId = :channelId ORDER BY id ASC")
    fun getVideosForChannel(channelId: Long): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideo(id: Long)

    @Query("UPDATE videos SET isCompleted = :completed WHERE id = :id")
    suspend fun setVideoCompleted(id: Long, completed: Boolean)

    // === Preferences ===
    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun getPreference(key: String): Flow<String?>

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
