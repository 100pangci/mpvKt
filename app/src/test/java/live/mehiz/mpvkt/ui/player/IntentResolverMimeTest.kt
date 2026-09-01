package live.mehiz.mpvkt.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentResolverMimeTest {

  @Test
  fun `media mimes are accepted`() {
    assertTrue("video/mp4".isMediaMime())
    assertTrue("video/x-matroska".isMediaMime())
    assertTrue("audio/flac".isMediaMime())
    assertTrue("image/png".isMediaMime())
    assertTrue("text/plain".isMediaMime())
    assertTrue("application/octet-stream".isMediaMime())
    assertTrue("application/x-matroska".isMediaMime())
    assertTrue("application/mp4".isMediaMime())
    assertTrue("application/ogg".isMediaMime())
  }

  @Test
  fun `non media mimes are rejected`() {
    assertFalse("application/pdf".isMediaMime())
    assertFalse("application/zip".isMediaMime())
    assertFalse("text/html".isMediaMime().not())
    assertFalse("".isMediaMime())
  }

  @Test
  fun `null mime is rejected by the predicate`() {
    assertFalse(null.isMediaMime())
  }
}
