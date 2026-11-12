package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.downloader.DownloadProgress
import com.example.data.downloader.MediaDownloader
import com.example.data.extractor.VideoExtractor
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadItem
import com.example.data.model.ExtractedVideo
import com.example.data.model.ExtractionResult
import com.example.data.model.SocialPlatform
import com.example.data.repository.DownloadHistoryRepository
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed class DownloadUiState {
    object Idle : DownloadUiState()
    data class Extracting(val url: String, val platform: SocialPlatform) : DownloadUiState()
    data class Downloading(
        val video: ExtractedVideo,
        val progress: Float,
        val speed: String
    ) : DownloadUiState()
    data class Success(val title: String, val filePath: String, val contentUri: String) : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("not_a_black_hole_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getInstance(application)
    private val historyRepository = DownloadHistoryRepository(database.downloadDao())
    private val videoExtractor = VideoExtractor()
    private val mediaDownloader = MediaDownloader(application)

    // User Preferences
    private val _currentTheme = MutableStateFlow(loadInitialTheme())
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private val _autoShareEnabled = MutableStateFlow(prefs.getBoolean("auto_share_enabled", true))
    val autoShareEnabled: StateFlow<Boolean> = _autoShareEnabled.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean("haptics_enabled", true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    val history: StateFlow<List<DownloadItem>> = historyRepository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadSpeed = MutableStateFlow("")
    val downloadSpeed: StateFlow<String> = _downloadSpeed.asStateFlow()

    private val _lastDetectedUrl = MutableStateFlow<String?>(null)
    val lastDetectedUrl: StateFlow<String?> = _lastDetectedUrl.asStateFlow()

    private fun loadInitialTheme(): AppTheme {
        val savedName = prefs.getString("selected_theme", AppTheme.ELEGANT_OBSIDIAN.name)
        return try {
            AppTheme.valueOf(savedName ?: AppTheme.ELEGANT_OBSIDIAN.name)
        } catch (_: Exception) {
            AppTheme.ELEGANT_OBSIDIAN
        }
    }

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun setAutoShare(enabled: Boolean) {
        _autoShareEnabled.value = enabled
        prefs.edit().putBoolean("auto_share_enabled", enabled).apply()
    }

    fun setHaptics(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()
    }

    fun updateClipboardPreview(text: String?) {
        val trimmed = text?.trim() ?: ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            _lastDetectedUrl.value = trimmed
        }
    }

    fun onBlackHoleTapped(clipboardText: String?) {
        vibratePhone(40)
        when (_uiState.value) {
            is DownloadUiState.Downloading -> {
                return
            }
            is DownloadUiState.Extracting -> {
                return
            }
            is DownloadUiState.Success, is DownloadUiState.Error -> {
                _uiState.value = DownloadUiState.Idle
                _downloadProgress.value = 0f
                _downloadSpeed.value = ""
            }
            is DownloadUiState.Idle -> {
                val targetUrl = clipboardText?.trim()?.takeIf { it.isNotBlank() }
                    ?: _lastDetectedUrl.value
                if (targetUrl.isNullOrBlank()) {
                    _uiState.value = DownloadUiState.Error("Clipboard is empty! Copy a video link first.")
                } else {
                    processUrl(targetUrl)
                }
            }
        }
    }

    fun processUrl(rawUrl: String) {
        val platform = videoExtractor.detectPlatform(rawUrl)
        _lastDetectedUrl.value = rawUrl
        _uiState.value = DownloadUiState.Extracting(rawUrl, platform)
        _downloadProgress.value = 0f
        _downloadSpeed.value = ""

        viewModelScope.launch {
            when (val extraction = videoExtractor.extract(rawUrl)) {
                is ExtractionResult.Failure -> {
                    _uiState.value = DownloadUiState.Error(extraction.error)
                }
                is ExtractionResult.Success -> {
                    startDownload(extraction.video, rawUrl)
                }
            }
        }
    }

    private fun startDownload(video: ExtractedVideo, originalUrl: String) {
        _uiState.value = DownloadUiState.Downloading(video, 0f, "")

        viewModelScope.launch {
            mediaDownloader.downloadVideo(
                videoUrl = video.directVideoUrl,
                suggestedTitle = video.title,
                platformName = video.platform.name
            ).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        _downloadProgress.value = progress.percentage
                        val mbSpeed = progress.speedBytesPerSec / (1024.0 * 1024.0)
                        val speedStr = if (mbSpeed >= 0.1) {
                            String.format(Locale.US, "%.1f MB/s", mbSpeed)
                        } else {
                            "${progress.speedBytesPerSec / 1024} KB/s"
                        }
                        _downloadSpeed.value = speedStr
                        _uiState.value = DownloadUiState.Downloading(video, progress.percentage, speedStr)
                    }

                    is DownloadProgress.Completed -> {
                        _downloadProgress.value = 1f
                        _uiState.value = DownloadUiState.Success(
                            title = video.title,
                            filePath = progress.filePath,
                            contentUri = progress.contentUri
                        )
                        vibratePhone(150)

                        // Save to Room history
                        historyRepository.recordDownload(
                            title = video.title,
                            originalUrl = originalUrl,
                            platform = video.platform.name,
                            filePath = progress.filePath,
                            contentUri = progress.contentUri,
                            fileSizeBytes = progress.totalBytes,
                            durationSeconds = video.durationSeconds
                        )
                    }

                    is DownloadProgress.Error -> {
                        _uiState.value = DownloadUiState.Error(progress.message)
                    }
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = DownloadUiState.Idle
        _downloadProgress.value = 0f
        _downloadSpeed.value = ""
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyRepository.removeDownload(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    private fun vibratePhone(durationMs: Long) {
        if (!_hapticsEnabled.value) return
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }
}
