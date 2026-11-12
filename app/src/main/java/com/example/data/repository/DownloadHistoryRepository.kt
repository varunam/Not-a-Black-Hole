package com.example.data.repository

import com.example.data.local.DownloadDao
import com.example.data.local.DownloadItem
import kotlinx.coroutines.flow.Flow

class DownloadHistoryRepository(
    private val downloadDao: DownloadDao
) {
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun recordDownload(
        title: String,
        originalUrl: String,
        platform: String,
        filePath: String,
        contentUri: String,
        fileSizeBytes: Long,
        durationSeconds: Long = 0
    ): Long {
        val item = DownloadItem(
            title = title,
            originalUrl = originalUrl,
            platform = platform,
            filePath = filePath,
            contentUri = contentUri,
            fileSizeBytes = fileSizeBytes,
            durationSeconds = durationSeconds
        )
        return downloadDao.insertDownload(item)
    }

    suspend fun removeDownload(id: Long) {
        downloadDao.deleteById(id)
    }

    suspend fun clearHistory() {
        downloadDao.clearAll()
    }
}
