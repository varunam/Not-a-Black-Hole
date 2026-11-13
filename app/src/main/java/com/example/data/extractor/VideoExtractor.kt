package com.example.data.extractor

import android.util.Log
import com.example.data.model.ExtractedVideo
import com.example.data.model.ExtractionResult
import com.example.data.model.SocialPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class VideoExtractor(
    private val client: OkHttpClient = createDefaultClient()
) {
    companion object {
        private const val TAG = "VideoExtractor"
        private const val IG_APP_ID = "936619743392459"
        private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        private val TWITTER_PATTERN = Pattern.compile(
            "https?://(?:www\\.|mobile\\.)?(?:twitter\\.com|x\\.com)/(?:#!/)?(?:[a-zA-Z0-9_]+/status/|i/status/)(\\d+)",
            Pattern.CASE_INSENSITIVE
        )

        // Supports /reel/, /p/, /tv/, /reels/, /r/, /share/reel/, /share/p/, /share/r/, /share/reels/
        private val INSTAGRAM_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?(?:instagram\\.com|instagr\\.am)/(?:(?:share/(?:reel|reels|p|r)?/)|(?:p|reel|reels|tv|r)/)([A-Za-z0-9_-]+)",
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

        fun createDefaultClient(): OkHttpClient {
            val cookieJar = object : CookieJar {
                private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val hostKey = url.topPrivateDomain() ?: url.host
                    val current = store.getOrPut(hostKey) { mutableListOf() }
                    synchronized(current) {
                        for (cookie in cookies) {
                            current.removeAll { it.name == cookie.name }
                            if (cookie.expiresAt > System.currentTimeMillis()) {
                                current.add(cookie)
                            }
                        }
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val hostKey = url.topPrivateDomain() ?: url.host
                    val current = store[hostKey] ?: return emptyList()
                    val now = System.currentTimeMillis()
                    synchronized(current) {
                        current.removeAll { it.expiresAt <= now }
                        return current.filter { it.matches(url) }
                    }
                }
            }

            return OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        fun extractUrlFromText(text: String): String {
            val matcher = URL_FINDER_PATTERN.matcher(text.trim())
            return if (matcher.find()) {
                matcher.group(0)?.trim() ?: text.trim()
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
                    title = "Direct Video Clip",
                    platform = SocialPlatform.UNKNOWN
                )
            )
        }

        val platform = detectPlatform(cleanUrl)
        when (platform) {
            SocialPlatform.TWITTER -> extractTwitterVideo(cleanUrl)
            SocialPlatform.INSTAGRAM -> extractInstagramVideo(cleanUrl)
            SocialPlatform.UNKNOWN -> {
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
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    // ==========================================
    // TWITTER / X VIDEO EXTRACTION
    // ==========================================
    private fun extractTwitterVideo(url: String): ExtractionResult {
        val matcher = TWITTER_PATTERN.matcher(url)
        val tweetId = if (matcher.find()) matcher.group(1) else {
            url.split("?").firstOrNull()?.split("/")?.lastOrNull { it.isNotEmpty() && it.all { c -> c.isDigit() } }
        }

        if (tweetId == null) {
            return ExtractionResult.Failure("Could not identify Twitter/X Post ID")
        }

        // Strategy 1: FxTwitter API
        val fxEndpoints = listOf(
            "https://api.fxtwitter.com/i/status/$tweetId",
            "https://api.fxtwitter.com/status/$tweetId"
        )
        for (fxUrl in fxEndpoints) {
            try {
                val request = Request.Builder()
                    .url(fxUrl)
                    .header("User-Agent", "NotABlackHole/1.0")
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
                                    val text = tweet.optString("text", "X Post")
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
                Log.w(TAG, "FxTwitter resolver failed for $fxUrl: ${e.message}")
            }
        }

        // Strategy 2: VxTwitter API fallback
        try {
            val vxUrl = "https://api.vxtwitter.com/Twitter/status/$tweetId"
            val request = Request.Builder()
                .url(vxUrl)
                .header("User-Agent", "NotABlackHole/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val videoUrl = json.optString("video_url")
                    if (videoUrl.isNotBlank()) {
                        val text = json.optString("text", "X Video")
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

        return ExtractionResult.Failure("No streamable video found in this Twitter/X post. Make sure the post contains an MP4 video clip.")
    }

    // ==========================================
    // INSTAGRAM REEL / POST EXTRACTION
    // ==========================================
    private fun extractInstagramVideo(initialUrl: String): ExtractionResult {
        // Instagram /share/... links can redirect (302) to the canonical /reel/<shortcode>/ URL
        val targetUrl = if (initialUrl.contains("/share/", ignoreCase = true)) {
            followRedirects(initialUrl)
        } else {
            initialUrl
        }

        val shortcode = extractShortcode(targetUrl) ?: extractShortcode(initialUrl)
        if (shortcode == null) {
            return ExtractionResult.Failure("Could not identify Instagram Reel / Post code from link.")
        }

        Log.d(TAG, "Extracted Instagram shortcode: $shortcode")

        // Strategy 1: Embed page scraping (fast, often exposes direct video_url or json payloads)
        tryInstagramEmbedScrape(shortcode)?.let { return ExtractionResult.Success(it) }

        // Strategy 2: Polaris Logged-Out GraphQL & Legacy GraphQL with CookieJar + CSRF + LSD
        tryInstagramPolarisAndGraphQl(shortcode)?.let { return ExtractionResult.Success(it) }

        // Strategy 3: Instagram Mobile Web API (/api/v1/media/{id}/info/)
        tryInstagramMediaInfoApi(shortcode)?.let { return ExtractionResult.Success(it) }

        // Strategy 4: Direct Mobile & Desktop Web Page Scrape
        tryInstagramWebScrape(shortcode)?.let { return ExtractionResult.Success(it) }

        // Strategy 5: SnapSave Resolver + JS Deobfuscator
        trySnapSaveApi(shortcode, targetUrl)?.let { return ExtractionResult.Success(it) }

        // Strategy 6: SaveIG / SaveClip Resolver Fallback
        trySaveIgApi(shortcode)?.let { return ExtractionResult.Success(it) }

        return ExtractionResult.Failure(
            "Could not download this Instagram Reel. Please check that the Reel is from a public account and try sharing the link directly."
        )
    }

    internal fun extractShortcode(url: String): String? {
        val matcher = INSTAGRAM_PATTERN.matcher(url)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val clean = url.split("?").firstOrNull() ?: url
        val parts = clean.trimEnd('/').split("/")
        for (i in 0 until parts.size - 1) {
            val part = parts[i].lowercase()
            if (part in listOf("reel", "reels", "p", "tv", "r")) {
                val candidate = parts[i + 1]
                if (candidate.isNotBlank() && candidate.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                    return candidate
                }
            }
        }
        return null
    }

    internal fun shortcodeToMediaId(shortcode: String): String? {
        var mediaId = 0L
        for (char in shortcode) {
            val idx = BASE64_ALPHABET.indexOf(char)
            if (idx < 0) return null
            mediaId = mediaId * 64L + idx
        }
        return mediaId.toString()
    }

    private fun getInstagramCookies(urlStr: String = "https://www.instagram.com/"): Map<String, String> {
        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host("www.instagram.com")
            .build()
        return client.cookieJar.loadForRequest(httpUrl).associate { it.name to it.value }
    }

    private fun bootstrapInstagramSession(): Pair<String, String> {
        var lsdToken = ""
        try {
            val req = Request.Builder()
                .url("https://www.instagram.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val lsdPatterns = listOf(
                        """\["LSD",\[\],\{"token":"([^"]+)"\}""",
                        """"LSD",\[\],\{"token":"([^"]+)"\}"""
                    )
                    for (p in lsdPatterns) {
                        val m = Pattern.compile(p).matcher(html)
                        if (m.find()) {
                            lsdToken = m.group(1) ?: ""
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Instagram session bootstrap failed: ${e.message}")
        }
        val csrf = getInstagramCookies()["csrftoken"] ?: ""
        return csrf to lsdToken
    }

    /**
     * Strategy 1: Embed captioned & standard embed pages (with escaped JSON support)
     */
    private fun tryInstagramEmbedScrape(shortcode: String): ExtractedVideo? {
        val embedUrls = listOf(
            "https://www.instagram.com/p/$shortcode/embed/captioned/",
            "https://www.instagram.com/reel/$shortcode/embed/"
        )
        for (embedUrl in embedUrls) {
            try {
                val request = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Referer", "https://www.google.com/")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        extractVideoUrlFromHtml(html, shortcode)?.let { return it }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Embed scrape failed for $embedUrl: ${e.message}")
            }
        }
        return null
    }

    /**
     * Strategy 2: Polaris Logged-Out GraphQL & Legacy Shortcode GraphQL
     */
    private fun tryInstagramPolarisAndGraphQl(shortcode: String): ExtractedVideo? {
        val (csrfToken, lsdToken) = bootstrapInstagramSession()
        val mediaId = shortcodeToMediaId(shortcode)

        // 2A: Polaris Logged-Out POST GraphQL
        if (mediaId != null && lsdToken.isNotBlank() && csrfToken.isNotBlank()) {
            val polarisDocIds = listOf("27130156389949648", "27128499623469141")
            for (docId in polarisDocIds) {
                try {
                    val formBody = FormBody.Builder()
                        .add("lsd", lsdToken)
                        .add("fb_api_caller_class", "RelayModern")
                        .add("fb_api_req_friendly_name", "PolarisLoggedOutDesktopWWWPostRootContentQuery")
                        .add("server_timestamps", "true")
                        .add("variables", """{"media_id":"$mediaId"}""")
                        .add("doc_id", docId)
                        .build()

                    val request = Request.Builder()
                        .url("https://www.instagram.com/api/graphql")
                        .post(formBody)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("X-IG-App-ID", IG_APP_ID)
                        .header("X-ASBD-ID", "198387")
                        .header("X-IG-WWW-Claim", "0")
                        .header("X-FB-Friendly-Name", "PolarisLoggedOutDesktopWWWPostRootContentQuery")
                        .header("X-CSRFToken", csrfToken)
                        .header("X-FB-LSD", lsdToken)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://www.instagram.com")
                        .header("Referer", "https://www.instagram.com/reel/$shortcode/")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            if (body.trimStart().startsWith("{")) {
                                val json = JSONObject(body)
                                val product = json.optJSONObject("data")
                                    ?.optJSONObject("xig_polaris_media")
                                    ?.optJSONObject("if_not_gated_logged_out")
                                if (product != null) {
                                    parseMediaApiItem(product, shortcode)?.let { return it }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Polaris GraphQL ($docId) failed: ${e.message}")
                }
            }
        }

        // 2B: Legacy / Shortcode GraphQL POST & GET
        val legacyDocIds = listOf("8845758582119845", "10015901848480474", "9510064595728286")
        val variablesJson = """{"shortcode":"$shortcode","fetch_tagged_user_count":null,"hoisted_comment_id":null,"hoisted_reply_id":null}"""

        for (docId in legacyDocIds) {
            try {
                val formBody = FormBody.Builder()
                    .add("variables", variablesJson)
                    .add("doc_id", docId)
                    .build()

                val postReq = Request.Builder()
                    .url("https://www.instagram.com/graphql/query")
                    .post(formBody)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("X-IG-App-ID", IG_APP_ID)
                    .header("X-CSRFToken", csrfToken)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", "https://www.instagram.com")
                    .header("Referer", "https://www.instagram.com/reel/$shortcode/")
                    .build()

                client.newCall(postReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.trimStart().startsWith("{")) {
                            parseGraphQlJson(body, shortcode)?.let { return it }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "GraphQL POST doc_id $docId failed: ${e.message}")
            }

            try {
                val encodedVars = URLEncoder.encode(variablesJson, StandardCharsets.UTF_8.name())
                val queryUrl = "https://www.instagram.com/graphql/query/?doc_id=$docId&variables=$encodedVars"
                val getReq = Request.Builder()
                    .url(queryUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("X-IG-App-ID", IG_APP_ID)
                    .header("X-CSRFToken", csrfToken)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "https://www.instagram.com/reel/$shortcode/")
                    .build()

                client.newCall(getReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.trimStart().startsWith("{")) {
                            parseGraphQlJson(body, shortcode)?.let { return it }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "GraphQL GET doc_id $docId failed: ${e.message}")
            }
        }

        return null
    }

    /**
     * Strategy 3: Instagram Mobile Web Info API (/api/v1/media/{mediaId}/info/)
     */
    private fun tryInstagramMediaInfoApi(shortcode: String): ExtractedVideo? {
        val mediaId = shortcodeToMediaId(shortcode) ?: return null
        val csrf = getInstagramCookies()["csrftoken"] ?: ""
        val endpoints = listOf(
            "https://i.instagram.com/api/v1/media/$mediaId/info/",
            "https://www.instagram.com/api/v1/media/$mediaId/info/"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("X-IG-App-ID", IG_APP_ID)
                    .header("X-ASBD-ID", "198387")
                    .header("X-IG-WWW-Claim", "0")
                    .header("X-CSRFToken", csrf)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "https://www.instagram.com/reel/$shortcode/")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.trimStart().startsWith("{")) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items")
                            if (items != null && items.length() > 0) {
                                parseMediaApiItem(items.getJSONObject(0), shortcode)?.let { return it }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Media Info API failed for $endpoint: ${e.message}")
            }
        }
        return null
    }

    /**
     * Strategy 4: Mobile & Desktop web scrape
     */
    private fun tryInstagramWebScrape(shortcode: String): ExtractedVideo? {
        val urlsAndAgents = listOf(
            "https://www.instagram.com/reel/$shortcode/" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
            "https://www.instagram.com/p/$shortcode/" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )
        for ((url, ua) in urlsAndAgents) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        extractVideoUrlFromHtml(html, shortcode)?.let { return it }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Web scrape failed for $url: ${e.message}")
            }
        }
        return null
    }

    /**
     * Strategy 5: SnapSave API with JavaScript deobfuscation
     */
    private fun trySnapSaveApi(shortcode: String, canonicalUrl: String? = null): ExtractedVideo? {
        val candidateUrls = listOfNotNull(
            "https://www.instagram.com/reel/$shortcode/",
            canonicalUrl?.takeIf { it.contains("instagram.com") && !it.contains("/reel/$shortcode") }
        ).distinct()

        for (targetUrl in candidateUrls) {
            try {
                val formBody = FormBody.Builder()
                    .add("url", targetUrl)
                    .build()

                val request = Request.Builder()
                    .url("https://snapsave.app/action.php?lang=en")
                    .post(formBody)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Origin", "https://snapsave.app")
                    .header("Referer", "https://snapsave.app/")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val rawJs = response.body?.string() ?: ""
                        if (rawJs.contains("decodeURIComponent(escape(r))}(")) {
                            val encoded = rawJs.substringAfter("decodeURIComponent(escape(r))}(")
                                .substringBefore("))")
                            val args = encoded.split(",").map { it.replace("\"", "").trim() }
                            if (args.size >= 5) {
                                val decodedHtml = decodeSnapApp(
                                    h = args[0],
                                    u = args[1],
                                    n = args[2],
                                    t = args[3].toIntOrNull() ?: 0,
                                    e = args[4].toIntOrNull() ?: 0
                                )
                                extractSnapSaveLink(decodedHtml)?.let { link ->
                                    val resolved = if (link.contains("get_progressApi")) {
                                        resolveSnapSaveRenderUrl(link)
                                    } else {
                                        link
                                    }
                                    if (!resolved.isNullOrBlank()) {
                                        return ExtractedVideo(
                                            directVideoUrl = resolved,
                                            title = "Instagram Reel ($shortcode)",
                                            platform = SocialPlatform.INSTAGRAM
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "SnapSave resolver failed for $targetUrl: ${e.message}")
            }
        }
        return null
    }

    private fun resolveSnapSaveRenderUrl(renderUrl: String): String? {
        return try {
            val req = Request.Builder()
                .url(renderUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://snapsave.app/")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    json.optString("file").takeIf { it.startsWith("http") }
                        ?: json.optString("url").takeIf { it.startsWith("http") }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Strategy 6: SaveIG / SaveClip AJAX Search fallback
     */
    private fun trySaveIgApi(shortcode: String): ExtractedVideo? {
        val endpoints = listOf(
            "https://v3.saveig.app/api/ajaxSearch" to "https://saveig.app/en",
            "https://saveclip.app/api/ajaxSearch" to "https://saveclip.app/en"
        )
        val targetUrl = "https://www.instagram.com/reel/$shortcode/"

        for ((endpoint, referer) in endpoints) {
            try {
                val formBody = FormBody.Builder()
                    .add("q", targetUrl)
                    .add("t", "media")
                    .add("lang", "en")
                    .build()

                val request = Request.Builder()
                    .url(endpoint)
                    .post(formBody)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", referer)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.trimStart().startsWith("{")) {
                            val json = JSONObject(body)
                            val html = json.optString("data")
                            if (html.isNotBlank()) {
                                extractSnapSaveLink(html)?.let { link ->
                                    return ExtractedVideo(
                                        directVideoUrl = link,
                                        title = "Instagram Reel ($shortcode)",
                                        platform = SocialPlatform.INSTAGRAM
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "SaveIG fallback failed for $endpoint: ${e.message}")
            }
        }
        return null
    }

    // ==========================================
    // PARSING & DEOBFUSCATION HELPERS
    // ==========================================

    internal fun parseGraphQlJson(body: String, shortcode: String): ExtractedVideo? {
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: return null
        val media = data.optJSONObject("xdt_shortcode_media")
            ?: data.optJSONObject("shortcode_media")
            ?: return null

        var videoUrl = media.optString("video_url")
        if (videoUrl.isBlank()) {
            videoUrl = extractBestVideoVersionUrl(media.optJSONArray("video_versions")) ?: ""
        }
        if (videoUrl.isBlank()) {
            val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
            if (edges != null) {
                for (i in 0 until edges.length()) {
                    val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val candidate = node.optString("video_url")
                    if (candidate.isNotBlank()) {
                        videoUrl = candidate
                        break
                    }
                }
            }
        }

        if (videoUrl.isBlank()) return null

        val title = media.optJSONObject("edge_media_to_caption")
            ?.optJSONArray("edges")
            ?.optJSONObject(0)
            ?.optJSONObject("node")
            ?.optString("text")
            ?.take(60)
            ?.ifBlank { null }
            ?: "Instagram Reel ($shortcode)"

        val thumb = media.optString("display_url").takeIf { it.isNotBlank() }
        val owner = media.optJSONObject("owner")?.optString("username")?.takeIf { it.isNotBlank() }

        return ExtractedVideo(
            directVideoUrl = unescapeJson(videoUrl),
            title = title,
            platform = SocialPlatform.INSTAGRAM,
            thumbnailUrl = thumb,
            authorName = owner
        )
    }

    internal fun parseMediaApiItem(item: JSONObject, shortcode: String): ExtractedVideo? {
        var videoUrl = extractBestVideoVersionUrl(item.optJSONArray("video_versions"))
            ?: item.optString("video_url").takeIf { it.isNotBlank() }

        if (videoUrl == null) {
            val carousel = item.optJSONArray("carousel_media")
            if (carousel != null) {
                for (i in 0 until carousel.length()) {
                    val child = carousel.optJSONObject(i) ?: continue
                    videoUrl = extractBestVideoVersionUrl(child.optJSONArray("video_versions"))
                    if (videoUrl != null) break
                }
            }
        }

        if (videoUrl.isNullOrBlank()) return null

        val caption = item.optJSONObject("caption")?.optString("text")?.take(60)?.ifBlank { null }
            ?: "Instagram Reel ($shortcode)"
        val owner = item.optJSONObject("user")?.optString("username")?.takeIf { it.isNotBlank() }
        val thumb = item.optJSONObject("image_versions2")
            ?.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }

        return ExtractedVideo(
            directVideoUrl = unescapeJson(videoUrl),
            title = caption,
            platform = SocialPlatform.INSTAGRAM,
            thumbnailUrl = thumb,
            authorName = owner
        )
    }

    private fun extractBestVideoVersionUrl(versions: JSONArray?): String? {
        if (versions == null || versions.length() == 0) return null
        var bestUrl: String? = null
        var bestArea = -1
        for (i in 0 until versions.length()) {
            val v = versions.optJSONObject(i) ?: continue
            val url = v.optString("url")
            val area = v.optInt("width", 0) * v.optInt("height", 0)
            if (url.isNotBlank() && (bestUrl == null || area > bestArea)) {
                bestUrl = url
                bestArea = area
            }
        }
        return bestUrl
    }

    internal fun extractVideoUrlFromHtml(html: String, shortcode: String): ExtractedVideo? {
        // Normalize doubly-escaped / JSON-escaped quotes and slashes so all patterns match uniformly
        val normalized = unescapeJson(
            html.replace("\\\\\"", "\"")
                .replace("\\\"", "\"")
                .replace("\\\\/", "/")
        )

        val patterns = listOf(
            """video_url"\s*:\s*"(https?://[^"]+)"""",
            """browser_native_hd_url"\s*:\s*"(https?://[^"]+)"""",
            """browser_native_sd_url"\s*:\s*"(https?://[^"]+)"""",
            """playback_url"\s*:\s*"(https?://[^"]+)"""",
            """<video[^>]+src=["'](https?://[^"']+)["']""",
            """<meta[^>]+property=["']og:video(?::secure_url)?["'][^>]+content=["'](https?://[^"']+)["']""",
            """(https?://[^\s"'<>\\]+(?:cdninstagram\.com|fbcdn\.net)[^\s"'<>\\]+\.mp4[^\s"'<>\\]*)"""
        )

        for (patternStr in patterns) {
            val p = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val m = p.matcher(normalized)
            while (m.find()) {
                val candidate = m.group(1)?.let { unescapeJson(it) } ?: continue
                if (candidate.startsWith("http") && !candidate.contains(".jpg") && !candidate.contains(".webp")) {
                    return ExtractedVideo(
                        directVideoUrl = candidate,
                        title = "Instagram Reel ($shortcode)",
                        platform = SocialPlatform.INSTAGRAM
                    )
                }
            }
        }
        return null
    }

    internal fun decodeSnapApp(h: String, u: String, n: String, t: Int, e: Int): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/"
        if (e <= 1 || e > alphabet.length || e >= n.length) return ""
        val hArr = alphabet.take(e)

        fun decodeBase(d: String): Long {
            var j = 0L
            var mult = 1L
            for (i in d.length - 1 downTo 0) {
                val idx = hArr.indexOf(d[i])
                if (idx != -1) {
                    j += idx * mult
                }
                mult *= e
            }
            return j
        }

        val byteBuffer = ArrayList<Byte>()
        var i = 0
        val len = h.length
        val sep = n[e]

        while (i < len) {
            val s = StringBuilder()
            while (i < len && h[i] != sep) {
                s.append(h[i])
                i++
            }
            i++
            var sStr = s.toString()
            for (j in n.indices) {
                sStr = sStr.replace(n[j].toString(), j.toString())
            }
            if (sStr.isNotEmpty()) {
                val jVal = decodeBase(sStr)
                val code = (jVal - t).toInt()
                if (code in 1..255) {
                    byteBuffer.add(code.toByte())
                }
            }
        }
        return String(byteBuffer.toByteArray(), StandardCharsets.UTF_8)
    }

    internal fun extractSnapSaveLink(decodedJsOrHtml: String): String? {
        // SnapSave wraps HTML inside JS strings with \" and \/ escapes — strip JS escaping first
        val unescaped = unescapeJson(
            decodedJsOrHtml
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
        )

        // Check for get_progressApi render URLs
        val progressMatch = Pattern.compile("""get_progressApi\('([^']+)'\)""").matcher(unescaped)
        if (progressMatch.find()) {
            val path = progressMatch.group(1) ?: ""
            if (path.startsWith("/")) {
                return "https://snapsave.app$path"
            } else if (path.startsWith("http")) {
                return path
            }
        }

        val hrefPattern = Pattern.compile("""href=["'](https?://[^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val matcher = hrefPattern.matcher(unescaped)
        val candidates = mutableListOf<String>()
        while (matcher.find()) {
            val link = matcher.group(1) ?: continue
            // Skip website navigation/ad links and static image thumbnails
            if (link.endsWith(".jpg") || link.endsWith(".jpeg") || link.endsWith(".png") || link.endsWith(".webp")) {
                continue
            }
            if (link.contains("rapidcdn", true) ||
                link.contains("snapcdn", true) ||
                link.contains("fbcdn", true) ||
                link.contains("cdninstagram", true) ||
                link.contains(".mp4", true) ||
                link.contains("?token=", true) ||
                link.contains("/download", true)
            ) {
                candidates.add(link)
            }
        }

        // Prefer explicit video/CDN token links first
        return candidates.firstOrNull {
            it.contains(".mp4", true) || it.contains("rapidcdn", true) || it.contains("snapcdn", true) || it.contains("?token=", true)
        } ?: candidates.firstOrNull()
    }

    private fun unescapeJson(raw: String): String {
        return raw.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("&amp;", "&")
    }
}
