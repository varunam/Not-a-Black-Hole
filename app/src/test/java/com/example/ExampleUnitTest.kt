package com.example

import com.example.data.extractor.VideoExtractor
import com.example.data.model.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPlatformDetection_Instagram() {
    val extractor = VideoExtractor()
    assertEquals(
      SocialPlatform.INSTAGRAM,
      extractor.detectPlatform("https://www.instagram.com/reel/C8xyz123/?igsh=abc")
    )
    assertEquals(
      SocialPlatform.INSTAGRAM,
      extractor.detectPlatform("https://instagram.com/p/C9abc456/")
    )
    assertEquals(
      SocialPlatform.INSTAGRAM,
      extractor.detectPlatform("https://www.instagram.com/share/reel/DCq3v18Mxyz/?igsh=MW55")
    )
    assertEquals(
      SocialPlatform.INSTAGRAM,
      extractor.detectPlatform("https://www.instagram.com/share/p/DCq3v18Mxyz/")
    )
  }

  @Test
  fun testPlatformDetection_Twitter() {
    val extractor = VideoExtractor()
    assertEquals(
      SocialPlatform.TWITTER,
      extractor.detectPlatform("https://x.com/AndroidDev/status/1789012345678901234")
    )
    assertEquals(
      SocialPlatform.TWITTER,
      extractor.detectPlatform("https://twitter.com/elonmusk/status/1789012345678901234")
    )
    assertEquals(
      SocialPlatform.TWITTER,
      extractor.detectPlatform("https://x.com/i/status/1789012345678901234")
    )
  }

  @Test
  fun testShortcodeAndMediaIdConversion() {
    val extractor = VideoExtractor()
    assertEquals("C8P1r0YvYg8", extractor.extractShortcode("https://www.instagram.com/reel/C8P1r0YvYg8/?igsh=xyz"))
    assertEquals("DCq3v18Mxyz", extractor.extractShortcode("https://www.instagram.com/share/reel/DCq3v18Mxyz/"))
    assertEquals("3391165152085510204", extractor.shortcodeToMediaId("C8P1r0YvYg8"))
  }

  @Test
  fun testExtractSnapSaveEscapedHtmlLink() {
    val extractor = VideoExtractor()
    val jsSnippet = """document.getElementById("download-section").innerHTML = "<div class=\"download-items\"><div class=\"download-items__thumb\"><img src=\"https:\/\/scontent.cdninstagram.com\/thumb.jpg\"></div><div class=\"download-items__btn\"><a href=\"https:\/\/d.rapidcdn.app\/d?token=eyJ1cmwiOiJ0ZXN0In0\" class=\"abutton\">Download Video</a></div></div>";"""
    val link = extractor.extractSnapSaveLink(jsSnippet)
    assertEquals("https://d.rapidcdn.app/d?token=eyJ1cmwiOiJ0ZXN0In0", link)
  }

  @Test
  fun testExtractVideoUrlFromEscapedInstagramHtml() {
    val extractor = VideoExtractor()
    val htmlSnippet = """<script>{"media":{"video_url":"https:\/\/scontent.cdninstagram.com\/v\/t50.2886-16\/sample.mp4?_nc_ht=scontent\u0026oe=12345"}}</script>"""
    val result = extractor.extractVideoUrlFromHtml(htmlSnippet, "C8P1r0YvYg8")
    assertNotNull(result)
    assertEquals(
      "https://scontent.cdninstagram.com/v/t50.2886-16/sample.mp4?_nc_ht=scontent&oe=12345",
      result?.directVideoUrl
    )
  }
}
