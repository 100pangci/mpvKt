package live.mehiz.mpvkt.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiblingSubtitleIndexTest {
  @Test
  fun `matching preserves the original prefix rule and directory order`() {
    val index = SiblingSubtitleIndex(listOf(
      "EP1.zh.srt" to "chinese", "EP10.ass" to "episode-ten", "EP1.ass" to "default", "EP2.ass" to "other",
    ))
    assertEquals(listOf("chinese", "episode-ten", "default"), index.forVideoName("EP1.mp4"))
  }

  @Test
  fun `subtitle extensions are case insensitive but prefixes are case sensitive`() {
    val index = SiblingSubtitleIndex(listOf("Episode.ASS" to "upper", "episode.srt" to "lower"))
    assertEquals(listOf("upper"), index.forVideoName("Episode.mp4"))
  }

  @Test
  fun `other media and files are not treated as subtitles`() {
    val index = SiblingSubtitleIndex(listOf("video.mp4" to "movie", "video.txt" to "notes"))
    assertTrue(index.forVideoName("video.mp4").isEmpty())
  }

  @Test
  fun `thousands of episodes match their own indexed subtitles`() {
    val names = (1..4000).map { "episode-${it.toString().padStart(5, '0')}" }
    val index = SiblingSubtitleIndex(names.map { "$it.ass" to "$it.ass" })
    names.forEach { assertEquals(listOf("$it.ass"), index.forVideoName("$it.mp4")) }
  }

  @Test
  fun `unicode names and empty subtitle directories work`() {
    val index = SiblingSubtitleIndex(listOf("中文 第1集.ja.vtt" to "unicode"))
    assertEquals(listOf("unicode"), index.forVideoName("中文 第1集.mkv"))
    assertTrue(SiblingSubtitleIndex(emptyList()).forVideoName("video.mp4").isEmpty())
  }
}
