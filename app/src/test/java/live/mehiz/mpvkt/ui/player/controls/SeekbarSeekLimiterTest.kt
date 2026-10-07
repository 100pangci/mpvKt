package live.mehiz.mpvkt.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekbarSeekLimiterTest {
  @Test
  fun `rapid pointer updates are limited to one preview per interval`() {
    val limiter = SeekbarSeekLimiter()
    assertEquals(10, limiter.update(10, 0))
    assertNull(limiter.update(20, 10))
    assertNull(limiter.update(30, 149))
    assertEquals(40, limiter.update(40, 150))
  }

  @Test
  fun `release flushes the latest suppressed target`() {
    val limiter = SeekbarSeekLimiter()
    limiter.update(10, 0)
    limiter.update(20, 20)
    limiter.update(30, 30)
    assertEquals(30, limiter.finish(precise = false))
    assertNull(limiter.finish(precise = false))
  }

  @Test
  fun `non precise release does not repeat the last keyframe seek`() {
    val limiter = SeekbarSeekLimiter()
    limiter.update(10, 0)
    assertNull(limiter.finish(precise = false))
  }

  @Test
  fun `precise release refines the preview exactly once`() {
    val limiter = SeekbarSeekLimiter()
    limiter.update(10, 0)
    assertEquals(10, limiter.finish(precise = true))
    assertNull(limiter.finish(precise = true))
  }

  @Test
  fun `whole second duplicates do not issue extra preview seeks`() {
    val limiter = SeekbarSeekLimiter()
    assertEquals(10, limiter.update(10, 0))
    assertNull(limiter.update(10, 150))
    assertNull(limiter.update(10, 300))
    assertEquals(11, limiter.update(11, 310))
  }

  @Test
  fun `next drag is not throttled by the preceding drag`() {
    val limiter = SeekbarSeekLimiter()
    limiter.update(10, 100)
    limiter.finish(precise = false)
    assertEquals(20, limiter.update(20, 101))
  }

  @Test
  fun `release without a drag never seeks`() {
    val limiter = SeekbarSeekLimiter()
    assertNull(limiter.finish(precise = false))
    assertNull(limiter.finish(precise = true))
  }

  @Test
  fun `returning to the last preview target avoids a redundant release seek`() {
    val limiter = SeekbarSeekLimiter()
    limiter.update(10, 0)
    limiter.update(20, 10)
    limiter.update(10, 20)
    assertNull(limiter.finish(precise = false))
  }
}
