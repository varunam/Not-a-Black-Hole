package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_items")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val originalUrl: String,
    val platform: String, // "INSTAGRAM" or "TWITTER"
    val filePath: String,
    val contentUri: String,
    val fileSizeBytes: Long,
    val durationSeconds: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
