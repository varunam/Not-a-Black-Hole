package com.example

import com.example.data.extractor.VideoExtractor
import com.example.data.model.SocialPlatform
import org.junit.Assert.assertEquals
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
  }
}
