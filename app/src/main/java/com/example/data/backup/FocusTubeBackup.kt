package com.example.data.backup

import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FocusTubeBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val app: String = "FocusTube",
    val groups: List<SubscriptionGroupEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val videos: List<VideoEntity> = emptyList()
)
