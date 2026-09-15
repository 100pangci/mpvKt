package live.mehiz.mpvkt.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeSpecTest {

  @Test
  fun `missing or foreign headers mean the full body`() {
    assertEquals(RangeSpec.Full, RangeSpec.parse(null, 1000))
    assertEquals(RangeSpec.Full, RangeSpec.parse("", 1000))
    assertEquals(RangeSpec.Full, RangeSpec.parse("items=0-10", 1000))
    assertEquals(RangeSpec.Full, RangeSpec.parse("bytes=abc-def", 1000))
    assertEquals(RangeSpec.Full, RangeSpec.parse("bytes=0-10,20-30", 1000))
  }

  @Test
  fun `parses a closed range`() {
    assertEquals(RangeSpec.Partial(HttpRange(100, 199)), RangeSpec.parse("bytes=100-199", 1000))
  }

  @Test
  fun `scheme is case insensitive`() {
    assertEquals(RangeSpec.Partial(HttpRange(0, 0)), RangeSpec.parse("Bytes=0-0", 1000))
  }

  @Test
  fun `open ended range runs to the end of the file`() {
    assertEquals(RangeSpec.Partial(HttpRange(900, 999)), RangeSpec.parse("bytes=900-", 1000))
  }

  @Test
  fun `end past the file is clamped`() {
    assertEquals(RangeSpec.Partial(HttpRange(900, 999)), RangeSpec.parse("bytes=900-5000", 1000))
  }

  @Test
  fun `suffix range counts back from the end`() {
    assertEquals(RangeSpec.Partial(HttpRange(900, 999)), RangeSpec.parse("bytes=-100", 1000))
  }

  @Test
  fun `suffix larger than the file starts at zero`() {
    assertEquals(RangeSpec.Partial(HttpRange(0, 999)), RangeSpec.parse("bytes=-5000", 1000))
  }

  @Test
  fun `start past the file is unsatisfiable`() {
    assertEquals(RangeSpec.Unsatisfiable, RangeSpec.parse("bytes=1000-", 1000))
    assertEquals(RangeSpec.Unsatisfiable, RangeSpec.parse("bytes=1000-2000", 1000))
  }

  @Test
  fun `end before start is unsatisfiable`() {
    assertEquals(RangeSpec.Unsatisfiable, RangeSpec.parse("bytes=500-100", 1000))
  }

  @Test
  fun `zero length suffix is unsatisfiable`() {
    assertEquals(RangeSpec.Unsatisfiable, RangeSpec.parse("bytes=-0", 1000))
  }

  @Test
  fun `empty files have no satisfiable ranges`() {
    assertEquals(RangeSpec.Unsatisfiable, RangeSpec.parse("bytes=0-", 0))
    assertEquals(RangeSpec.Unsatisfiable, RangeSpec.parse("bytes=-10", 0))
    assertEquals(RangeSpec.Full, RangeSpec.parse(null, 0))
  }
}
