package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class IntroPageData(
    val badge: String,
    val title: String,
    val description: String,
    val highlightNote: String
)

private val introPages = listOf(
    IntroPageData(
        badge = "01  •  ONE-TAP ABSORPTION",
        title = "Copy Any Video Link.\nTap the Disc.",
        description = "Copy a public video link from Instagram or Twitter/X, then tap the center disc. It pulls the stream and saves a clean .mp4 straight to your Gallery.",
        highlightNote = "Saved locally inside Movies/NotABlackHole"
    ),
    IntroPageData(
        badge = "02  •  ZERO-COPY SHORTCUT",
        title = "Share Straight From\nInstagram & Twitter/X",
        description = "Don't even switch apps to copy and paste. Tap Share on any public post or Reel and choose Not a Black Hole—your download starts instantly.",
        highlightNote = "Works directly from Android's Share Sheet"
    ),
    IntroPageData(
        badge = "03  •  INSTANT RE-SHARE",
        title = "Ready to Send\nThe Moment It Saves",
        description = "As soon as a video finishes downloading, the share menu opens automatically so you can forward it to WhatsApp, Email, or any app. Customize Auto-Share and themes anytime in Settings.",
        highlightNote = "Public links only • Respect creators' rights"
    )
)

@Composable
fun IntroScreen(
    appTheme: AppTheme,
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { introPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == introPages.lastIndex

    BackHandler {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appTheme.backgroundColor)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("intro_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Wordmark + Skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(appTheme.accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOT A BLACK HOLE",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp
                    )
                }

                AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("intro_skip_button")
                    ) {
                        Text(
                            text = "Skip",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Middle Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val page = introPages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Custom luxury artwork per page
                    when (pageIndex) {
                        0 -> AbsorbArtwork(appTheme = appTheme)
                        1 -> DirectShareArtwork(appTheme = appTheme)
                        else -> InstantReshareArtwork(appTheme = appTheme)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Step badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(appTheme.surfaceColor)
                            .border(1.dp, appTheme.surfaceBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = page.badge,
                            color = appTheme.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = page.title,
                        color = TextPrimary,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = page.description,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Subtle bottom pill note
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(appTheme.accentColor.copy(alpha = 0.08f))
                            .border(1.dp, appTheme.accentColor.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = if (pageIndex == 2) Icons.Default.Lock else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = appTheme.accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = page.highlightNote,
                            color = appTheme.primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Indicators & Primary Action
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(introPages.size) { index ->
                        val active = pagerState.currentPage == index
                        val dotWidth by animateDpAsState(
                            targetValue = if (active) 24.dp else 7.dp,
                            animationSpec = tween(250),
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(
                                    if (active) appTheme.accentColor else appTheme.surfaceBorder
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        if (isLastPage) {
                            onComplete()
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLastPage) appTheme.accentColor else appTheme.surfaceColor,
                        contentColor = if (isLastPage) Color.Black else TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .border(
                            width = 1.dp,
                            color = if (isLastPage) appTheme.accentColor else appTheme.surfaceBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag(if (isLastPage) "intro_get_started_button" else "intro_next_button")
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Continue",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun AbsorbArtwork(appTheme: AppTheme) {
    val infiniteTransition = rememberInfiniteTransition(label = "absorb_art")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        appTheme.accentColor.copy(alpha = 0.20f),
                        appTheme.primaryColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR * pulse
                ),
                radius = maxR * pulse,
                center = center
            )

            // Precision hairline concentric rings
            listOf(0.88f, 0.72f, 0.56f).forEachIndexed { idx, ratio ->
                drawCircle(
                    color = if (idx == 1) appTheme.accentColor.copy(alpha = 0.35f)
                    else appTheme.primaryColor.copy(alpha = 0.16f),
                    radius = maxR * ratio,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // Orbiting accent arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        appTheme.accentColor.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = rotation,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - maxR * 0.72f, center.y - maxR * 0.72f),
                size = androidx.compose.ui.geometry.Size(maxR * 1.44f, maxR * 1.44f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Core matte disc
        Surface(
            shape = CircleShape,
            color = appTheme.surfaceColor,
            modifier = Modifier
                .size(92.dp)
                .border(1.5.dp, appTheme.accentColor.copy(alpha = 0.5f), CircleShape)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Absorb Video",
                    tint = appTheme.accentColor,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ABSORB",
                    color = appTheme.primaryColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
            }
        }

        // Floating clipboard chip
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = appTheme.surfaceColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp)
                .border(1.dp, appTheme.surfaceBorder, RoundedCornerShape(10.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = appTheme.accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Link Copied",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DirectShareArtwork(appTheme: AppTheme) {
    // Clean visual step card illustrating: Social App Share -> Not a Black Hole (no 3rd-party logos)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(appTheme.surfaceColor)
            .border(1.dp, appTheme.surfaceBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepVisualRow(
            icon = Icons.Default.IosShare,
            step = "STEP 1",
            label = "Inside Instagram or Twitter/X, tap Share",
            appTheme = appTheme,
            highlighted = false
        )

        Box(
            modifier = Modifier
                .padding(start = 18.dp)
                .width(1.dp)
                .height(10.dp)
                .background(appTheme.surfaceBorder)
        )

        StepVisualRow(
            icon = Icons.Default.Share,
            step = "STEP 2",
            label = "Select \"Not a Black Hole\" from apps",
            appTheme = appTheme,
            highlighted = true
        )

        Box(
            modifier = Modifier
                .padding(start = 18.dp)
                .width(1.dp)
                .height(10.dp)
                .background(appTheme.surfaceBorder)
        )

        StepVisualRow(
            icon = Icons.Default.Download,
            step = "DONE",
            label = "Video downloads & saves automatically",
            appTheme = appTheme,
            highlighted = false
        )
    }
}

@Composable
private fun InstantReshareArtwork(appTheme: AppTheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(appTheme.surfaceColor)
            .border(1.dp, appTheme.surfaceBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "READY TO SHARE",
                    color = appTheme.accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "saved_video_clip.mp4",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(appTheme.accentColor.copy(alpha = 0.16f))
                    .border(1.dp, appTheme.accentColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = appTheme.accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Mock destination pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("WhatsApp", "Telegram", "Email", "More").forEach { dest ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(appTheme.backgroundColor)
                        .border(1.dp, appTheme.surfaceBorder, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dest,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StepVisualRow(
    icon: ImageVector,
    step: String,
    label: String,
    appTheme: AppTheme,
    highlighted: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlighted) appTheme.accentColor.copy(alpha = 0.12f)
                else appTheme.backgroundColor.copy(alpha = 0.6f)
            )
            .border(
                width = 1.dp,
                color = if (highlighted) appTheme.accentColor.copy(alpha = 0.45f) else appTheme.surfaceBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (highlighted) appTheme.accentColor else appTheme.surfaceColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) Color.Black else appTheme.primaryColor,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = step,
                color = if (highlighted) appTheme.accentColor else TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
