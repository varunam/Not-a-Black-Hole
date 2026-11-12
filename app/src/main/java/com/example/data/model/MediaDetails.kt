package com.example.data.model

enum class SocialPlatform(val displayName: String) {
    INSTAGRAM("Instagram"),
    TWITTER("Twitter/X"),
    UNKNOWN("Unknown")
}

data class ExtractedVideo(
    val directVideoUrl: String,
    val title: String,
    val platform: SocialPlatform,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0,
    val authorName: String? = null
)

sealed class ExtractionResult {
    data class Success(val video: ExtractedVideo) : ExtractionResult()
    data class Failure(val error: String) : ExtractionResult()
}
