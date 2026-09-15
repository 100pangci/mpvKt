package live.mehiz.mpvkt.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class SmbStreamServerTest {

  private val data = ByteArray(1000) { it.toByte() }
  private val server = SmbStreamServer(
    SmbStreamServer.StreamOpener { _, _, block -> block(FakeReader(data)) },
  )
  private val source = NetworkSource(
    id = 1,
    type = NetworkType.SMB,
    name = "test",
    host = "127.0.0.1",
    port = 445,
    basePath = "/media",
  )

  @Test
  fun `serves the full body without a range header`() {
    val connection = open()
    assertEquals(200, connection.responseCode)
    assertEquals(data.size.toLong(), connection.getHeaderFieldLong("Content-Length", -1))
    assertArrayEquals(data, connection.readBody())
  }

  @Test
  fun `serves a partial range`() {
    val connection = open("bytes=100-199")
    assertEquals(206, connection.responseCode)
    assertEquals("bytes 100-199/1000", connection.getHeaderField("Content-Range"))
    assertArrayEquals(data.copyOfRange(100, 200), connection.readBody())
  }

  @Test
  fun `serves an open ended range`() {
    val connection = open("bytes=900-")
    assertEquals(206, connection.responseCode)
    assertArrayEquals(data.copyOfRange(900, 1000), connection.readBody())
  }

  @Test
  fun `serves a suffix range`() {
    val connection = open("bytes=-100")
    assertEquals(206, connection.responseCode)
    assertEquals("bytes 900-999/1000", connection.getHeaderField("Content-Range"))
    assertArrayEquals(data.copyOfRange(900, 1000), connection.readBody())
  }

  @Test
  fun `rejects a range past the end`() {
    val connection = open("bytes=5000-")
    assertEquals(416, connection.responseCode)
    assertEquals("bytes */1000", connection.getHeaderField("Content-Range"))
  }

  @Test
  fun `answers HEAD with the size and no body`() {
    val connection = open()
    connection.requestMethod = "HEAD"
    assertEquals(200, connection.responseCode)
    assertEquals(data.size.toLong(), connection.getHeaderFieldLong("Content-Length", -1))
    assertEquals(0, connection.inputStream.use { it.readBytes().size })
  }

  @Test
  fun `rejects unknown tokens`() {
    val unknown = URL(urlFor().toString().replace(TOKEN, "0".repeat(32)))
    val response = unknown.openConnection() as HttpURLConnection
    assertEquals(404, response.responseCode)
  }

  private fun urlFor(path: String = "dir/movie.mkv"): URL = URL(server.register(source, path))

  private fun open(range: String? = null): HttpURLConnection =
    (urlFor().openConnection() as HttpURLConnection).apply {
      if (range != null) setRequestProperty("Range", range)
    }

  private fun HttpURLConnection.readBody(): ByteArray = inputStream.use { it.readBytes() }

  private class FakeReader(private val data: ByteArray) : RemoteFileReader {
    override val size: Long get() = data.size.toLong()

    override fun read(offset: Long, buffer: ByteArray, length: Int): Int {
      if (offset >= data.size) return 0
      val count = minOf(length.toLong(), data.size - offset).toInt()
      System.arraycopy(data, offset.toInt(), buffer, 0, count)
      return count
    }
  }

  private companion object {
    val TOKEN = Regex("[0-9a-f]{32}")
  }
}
