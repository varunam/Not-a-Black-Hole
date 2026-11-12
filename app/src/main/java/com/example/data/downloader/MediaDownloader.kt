package com.example.data.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

sealed class DownloadProgress {
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentage: Float, // 0.0 to 1.0
        val speedBytesPerSec: Long
    ) : DownloadProgress()

    data class Completed(
        val contentUri: String,
        val filePath: String,
        val totalBytes: Long,
        val fileName: String
    ) : DownloadProgress()

    data class Error(val message: String) : DownloadProgress()
}

class MediaDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "MediaDownloader"
    }

    fun downloadVideo(
        videoUrl: String,
        suggestedTitle: String,
        platformName: String
    ): Flow<DownloadProgress> = flow {
        Log.d(TAG, "Starting download stream from: $videoUrl")
        val timestamp = System.currentTimeMillis()
        val safeTitle = suggestedTitle
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(30)
            .ifBlank { "clip" }
        // Guarantee the filename explicitly ends with .mp4
        val fileName = "NotABlackHole_${platformName.lowercase()}_${safeTitle}_$timestamp.mp4"

        val request = Request.Builder()
            .url(videoUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Connection", "keep-alive")
            .build()

        // 1. Always create a real physical file in the app's external files directory with the .mp4 extension
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        moviesDir.mkdirs()
        val physicalFile = File(moviesDir, fileName)

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadProgress.Error("Server returned code ${response.code}"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(DownloadProgress.Error("Empty response body from media server"))
                return@flow
            }

            val totalBytes = body.contentLength()
            val inputStream: InputStream = body.byteStream()
            val buffer = ByteArray(16 * 1024)
            var bytesCopied: Long = 0
            var lastUpdate = System.currentTimeMillis()
            var bytesSinceLastUpdate: Long = 0
            var currentSpeed: Long = 0

            // Stream directly into the physical .mp4 file
            FileOutputStream(physicalFile).use { out ->
                inputStream.use { input ->
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        out.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        bytesSinceLastUpdate += bytes

                        val now = System.currentTimeMillis()
                        val diff = now - lastUpdate
                        if (diff >= 250) { // update progress every 250ms
                            currentSpeed = (bytesSinceLastUpdate * 1000) / diff
                            lastUpdate = now
                            bytesSinceLastUpdate = 0

                            val progressFraction = if (totalBytes > 0) {
                                (bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f)
                            } else {
                                0.5f // Indeterminate
                            }

                            emit(
                                DownloadProgress.Progress(
                                    bytesDownloaded = bytesCopied,
                                    totalBytes = totalBytes,
                                    percentage = progressFraction,
                                    speedBytesPerSec = currentSpeed
                                )
                            )
                        }

                        bytes = input.read(buffer)
                    }
                    out.flush()
                }
            }

            // 2. Also register in Android's public MediaStore so it appears in the device's Gallery
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/NotABlackHole")
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val mediaUri = context.contentResolver.insert(collection, values)
                    if (mediaUri != null) {
                        context.contentResolver.openOutputStream(mediaUri)?.use { out ->
                            physicalFile.inputStream().use { inp ->
                                inp.copyTo(out)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore gallery export notice: ${e.message}")
            }

            // 3. Generate a FileProvider content URI that explicitly retains the .mp4 extension
            val fileProviderUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    physicalFile
                )
            } catch (e: Exception) {
                Uri.fromFile(physicalFile)
            }

            Log.d(TAG, "Download finished successfully: file=${physicalFile.absolutePath}, uri=$fileProviderUri")
            emit(
                DownloadProgress.Completed(
                    contentUri = fileProviderUri.toString(),
                    filePath = physicalFile.absolutePath,
                    totalBytes = bytesCopied,
                    fileName = fileName
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            if (physicalFile.exists()) {
                physicalFile.delete()
            }
            emit(DownloadProgress.Error(e.message ?: "Download interrupted or network failed"))
        }
    }.flowOn(Dispatchers.IO)
}
