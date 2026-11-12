package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppTheme(
    val title: String,
    val subtitle: String,
    val primaryColor: Color,
    val accentColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val surfaceBorder: Color
) {
    ELEGANT_OBSIDIAN(
        title = "CRED Obsidian (Default)",
        subtitle = "Ultra-matte luxury dark with titanium & subtle champagne gold",
        primaryColor = Color(0xFFE2E8F0),   // Platinum Silver
        accentColor = Color(0xFFF59E0B),    // Warm Champagne Gold
        backgroundColor = Color(0xFF09090D), // Matte Obsidian
        surfaceColor = Color(0xFF13131A),
        surfaceBorder = Color(0xFF242432)
    ),
    CYBER_NEON(
        title = "Cyber Neon",
        subtitle = "Vibrant cosmic violet, cyan & electric event horizon",
        primaryColor = Color(0xFFA855F7),   // Electric Violet
        accentColor = Color(0xFF06B6D4),    // Neon Cyan
        backgroundColor = Color(0xFF050508),
        surfaceColor = Color(0xFF0F0F16),
        surfaceBorder = Color(0xFF26263A)
    ),
    ROYAL_GOLD(
        title = "Midnight Gold",
        subtitle = "Deep velvet black with rich 24K gold & amber luster",
        primaryColor = Color(0xFFFBBF24),   // 24K Gold
        accentColor = Color(0xFFF59E0B),    // Warm Amber
        backgroundColor = Color(0xFF0A0906),
        surfaceColor = Color(0xFF16140D),
        surfaceBorder = Color(0xFF2E2716)
    ),
    TITANIUM_MONO(
        title = "Titanium Minimal",
        subtitle = "Pure stark brutalist monochrome with brushed steel",
        primaryColor = Color(0xFFFFFFFF),   // Crisp White
        accentColor = Color(0xFF94A3B8),    // Steel Gray
        backgroundColor = Color(0xFF060607),
        surfaceColor = Color(0xFF121214),
        surfaceBorder = Color(0xFF222226)
    )
}
