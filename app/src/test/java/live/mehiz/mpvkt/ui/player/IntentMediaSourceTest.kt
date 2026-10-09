package live.mehiz.mpvkt.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentMediaSourceTest {
  @Test
  fun `text mime with data URI and missing shared text uses the data`() {
    assertEquals("content://videos/1", chooseIntentMediaSource("content://videos/1", null, null))
  }

  @Test
  fun `stream URI works without shared text`() {
    assertEquals("content://videos/2", chooseIntentMediaSource(null, "content://videos/2", null))
  }

  @Test
  fun `data then stream take priority over an unrelated text field`() {
    assertEquals("content://videos/1", chooseIntentMediaSource("content://videos/1", "content://videos/2", "caption"))
    assertEquals("content://videos/2", chooseIntentMediaSource(null, "content://videos/2", "caption"))
  }

  @Test
  fun `text only shares are trimmed and still supported`() {
    assertEquals(
      "https://example.org/video.mp4",
      chooseIntentMediaSource(null, null, "  https://example.org/video.mp4  "),
    )
  }

  @Test
  fun `missing or blank sources return null instead of throwing`() {
    assertNull(chooseIntentMediaSource(null, null, null))
    assertNull(chooseIntentMediaSource("", " ", "\n"))
  }
}
