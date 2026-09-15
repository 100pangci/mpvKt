package live.mehiz.mpvkt.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class SmbParallelStreamerTest {

  private val data = ByteArray(5 * 1024 * 1024 + 123) { (it * 31).toByte() }

  @Test
  fun `keeps the bytes ordered even when the first chunk lags behind`() {
    val opens = AtomicInteger()
    val factory = SmbStreamServer.ReaderFactory {
      opens.incrementAndGet()
      DelayedReader(data)
    }
    val first = DelayedReader(data, delayMillis = 80)
    val output = ByteArrayOutputStream()

    SmbParallelStreamer(factory, 4, 0, data.size.toLong()).copy(output, first)

    assertArrayEquals(data, output.toByteArray())
    assertEquals("extra readers opened", 3, opens.get())
  }

  @Test
  fun `copies an unaligned range correctly`() {
    val start = 1_000_000L
    val factory = SmbStreamServer.ReaderFactory { DelayedReader(data) }
    val output = ByteArrayOutputStream()

    SmbParallelStreamer(factory, 4, start, data.size - start).copy(output, DelayedReader(data))

    assertArrayEquals(data.copyOfRange(start.toInt(), data.size), output.toByteArray())
    assertEquals((data.size - start).toInt(), output.size())
  }

  private class DelayedReader(private val data: ByteArray, private val delayMillis: Long = 0) : RemoteFileReader {
    override val size: Long get() = data.size.toLong()

    override fun read(offset: Long, buffer: ByteArray, length: Int): Int {
      if (delayMillis > 0) Thread.sleep(delayMillis)
      if (offset >= data.size) return 0
      val count = minOf(length.toLong(), data.size - offset).toInt()
      System.arraycopy(data, offset.toInt(), buffer, 0, count)
      return count
    }
  }
}
