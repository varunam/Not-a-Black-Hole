package com.example.data.extractor

import android.util.Log
import com.example.data.model.ExtractedVideo
import com.example.data.model.ExtractionResult
import com.example.data.model.SocialPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class VideoExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "VideoExtractor"

        private val TWITTER_PATTERN = Pattern.compile(
            "https?://(?:www\\.|mobile\\.)?(?:twitter\\.com|x\\.com)/(?:#!/)?(\\w+)/status/(\\d+)",
            Pattern.CASE_INSENSITIVE
        )

        private val INSTAGRAM_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?(?:instagram\\.com|instagr\\.am)/(?:p|reel|tv|reels)/([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
        )

        private val DIRECT_VIDEO_PATTERN = Pattern.compile(
            "https?://.*\\.(?:mp4|webm|mov|m4v)(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE
        )

        private val URL_FINDER_PATTERN = Pattern.compile(
            "https?://[^\\s<>\"]+",
            Pattern.CASE_INSENSITIVE
        )

        fun extractUrlFromText(text: String): String {
            val matcher = URL_FINDER_PATTERN.matcher(text.trim())
            return if (matcher.find()) {
                matcher.group(0).trim()
            } else {
                text.trim()
            }
        }
    }

    fun detectPlatform(url: String): SocialPlatform {
        val trimmed = url.trim()
        return when {
            TWITTER_PATTERN.matcher(trimmed).find() -> SocialPlatform.TWITTER
            INSTAGRAM_PATTERN.matcher(trimmed).find() -> SocialPlatform.INSTAGRAM
            trimmed.contains("twitter.com", ignoreCase = true) || trimmed.contains("x.com", ignoreCase = true) -> SocialPlatform.TWITTER
            trimmed.contains("instagram.com", ignoreCase = true) || trimmed.contains("instagr.am", ignoreCase = true) -> SocialPlatform.INSTAGRAM
            else -> SocialPlatform.UNKNOWN
        }
    }

    suspend fun extract(rawUrl: String): ExtractionResult = withContext(Dispatchers.IO) {
        val cleanUrl = cleanUrl(rawUrl)
        Log.d(TAG, "Starting extraction for: $cleanUrl")

        if (cleanUrl.isBlank()) {
            return@withContext ExtractionResult.Failure("Clipboard is empty or contains no valid URL")
        }

        // Direct video check
        if (DIRECT_VIDEO_PATTERN.matcher(cleanUrl).find()) {
            return@withContext ExtractionResult.Success(
                ExtractedVideo(
                    directVideoUrl = cleanUrl,
                    title = "Direct Video",
                    platform = SocialPlatform.UNKNOWN
                )
            )
        }

        val platform = detectPlatform(cleanUrl)
        when (platform) {
            SocialPlatform.TWITTER -> extractTwitterVideo(cleanUrl)
            SocialPlatform.INSTAGRAM -> extractInstagramVideo(cleanUrl)
            SocialPlatform.UNKNOWN -> {
                // If it doesn't match standard Instagram or Twitter pattern directly, check if it's a redirect or shortened link
                val resolvedUrl = followRedirects(cleanUrl)
                val resolvedPlatform = detectPlatform(resolvedUrl)
                when (resolvedPlatform) {
                    SocialPlatform.TWITTER -> extractTwitterVideo(resolvedUrl)
                    SocialPlatform.INSTAGRAM -> extractInstagramVideo(resolvedUrl)
                    else -> ExtractionResult.Failure("Only Instagram and Twitter/X links are supported")
                }
            }
        }
    }

    private fun cleanUrl(url: String): String {
        return extractUrlFromText(url)
    }

    private fun followRedirects(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            url
        }
    }

    private fun extractTwitterVideo(url: String): ExtractionResult {
        val matcher = TWITTER_PATTERN.matcher(url)
        val tweetId = if (matcher.find()) matcher.group(2) else {
            // Extract trailing digits
            val digits = url.split("?").firstOrNull()?.split("/")?.lastOrNull { it.all { c -> c.isDigit() } }
            digits
        }

        if (tweetId == null) {
            return ExtractionResult.Failure("Could not identify Twitter/X Post ID")
        }

        // Strategy 1: FxTwitter API (Super fast, official public tweet media parser)
        try {
            val fxUrl = "https://api.fxtwitter.com/status/$tweetId"
            val request = Request.Builder()
                .url(fxUrl)
                .header("User-Agent", "BlackHoleAndroid/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val tweet = json.optJSONObject("tweet")
                    if (tweet != null) {
                        val media = tweet.optJSONObject("media")
                        val videos = media?.optJSONArray("videos")
                        if (videos != null && videos.length() > 0) {
                            var bestUrl: String? = null
                            var bestBitrate = -1
                            var thumbUrl: String? = null
                            var duration = 0L

                            for (i in 0 until videos.length()) {
                                val v = videos.getJSONObject(i)
                                val vUrl = v.optString("url")
                                val bitrate = v.optInt("bitrate", 0)
                                if (vUrl.isNotBlank() && (bestUrl == null || bitrate > bestBitrate)) {
                                    bestUrl = vUrl
                                    bestBitrate = bitrate
                                    thumbUrl = v.optString("thumbnail_url")
                                    duration = v.optLong("duration", 0L)
                                }
                            }

                            if (bestUrl != null) {
                                val text = tweet.optString("text", "Twitter Video")
                                val author = tweet.optJSONObject("author")?.optString("name")
                                return ExtractionResult.Success(
                                    ExtractedVideo(
                                        directVideoUrl = bestUrl,
                                        title = if (text.isNotBlank()) text.take(60) else "X Post by @$author",
                                        platform = SocialPlatform.TWITTER,
                                        thumbnailUrl = thumbUrl,
                                        durationSeconds = duration / 1000,
                                        authorName = author
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FxTwitter resolver failed: ${e.message}")
        }

        // Strategy 2: VxTwitter API fallback
        try {
            val vxUrl = "https://api.vxtwitter.com/Twitter/status/$tweetId"
            val request = Request.Builder()
                .url(vxUrl)
                .header("User-Agent", "BlackHoleAndroid/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val videoUrl = json.optString("video_url")
                    if (videoUrl.isNotBlank()) {
                        val text = json.optString("text", "Twitter Video")
                        val author = json.optString("user_name")
                        return ExtractionResult.Success(
                            ExtractedVideo(
                                directVideoUrl = videoUrl,
                                title = if (text.isNotBlank()) text.take(60) else "X Post $tweetId",
                                platform = SocialPlatform.TWITTER,
                                authorName = author
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VxTwitter resolver failed: ${e.message}")
        }

        return ExtractionResult.Failure("No streamable video found in this Twitter/X post. Make sure it contains an MP4 video clip.")
    }

    private fun extractInstagramVideo(url: String): ExtractionResult {
        val matcher = INSTAGRAM_PATTERN.matcher(url)
        val shortcode = if (matcher.find()) matcher.group(1) else null

        if (shortcode == null) {
            return ExtractionResult.Failure("Could not identify Instagram Reel / Post code")
        }

        // Strategy 1: Instagram Embed Page Scraping
        // Instagram embed pages contain the public CDN mp4 stream without authentication
        try {
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""

                    // Regex for video src in embed page
                    val videoRegex = Pattern.compile(
                        """video_url["']:\s*["']([^"']+)["']|<video[^>]+src=["']([^"']+)["']|class=["']EmbeddedMediaImage["'][^>]*src=["']([^"']+)["']""",
                        Pattern.CASE_INSENSITIVE
                    )
                    val m = videoRegex.matcher(html)
                    while (m.find()) {
                        var videoUrl = m.group(1) ?: m.group(2)
                        if (videoUrl != null) {
                            videoUrl = unescapeJson(videoUrl)
                            if (videoUrl.contains(".mp4") || videoUrl.contains("cdninstagram.com")) {
                                return ExtractionResult.Success(
                                    ExtractedVideo(
                                        directVideoUrl = videoUrl,
                                        title = "Instagram Reel ($shortcode)",
                                        platform = SocialPlatform.INSTAGRAM
                                    )
                                )
                            }
                        }
                    }

                    // Secondary match for any cdninstagram mp4 in json
                    val cdnRegex = Pattern.compile(
                        """https:\\/\\/[^"'\\]+cdninstagram\.com[^"'\\]+\.mp4[^"'\\]*""",
                        Pattern.CASE_INSENSITIVE
                    )
                    val cdnMatcher = cdnRegex.matcher(html)
                    if (cdnMatcher.find()) {
                        val videoUrl = unescapeJson(cdnMatcher.group(0) ?: "")
                        if (videoUrl.isNotBlank()) {
                            return ExtractionResult.Success(
                                ExtractedVideo(
                                    directVideoUrl = videoUrl,
                                    title = "Instagram Reel ($shortcode)",
                                    platform = SocialPlatform.INSTAGRAM
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Instagram embed scrape error: ${e.message}")
        }

        // Strategy 2: Fast public Instagram DDInstagram resolver fallback
        try {
            val ddUrl = "https://ddinstagram.com/p/$shortcode/"
            val request = Request.Builder()
                .url(ddUrl)
                .header("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val ogVideoPattern = Pattern.compile(
                        """<meta\s+property=["']og:video["']\s+content=["']([^"']+)["']|<meta\s+property=["']og:video:secure_url["']\s+content=["']([^"']+)["']""",
                        Pattern.CASE_INSENSITIVE
                    )
                    val ogMatcher = ogVideoPattern.matcher(html)
                    if (ogMatcher.find()) {
                        val videoUrl = ogMatcher.group(1) ?: ogMatcher.group(2)
                        if (!videoUrl.isNullOrBlank()) {
                            return ExtractionResult.Success(
                                ExtractedVideo(
                                    directVideoUrl = unescapeJson(videoUrl),
                                    title = "Instagram Reel ($shortcode)",
                                    platform = SocialPlatform.INSTAGRAM
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DDInstagram resolver failed: ${e.message}")
        }

        // Strategy 3: Fast oEmbed fallback for details
        return ExtractionResult.Failure("Unable to extract video from Instagram. Ensure the Reel or Post is from a public account.")
    }

    private fun unescapeJson(raw: String): String {
        return raw.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("\\\"", "\"")
    }
}
