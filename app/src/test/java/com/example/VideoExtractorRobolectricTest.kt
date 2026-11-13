package com.example

import com.example.data.extractor.VideoExtractor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoExtractorRobolectricTest {

  @Test
  fun testParseGraphQlJsonAndMediaApi() {
    val extractor = VideoExtractor()
    val gqlJson = """{"data":{"xdt_shortcode_media":{"video_url":"https:\/\/cdninstagram.com\/v\/reel.mp4?a=1\u0026b=2","display_url":"https:\/\/cdninstagram.com\/thumb.jpg","owner":{"username":"nasa"}}}}"""
    val gqlResult = extractor.parseGraphQlJson(gqlJson, "ABC123")
    assertNotNull(gqlResult)
    assertEquals("https://cdninstagram.com/v/reel.mp4?a=1&b=2", gqlResult?.directVideoUrl)
    assertEquals("nasa", gqlResult?.authorName)

    val apiJson = JSONObject("""{"video_versions":[{"url":"https://cdninstagram.com/lo.mp4","width":480,"height":854},{"url":"https://cdninstagram.com/hi.mp4","width":720,"height":1280}],"caption":{"text":"Space Launch"},"user":{"username":"spacex"}}""")
    val apiResult = extractor.parseMediaApiItem(apiJson, "XYZ999")
    assertNotNull(apiResult)
    assertEquals("https://cdninstagram.com/hi.mp4", apiResult?.directVideoUrl)
    assertEquals("Space Launch", apiResult?.title)
    assertEquals("spacex", apiResult?.authorName)
  }
}
