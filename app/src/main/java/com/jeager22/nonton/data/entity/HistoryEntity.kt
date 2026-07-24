package com.jeager22.nonton.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val author: String,
    val authorId: String,
    val thumbnailUrl: String,
    val lengthSeconds: Int,
    val watchedAt: Long
)
