package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BlackHoleSingularity
import com.example.ui.components.HistoryBottomSheet
import com.example.ui.components.IntroScreen
import com.example.ui.components.ManualUrlDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val speed by viewModel.downloadSpeed.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val lastDetectedUrl by viewModel.lastDetectedUrl.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val autoShareEnabled by viewModel.autoShareEnabled.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val showIntro by viewModel.showIntro.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var lastSharedVideoPath by remember { mutableStateOf<String?>(null) }

    // Check clipboard periodically on resume or load
    LaunchedEffect(Unit) {
        val clip = clipboardManager.getText()?.text
        viewModel.updateClipboardPreview(clip)
    }

    // Auto-open system app chooser immediately after download completes if enabled
    LaunchedEffect(uiState, autoShareEnabled) {
        if (uiState is DownloadUiState.Success && autoShareEnabled) {
            val success = uiState as DownloadUiState.Success
            if (lastSharedVideoPath != success.filePath) {
                lastSharedVideoPath = success.filePath
                Toast.makeText(context, "Saved to Gallery! Opening share options...", Toast.LENGTH_SHORT).show()
                launchShareChooser(context, success.contentUri, success.filePath, success.title)
            }
        }
    }

    Scaffold(
        containerColor = currentTheme.backgroundColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(currentTheme.backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Branding - "NOT A BLACK HOLE"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(currentTheme.accentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NOT A BLACK HOLE",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.5.sp
                        )
                    }

                    // Top Action Buttons (Link, History, Settings)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Manual Link Enter Button
                        IconButton(
                            onClick = { showManualDialog = true },
                            modifier = Modifier.testTag("manual_link_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLink,
                                contentDescription = "Enter Link Manually",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // History button with count badge
                        IconButton(
                            onClick = { showHistorySheet = true },
                            modifier = Modifier.testTag("history_button")
                        ) {
                            if (history.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = currentTheme.accentColor,
                                            contentColor = Color.Black
                                        ) {
                                            Text(history.size.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Download History",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Download History",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Settings Button
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Middle: Singularity Stage
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    BlackHoleSingularity(
                        uiState = uiState,
                        downloadProgress = progress,
                        downloadSpeed = speed,
                        appTheme = currentTheme,
                        onClick = {
                            val currentClipboard = clipboardManager.getText()?.text
                            viewModel.onBlackHoleTapped(currentClipboard)
                        }
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    // Dynamic Status Prompt
                    when (val state = uiState) {
                        is DownloadUiState.Idle -> {
                            Text(
                                text = "Copy video link from Instagram or Twitter/X",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the center disc to absorb and download",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Quick test helpers
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(currentTheme.surfaceColor)
                                        .border(1.dp, currentTheme.surfaceBorder, RoundedCornerShape(20.dp))
                                        .clickable {
                                            val demoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(demoUrl))
                                            viewModel.updateClipboardPreview(demoUrl)
                                            Toast.makeText(context, "Test link copied! Tap disc to absorb.", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                        .testTag("copy_test_link_button")
                                ) {
                                    Text(
                                        text = "📋 Copy Test Link",
                                        color = currentTheme.primaryColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(currentTheme.accentColor.copy(alpha = 0.15f))
                                        .border(1.dp, currentTheme.accentColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                        .clickable {
                                            viewModel.processUrl("https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4")
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                        .testTag("direct_test_download_button")
                                ) {
                                    Text(
                                        text = "⚡ Instant Test Video",
                                        color = currentTheme.accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        is DownloadUiState.Extracting -> {
                            Text(
                                text = "Resolving ${state.platform.displayName} Stream...",
                                color = currentTheme.accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Extracting video payload",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        is DownloadUiState.Downloading -> {
                            Text(
                                text = state.video.title.ifBlank { "Downloading Video" },
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Saving into Movies/BlackHole",
                                color = currentTheme.accentColor,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        is DownloadUiState.Success -> {
                            Text(
                                text = "Download Complete",
                                color = SuccessGreen,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Saved to Gallery",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Share chooser button to share immediately
                            Button(
                                onClick = {
                                    launchShareChooser(context, state.contentUri, state.filePath, state.title)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = currentTheme.accentColor,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("share_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send to WhatsApp / Apps",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        is DownloadUiState.Error -> {
                            Text(
                                text = state.message,
                                color = ErrorRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the center disc to retry",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Bottom: Clipboard Detection Pill & Platform Badges
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val detectedUrl = lastDetectedUrl
                    if (!detectedUrl.isNullOrBlank() && uiState is DownloadUiState.Idle) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = currentTheme.surfaceColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, currentTheme.surfaceBorder, RoundedCornerShape(14.dp))
                                .clickable { viewModel.processUrl(detectedUrl) }
                                .testTag("detected_url_pill")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Clipboard",
                                        tint = currentTheme.accentColor,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Link Detected in Clipboard",
                                            color = currentTheme.accentColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = detectedUrl,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(currentTheme.accentColor)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Absorb",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Platform badges
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlatformBadge(name = "Instagram Reels", color = currentTheme.accentColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        PlatformBadge(name = "Twitter / X", color = currentTheme.primaryColor)
                    }
                }
            }
        }
    }

    // History Bottom Sheet
    if (showHistorySheet) {
        HistoryBottomSheet(
            downloads = history,
            onDismiss = { showHistorySheet = false },
            onDeleteItem = { viewModel.deleteHistoryItem(it) },
            onClearAll = { viewModel.clearAllHistory() }
        )
    }

    // Manual URL Dialog
    if (showManualDialog) {
        ManualUrlDialog(
            initialUrl = lastDetectedUrl ?: "",
            onDismiss = { showManualDialog = false },
            onSubmitUrl = { url ->
                showManualDialog = false
                viewModel.processUrl(url)
            }
        )
    }

    // Settings & Theme Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            currentTheme = currentTheme,
            autoShareEnabled = autoShareEnabled,
            hapticsEnabled = hapticsEnabled,
            onSelectTheme = { viewModel.setTheme(it) },
            onToggleAutoShare = { viewModel.setAutoShare(it) },
            onToggleHaptics = { viewModel.setHaptics(it) },
            onReplayIntro = {
                showSettingsDialog = false
                viewModel.openIntroFromSettings()
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // First-Launch / Replayed Onboarding Walkthrough
    AnimatedVisibility(
        visible = showIntro,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        IntroScreen(
            appTheme = currentTheme,
            onComplete = { viewModel.completeIntro() }
        )
    }
}

private fun launchShareChooser(
    context: Context,
    contentUriStr: String,
    filePath: String,
    title: String
) {
    try {
        val file = File(filePath)
        val validFileName = if (file.exists() && file.name.endsWith(".mp4", ignoreCase = true)) {
            file.name
        } else if (title.endsWith(".mp4", ignoreCase = true)) {
            title
        } else {
            val safe = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30).ifBlank { "video" }
            "$safe.mp4"
        }

        val uri = if (file.exists()) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else if (contentUriStr.startsWith("content://")) {
            Uri.parse(contentUriStr)
        } else {
            Uri.parse(contentUriStr)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, validFileName)
            putExtra(Intent.EXTRA_TITLE, validFileName)
            clipData = android.content.ClipData.newUri(context.contentResolver, validFileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share $validFileName via...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open share chooser: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PlatformBadge(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = name,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
