package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_groups")
data class SubscriptionGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isPinnedHome: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val handleOrUrl: String = "",
    val groupId: Long, // Foreign key to SubscriptionGroupEntity
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: String,
    val title: String,
    val isPinnedHome: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String, // 11-char YouTube ID
    val playlistId: String? = null, // Set if in a playlist
    val groupId: Long? = null, // Set if in a channel group
    val channelDbId: Long? = null, // Set if in a channel
    val title: String,
    val channelName: String,
    val description: String = "", // Video description (timestamps, links, summary)
    val duration: String = "Video",
    val thumbnailUrl: String = "",
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
