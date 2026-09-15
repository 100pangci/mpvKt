package live.mehiz.mpvkt.network

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Streams one byte range through several independent SMB readers at once.
 *
 * jcifs-ng caps every SMB2 read at roughly 64 KiB and issues it
 * synchronously, so a single reader is latency-bound (about 100 Mbit/s on a
 * typical Wi-Fi link). Each reader below keeps one 64 KiB read in flight;
 * completed chunks are written strictly in order, so the byte stream is
 * identical to a sequential copy while throughput scales with the number of
 * readers.
 */
internal class SmbParallelStreamer(
  private val factory: SmbStreamServer.ReaderFactory,
  private val readers: Int,
  private val start: Long,
  private val length: Long,
) {
  private val chunks = ((length + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
  private val workers = minOf(readers, chunks).coerceAtLeast(1)

  fun copy(output: OutputStream, first: RemoteFileReader) {
    if (workers <= 1) {
      copySequential(first, output)
      return
    }
    val pool = newPool()
    try {
      pipe(output, first, pool)
    } finally {
      pool.shutdownNow()
    }
  }

  private fun pipe(output: OutputStream, first: RemoteFileReader, pool: ExecutorService) {
    val completion = ExecutorCompletionService<Chunk>(pool)
    // One handle per worker; extra handles open lazily in their own task so
    // the extra SMB handshakes overlap the first reads.
    val handles = arrayOfNulls<RemoteFileReader>(workers)
    handles[0] = first
    var submitted = 0
    var written = 0
    val pending = HashMap<Int, ByteArray>()
    try {
      while (submitted < chunks && submitted < workers) submit(completion, handles, submitted++, start, length)
      while (written < chunks) {
        val chunk = await(completion)
        pending[chunk.index] = chunk.data
        while (true) {
          val data = pending.remove(written) ?: break
          output.write(data)
          written++
        }
        // A window of one chunk per reader guarantees the reader is idle
        // again before its next chunk is submitted.
        while (submitted < chunks && submitted - written < handles.size) {
          submit(completion, handles, submitted++, start, length)
        }
      }
    } finally {
      // The first handle belongs to the caller; the rest are ours.
      for (index in 1 until handles.size) {
        handles[index]?.let { runCatching { it.close() } }
      }
    }
  }

  private fun submit(
    completion: ExecutorCompletionService<Chunk>,
    handles: Array<RemoteFileReader?>,
    index: Int,
    start: Long,
    length: Long,
  ) {
    val worker = index % handles.size
    val offset = start + index.toLong() * CHUNK_SIZE
    val size = minOf(CHUNK_SIZE.toLong(), start + length - offset).toInt()
    completion.submit {
      val reader = openWorker(handles, worker)
      Chunk(index, readFully(reader, offset, size))
    }
  }

  private fun openWorker(handles: Array<RemoteFileReader?>, worker: Int): RemoteFileReader =
    handles[worker] ?: synchronized(handles) {
      val existing = handles[worker]
      if (existing != null) {
        existing
      } else {
        val opened = factory.open()
        handles[worker] = opened
        opened
      }
    }

  private fun await(completion: ExecutorCompletionService<Chunk>): Chunk {
    val done = completion.take()
    return try {
      done.get()
    } catch (e: ExecutionException) {
      throw (e.cause as? IOException) ?: IOException(e.cause)
    }
  }

  private fun readFully(reader: RemoteFileReader, offset: Long, size: Int): ByteArray {
    val buffer = ByteArray(size)
    var total = 0
    while (total < size) {
      val read = reader.read(offset + total, buffer, size - total)
      if (read <= 0) break
      total += read
    }
    return if (total == size) buffer else buffer.copyOf(total)
  }

  private fun copySequential(reader: RemoteFileReader, output: OutputStream) {
    val buffer = ByteArray(CHUNK_SIZE)
    var position = start
    var remaining = length
    while (remaining > 0) {
      val wanted = minOf(buffer.size.toLong(), remaining).toInt()
      val read = reader.read(position, buffer, wanted)
      if (read <= 0) return
      output.write(buffer, 0, read)
      position += read
      remaining -= read
    }
  }

  private fun newPool(): ExecutorService =
    Executors.newFixedThreadPool(workers) { runnable ->
      Thread(runnable, "smb-stream-read").apply { isDaemon = true }
    }

  private class Chunk(val index: Int, val data: ByteArray)

  private companion object {
    const val CHUNK_SIZE = 64 * 1024
  }
}
