package live.mehiz.mpvkt.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HorizontalSeekSessionTest {
  @Test
  fun `drag previews are throttled and release uses the latest target`() {
    val session = HorizontalSeekSession()
    session.start(paused = false)
    assertEquals(10, session.update(10, 0))
    assertNull(session.update(20, 10))
    assertEquals(HorizontalSeekCompletion(20, false, true), session.finish(precise = false))
  }

  @Test
  fun `cancel restores running state without issuing a final seek`() {
    val session = HorizontalSeekSession()
    session.start(paused = false)
    session.update(10, 0)
    session.update(20, 10)
    assertEquals(HorizontalSeekCompletion(null, false, true), session.finish(precise = false, cancelled = true))
    assertFalse(session.isActive)
    assertNull(session.finish(precise = false, cancelled = true))
  }

  @Test
  fun `cancel never starts a video that was already paused`() {
    val session = HorizontalSeekSession()
    session.start(paused = true)
    assertEquals(HorizontalSeekCompletion(null, true, false), session.finish(precise = true, cancelled = true))
  }

  @Test
  fun `precise setting is respected on normal release`() {
    val session = HorizontalSeekSession()
    session.start(paused = true)
    session.update(10, 0)
    assertEquals(HorizontalSeekCompletion(10, true, false), session.finish(precise = true))
    session.start(paused = true)
    session.update(10, 1)
    assertEquals(HorizontalSeekCompletion(null, false, false), session.finish(precise = false))
  }

  @Test
  fun `disposal before a drag and repeated cleanup are harmless`() {
    val session = HorizontalSeekSession()
    assertNull(session.update(10, 0))
    assertNull(session.finish(precise = false, cancelled = true))
    session.start(paused = false)
    session.finish(precise = false)
    assertNull(session.finish(precise = false, cancelled = true))
  }
}
