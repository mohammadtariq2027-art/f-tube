package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val type: String, // "CHANNEL" or "PLAYLIST"
    val timestamp: Long = System.currentTimeMillis()
)
