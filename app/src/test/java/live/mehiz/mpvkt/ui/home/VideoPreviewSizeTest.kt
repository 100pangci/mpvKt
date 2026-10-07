package live.mehiz.mpvkt.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPreviewSizeTest {
  @Test
  fun `landscape video fits within preview bounds`() {
    assertEquals(288 to 162, fitVideoPreviewSize(3840, 2160, 288, 162))
  }

  @Test
  fun `portrait video keeps its aspect ratio`() {
    assertEquals(91 to 162, fitVideoPreviewSize(1080, 1920, 288, 162))
  }

  @Test
  fun `square video is not stretched`() {
    assertEquals(162 to 162, fitVideoPreviewSize(1080, 1080, 288, 162))
  }

  @Test
  fun `small videos are not enlarged`() {
    assertEquals(80 to 45, fitVideoPreviewSize(80, 45, 288, 162))
  }

  @Test
  fun `extreme aspect ratios never produce zero dimensions`() {
    assertEquals(288 to 1, fitVideoPreviewSize(100000, 1, 288, 162))
    assertEquals(1 to 162, fitVideoPreviewSize(1, 100000, 288, 162))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `invalid dimensions are rejected`() {
    fitVideoPreviewSize(0, 1080, 288, 162)
  }
}
