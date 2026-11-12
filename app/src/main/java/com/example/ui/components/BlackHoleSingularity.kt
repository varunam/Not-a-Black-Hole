package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DownloadUiState
import com.example.ui.theme.AppTheme
import com.example.ui.theme.CosmicCyan
import com.example.ui.theme.CosmicPink
import com.example.ui.theme.CosmicPurple
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VoidBlack
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BlackHoleSingularity(
    uiState: DownloadUiState,
    downloadProgress: Float, // 0f to 1f
    downloadSpeed: String,
    appTheme: AppTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    // Infinite transitions for smooth celestial rotation & breathing
    val infiniteTransition = rememberInfiniteTransition(label = "CosmicVortex")

    // Rotation speed changes based on state
    val targetDuration = when (uiState) {
        is DownloadUiState.Extracting -> 2200
        is DownloadUiState.Downloading -> 3500
        else -> if (appTheme == AppTheme.ELEGANT_OBSIDIAN) 14000 else 9000
    }

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = targetDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VortexRotation"
    )

    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = targetDuration * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CounterRotation"
    )

    // Breathing pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingPulse"
    )

    val pulseGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val effectiveScale = scaleAnim.value * if (uiState is DownloadUiState.Idle) pulseScale else 1.0f

    Box(
        modifier = modifier
            .size(280.dp)
            .scale(effectiveScale)
            .testTag("black_hole_button")
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                coroutineScope.launch {
                    scaleAnim.animateTo(0.92f, animationSpec = spring(dampingRatio = 0.45f))
                    scaleAnim.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f))
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f - 16f
            val singularityRadius = outerRadius * 0.72f

            when (appTheme) {
                AppTheme.ELEGANT_OBSIDIAN -> {
                    // CRED-INSPIRED SCULPTURAL OBSIDIAN & PLATINUM ELEGANCE
                    // 1. Subtle warm ambient champagne-platinum halo
                    val ambientGlow = when (uiState) {
                        is DownloadUiState.Success -> SuccessGreen.copy(alpha = 0.25f * pulseGlowAlpha)
                        is DownloadUiState.Error -> ErrorRed.copy(alpha = 0.25f)
                        else -> Color(0xFFF59E0B).copy(alpha = 0.14f * pulseGlowAlpha)
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ambientGlow,
                                Color(0xFFE2E8F0).copy(alpha = 0.05f * pulseGlowAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outerRadius * 1.25f
                        ),
                        radius = outerRadius * 1.25f,
                        center = center
                    )

                    // 2. Precision-machined Concentric Hairline Rings (CRED Aesthetic)
                    val ringColor = Color(0xFF2E2E3E)
                    drawCircle(
                        color = ringColor,
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                    drawCircle(
                        color = ringColor.copy(alpha = 0.6f),
                        radius = outerRadius * 0.88f,
                        center = center,
                        style = Stroke(width = 0.8f)
                    )

                    // 3. Rotating Metallic Titanium Light Reflection Sweep
                    rotate(rotation, pivot = center) {
                        val metallicSweep = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFE2E8F0).copy(alpha = 0.65f), // Platinum gleam
                                Color(0xFFF59E0B).copy(alpha = 0.70f), // Warm Champagne gold accent
                                Color.Transparent
                            ),
                            center = center
                        )

                        // Polished outer bevel ring
                        drawCircle(
                            brush = metallicSweep,
                            radius = singularityRadius + 8f,
                            center = center,
                            style = Stroke(width = 2.5f)
                        )

                        // Delicate orbital ticks (Sculptural luxury watch bezel inspired)
                        for (i in 0 until 24) {
                            val angle = (i * 15.0) * (Math.PI / 180.0)
                            val r = singularityRadius + 14f
                            val px = center.x + (r * cos(angle)).toFloat()
                            val py = center.y + (r * sin(angle)).toFloat()
                            drawCircle(
                                color = if (i % 6 == 0) Color(0xFFF59E0B) else Color(0xFF94A3B8).copy(alpha = 0.4f),
                                radius = if (i % 6 == 0) 2f else 1f,
                                center = Offset(px, py)
                            )
                        }
                    }

                    // 4. Circular Progress Arc (When downloading)
                    if (uiState is DownloadUiState.Downloading) {
                        val strokeWidth = 6.5f
                        val sweepAngle = 360f * downloadProgress.coerceIn(0f, 1f)

                        drawCircle(
                            color = Color(0xFF262638),
                            radius = singularityRadius + 8f,
                            center = center,
                            style = Stroke(width = strokeWidth)
                        )

                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFE2E8F0),
                                    Color(0xFFF59E0B),
                                    Color(0xFFE2E8F0)
                                ),
                                center = center
                            ),
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - (singularityRadius + 8f), center.y - (singularityRadius + 8f)),
                            size = Size((singularityRadius + 8f) * 2, (singularityRadius + 8f) * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // 5. Matte Obsidian Disc Core (Tactile depth)
                    drawCircle(
                        color = Color(0xFF09090D),
                        radius = singularityRadius,
                        center = center
                    )

                    // Inner beveled shade
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF050508).copy(alpha = 0.8f),
                                Color(0xFF222230).copy(alpha = 0.5f)
                            ),
                            center = center,
                            radius = singularityRadius
                        ),
                        radius = singularityRadius,
                        center = center
                    )
                }

                AppTheme.ROYAL_GOLD -> {
                    // ROYAL MIDNIGHT GOLD
                    val goldGlow = Color(0xFFF59E0B).copy(alpha = 0.28f * pulseGlowAlpha)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(goldGlow, Color.Transparent),
                            center = center,
                            radius = outerRadius * 1.2f
                        ),
                        radius = outerRadius * 1.2f,
                        center = center
                    )

                    rotate(rotation, pivot = center) {
                        val goldSweep = Brush.sweepGradient(
                            colors = listOf(Color(0xFFD97706), Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFD97706)),
                            center = center
                        )
                        drawCircle(brush = goldSweep, radius = singularityRadius + 8f, center = center, style = Stroke(width = 4f))
                    }

                    if (uiState is DownloadUiState.Downloading) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFFDE68A)), center),
                            startAngle = -90f,
                            sweepAngle = 360f * downloadProgress.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = Offset(center.x - (singularityRadius + 8f), center.y - (singularityRadius + 8f)),
                            size = Size((singularityRadius + 8f) * 2, (singularityRadius + 8f) * 2),
                            style = Stroke(width = 7f, cap = StrokeCap.Round)
                        )
                    }

                    drawCircle(color = Color(0xFF0A0906), radius = singularityRadius, center = center)
                }

                AppTheme.TITANIUM_MONO -> {
                    // MINIMAL MONOCHROME
                    drawCircle(
                        color = Color(0xFF222226),
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                    rotate(rotation, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent, Color.White.copy(alpha = 0.8f)), center),
                            radius = singularityRadius + 6f,
                            center = center,
                            style = Stroke(width = 2f)
                        )
                    }
                    if (uiState is DownloadUiState.Downloading) {
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = 360f * downloadProgress.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = Offset(center.x - (singularityRadius + 6f), center.y - (singularityRadius + 6f)),
                            size = Size((singularityRadius + 6f) * 2, (singularityRadius + 6f) * 2),
                            style = Stroke(width = 6f, cap = StrokeCap.Round)
                        )
                    }
                    drawCircle(color = Color(0xFF060607), radius = singularityRadius, center = center)
                }

                AppTheme.CYBER_NEON -> {
                    // ORIGINAL COSMIC NEON EVENT HORIZON
                    val haloColors = when (uiState) {
                        is DownloadUiState.Success -> listOf(SuccessGreen.copy(alpha = 0.35f * pulseGlowAlpha), Color.Transparent)
                        is DownloadUiState.Error -> listOf(ErrorRed.copy(alpha = 0.35f), Color.Transparent)
                        else -> listOf(CosmicPurple.copy(alpha = 0.40f * pulseGlowAlpha), CosmicCyan.copy(alpha = 0.20f * pulseGlowAlpha), Color.Transparent)
                    }

                    drawCircle(brush = Brush.radialGradient(colors = haloColors, center = center, radius = outerRadius * 1.15f), radius = outerRadius * 1.15f, center = center)

                    rotate(rotation, pivot = center) {
                        val diskSweep = Brush.sweepGradient(listOf(CosmicPurple, CosmicCyan, CosmicPink, CosmicPurple), center = center)
                        drawCircle(brush = diskSweep, radius = singularityRadius + 10f, center = center, style = Stroke(width = 6f))

                        for (i in 0 until 12) {
                            val angle = (i * 30.0) * (Math.PI / 180.0)
                            val r = singularityRadius + 18f + (i % 3) * 6f
                            val px = center.x + (r * cos(angle)).toFloat()
                            val py = center.y + (r * sin(angle)).toFloat()
                            drawCircle(color = if (i % 2 == 0) CosmicCyan.copy(alpha = 0.8f) else CosmicPink.copy(alpha = 0.8f), radius = if (i % 3 == 0) 3.5f else 2f, center = Offset(px, py))
                        }
                    }

                    rotate(counterRotation, pivot = center) {
                        for (i in 0 until 8) {
                            val angle = (i * 45.0 + 15.0) * (Math.PI / 180.0)
                            val r = outerRadius * 0.90f
                            val px = center.x + (r * cos(angle)).toFloat()
                            val py = center.y + (r * sin(angle)).toFloat()
                            drawCircle(color = CosmicPurple.copy(alpha = 0.6f), radius = 2.5f, center = Offset(px, py))
                        }
                    }

                    if (uiState is DownloadUiState.Downloading) {
                        drawCircle(color = Color.White.copy(alpha = 0.15f), radius = singularityRadius + 6f, center = center, style = Stroke(width = 10f))
                        drawArc(
                            brush = Brush.sweepGradient(listOf(CosmicCyan, CosmicPink, CosmicPurple, CosmicCyan), center = center),
                            startAngle = -90f,
                            sweepAngle = 360f * downloadProgress.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = Offset(center.x - (singularityRadius + 6f), center.y - (singularityRadius + 6f)),
                            size = Size((singularityRadius + 6f) * 2, (singularityRadius + 6f) * 2),
                            style = Stroke(width = 10f, cap = StrokeCap.Round)
                        )
                    }

                    drawCircle(color = VoidBlack, radius = singularityRadius, center = center)
                }
            }
        }

        // Inner Content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (uiState) {
                is DownloadUiState.Idle -> {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Absorb",
                        tint = appTheme.primaryColor.copy(alpha = 0.9f),
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ABSORB",
                        color = appTheme.primaryColor.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }

                is DownloadUiState.Extracting -> {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = "Extracting",
                        tint = appTheme.accentColor,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "RESOLVING",
                        color = appTheme.accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                is DownloadUiState.Downloading -> {
                    val percentInt = (downloadProgress * 100).toInt()
                    Text(
                        text = "$percentInt%",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (downloadSpeed.isNotBlank()) {
                        Text(
                            text = downloadSpeed,
                            color = appTheme.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                is DownloadUiState.Success -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = SuccessGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SAVED",
                        color = SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp
                    )
                }

                is DownloadUiState.Error -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = ErrorRed,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "RETRY",
                        color = ErrorRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
