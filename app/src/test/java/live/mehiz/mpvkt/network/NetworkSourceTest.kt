package live.mehiz.mpvkt.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkSourceTest {

  @Test
  fun `splitHost passes a plain host through untouched`() {
    val parts = NetworkSource.splitHost("192.168.1.10")
    assertEquals("192.168.1.10", parts.host)
    assertNull(parts.port)
    assertNull(parts.basePath)
  }

  @Test
  fun `splitHost strips a pasted scheme`() {
    val parts = NetworkSource.splitHost("http://192.168.1.10")
    assertEquals("192.168.1.10", parts.host)
    assertNull(parts.port)
    assertNull(parts.basePath)
  }

  @Test
  fun `splitHost splits scheme, port and base path`() {
    val parts = NetworkSource.splitHost("http://192.168.1.10:5244/dav")
    assertEquals("192.168.1.10", parts.host)
    assertEquals(5244, parts.port)
    assertEquals("/dav", parts.basePath)
  }

  @Test
  fun `splitHost handles trailing slashes and deep paths`() {
    val parts = NetworkSource.splitHost("https://nas.example.com:5006/media/movies/")
    assertEquals("nas.example.com", parts.host)
    assertEquals(5006, parts.port)
    assertEquals("/media/movies", parts.basePath)
  }

  @Test
  fun `splitHost ignores a non-numeric port`() {
    val parts = NetworkSource.splitHost("host:notaport")
    assertEquals("host", parts.host)
    assertNull(parts.port)
  }

  @Test
  fun `splitHost rejects out of range ports`() {
    val parts = NetworkSource.splitHost("host:99999")
    assertEquals("host", parts.host)
    assertNull(parts.port)
  }

  @Test
  fun `splitHost keeps an empty host as empty`() {
    val parts = NetworkSource.splitHost("  ")
    assertEquals("", parts.host)
    assertNull(parts.port)
    assertNull(parts.basePath)
  }
}
