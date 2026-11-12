package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.extractor.VideoExtractor
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.BlackHoleTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming shared links via Android Share Sheet
        handleIncomingIntent(intent)

        setContent {
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
            BlackHoleTheme(appTheme = currentTheme) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        val rawText = when (action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.clipData?.getItemAt(0)?.text?.toString()
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            }
            Intent.ACTION_VIEW -> {
                intent.dataString
            }
            else -> null
        }

        if (!rawText.isNullOrBlank()) {
            val extractedUrl = VideoExtractor.extractUrlFromText(rawText)
            if (extractedUrl.isNotBlank() && (extractedUrl.startsWith("http://") || extractedUrl.startsWith("https://"))) {
                viewModel.processUrl(extractedUrl)
            }
        }
    }
}
