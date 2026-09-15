package live.mehiz.mpvkt.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmbRemotePathTest {

  @Test
  fun `resolves a share-only base path`() {
    val target = SmbRemotePath.resolve("/media", "")
    assertEquals("media", target?.share)
    assertEquals("", target?.path)
  }

  @Test
  fun `joins base folders and the browsed path`() {
    val target = SmbRemotePath.resolve("/media/Movies", "S1/ep1.mkv")
    assertEquals("media", target?.share)
    assertEquals("Movies/S1/ep1.mkv", target?.path)
  }

  @Test
  fun `takes the share from the browsed path when the root path is blank`() {
    val target = SmbRemotePath.resolve("", "media/S1/ep1.mkv")
    assertEquals("media", target?.share)
    assertEquals("S1/ep1.mkv", target?.path)
  }

  @Test
  fun `resolves a share picked straight from the share list`() {
    val target = SmbRemotePath.resolve("", "media")
    assertEquals("media", target?.share)
    assertEquals("", target?.path)
  }

  @Test
  fun `ignores redundant separators`() {
    val target = SmbRemotePath.resolve("//media//Movies//", "/S1/")
    assertEquals("media", target?.share)
    assertEquals("Movies/S1", target?.path)
  }

  @Test
  fun `accepts backslash paths`() {
    val target = SmbRemotePath.resolve("\\media\\Movies", "S1\\ep1.mkv")
    assertEquals("media", target?.share)
    assertEquals("Movies/S1/ep1.mkv", target?.path)
  }

  @Test
  fun `an empty location means the server root`() {
    assertNull(SmbRemotePath.resolve("", ""))
    assertNull(SmbRemotePath.resolve("/", ""))
    assertNull(SmbRemotePath.resolve("  ", " / "))
  }
}
