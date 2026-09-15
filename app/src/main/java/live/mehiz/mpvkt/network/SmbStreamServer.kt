package live.mehiz.mpvkt.network

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Streams SMB files to mpv over loopback HTTP with byte-range support. mpv's
 * bundled ffmpeg has no SMB protocol, so [SmbClient] hands it a
 * `http://127.0.0.1:<port>/smb/<token>/<name>` URL; the server resolves the
 * random token back to a source+path and serves it by random access.
 *
 * Loopback-only and token-gated: the SMB credentials never reach mpv, only
 * the opaque URL. Targets live in a small LRU so a long browsing session
 * cannot grow the map without bound.
 */
class SmbStreamServer internal constructor(
  private val openStream: StreamOpener,
) {
  /**
   * Opens [path] and keeps it open for the duration of [block]. Implementations
   * must throw when the file cannot be opened and must not retain the reader
   * after returning.
   */
  fun interface StreamOpener {
    fun open(source: NetworkSource, path: String, block: (RemoteFileReader) -> Unit)
  }

  constructor() : this(
    StreamOpener { source, path, block -> SmbRemote(source).use { it.withReader(path, block) } },
  )

  private val lock = Any()
  private var serverSocket: ServerSocket? = null
  private val targets = object : LinkedHashMap<String, StreamTarget>(MAX_TARGETS, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamTarget>): Boolean =
      size > MAX_TARGETS
  }

  /**
   * Makes [path] playable through mpv and returns its loopback URL. The file
   * name stays as the last (encoded) segment so mpv still derives titles and
   * container hints from it.
   */
  fun register(source: NetworkSource, path: String): String {
    val port = ensureStarted()
    val token = UUID.randomUUID().toString().replace("-", "")
    synchronized(lock) {
      targets[token] = StreamTarget(source, path)
    }
    val name = URLEncoder.encode(path.substringAfterLast('/'), Charsets.UTF_8.name()).replace("+", "%20")
    return "http://$LOOPBACK:$port/$PATH_PREFIX$token/$name"
  }

  private fun ensureStarted(): Int = synchronized(lock) {
    serverSocket?.let { return it.localPort }
    // Bind IPv4 loopback explicitly: Android's getLoopbackAddress() resolves
    // to the IPv6 ::1, and mpv connects to the 127.0.0.1 literal below, which
    // a v6-only listener refuses ("Could not connect to server").
    val socket = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK))
    serverSocket = socket
    thread(isDaemon = true, name = "smb-stream-server") { acceptLoop(socket) }
    socket.localPort
  }

  private fun acceptLoop(server: ServerSocket) {
    while (true) {
      val socket = runCatching { server.accept() }
        .onFailure { Log.w(TAG, "SMB stream server stopped: ${it.message}") }
        .getOrNull() ?: return
      thread(isDaemon = true, name = "smb-stream") {
        runCatching { handle(socket) }
          .onFailure { Log.w(TAG, "SMB stream request failed: ${it.message}") }
      }
    }
  }

  private fun handle(socket: Socket) {
    socket.use {
      it.soTimeout = REQUEST_TIMEOUT_MILLIS
      val input = BufferedInputStream(it.getInputStream())
      val request = readRequest(input) ?: return
      val target = request.token?.let { token -> synchronized(lock) { targets[token] } }
      val output = BufferedOutputStream(it.getOutputStream())
      when {
        request.method != "GET" && request.method != "HEAD" -> writeHeaders(output, 405, 0, null, 0)
        target == null -> writeHeaders(output, 404, 0, null, 0)
        else -> serve(output, request, target)
      }
      output.flush()
    }
  }

  private data class HttpRequest(
    val method: String,
    val token: String?,
    val rangeHeader: String?,
    val headOnly: Boolean,
  )

  private fun readRequest(input: InputStream): HttpRequest? {
    val parts = (readLine(input) ?: return null).split(' ')
    val method = parts.getOrNull(0)?.uppercase()
    val target = parts.getOrNull(1)
    var range: String? = null
    var header = readLine(input)
    while (!header.isNullOrEmpty()) {
      val separator = header.indexOf(':')
      if (separator > 0 && header.substring(0, separator).trim().equals("Range", ignoreCase = true)) {
        range = header.substring(separator + 1).trim()
      }
      header = readLine(input)
    }
    return if (method == null || target == null) {
      null
    } else {
      val token = target.substringBefore('?').removePrefix("/$PATH_PREFIX").substringBefore('/')
      HttpRequest(method, token.takeIf { it.length == TOKEN_LENGTH }, range, method == "HEAD")
    }
  }

  private fun serve(output: OutputStream, request: HttpRequest, target: StreamTarget) {
    // Anything after the first packet is the response body: only a failure
    // while opening may still turn into an error status.
    var opened = false
    runCatching {
      openStream.open(target.source, target.path) { reader ->
        opened = true
        respond(output, request, reader)
      }
    }.onFailure {
      Log.w(TAG, "SMB stream for ${target.path} failed: ${it.message}")
      if (!opened) runCatching { writeHeaders(output, 502, 0, null, 0) }
    }
  }

  private fun respond(output: OutputStream, request: HttpRequest, reader: RemoteFileReader) {
    val spec = RangeSpec.parse(request.rangeHeader, reader.size)
    if (spec is RangeSpec.Unsatisfiable) {
      writeHeaders(output, 416, 0, null, reader.size)
      return
    }
    val range = (spec as? RangeSpec.Partial)?.range
    val length = range?.length ?: reader.size
    writeHeaders(output, if (range == null) 200 else 206, length, range, reader.size)
    if (!request.headOnly) {
      copyRange(reader, output, range?.start ?: 0, length)
      output.flush()
    }
  }

  private fun copyRange(reader: RemoteFileReader, output: OutputStream, start: Long, length: Long) {
    val buffer = ByteArray(BUFFER_SIZE)
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

  private fun writeHeaders(output: OutputStream, status: Int, length: Long, range: HttpRange?, total: Long) {
    val headers = buildString {
      append("HTTP/1.1 $status ${STATUS_TEXT[status]}\r\n")
      append("Content-Type: application/octet-stream\r\n")
      append("Accept-Ranges: bytes\r\n")
      append("Content-Length: $length\r\n")
      when {
        range != null -> append("Content-Range: bytes ${range.start}-${range.endInclusive}/$total\r\n")
        status == 416 -> append("Content-Range: bytes */$total\r\n")
      }
      append("Connection: close\r\n\r\n")
    }
    output.write(headers.toByteArray(Charsets.US_ASCII))
  }

  private fun readLine(input: InputStream): String? {
    val line = StringBuilder()
    while (line.length <= MAX_LINE_LENGTH) {
      val next = input.read()
      if (next < 0) break
      if (next == '\n'.code) return line.toString().trimEnd('\r')
      line.append(next.toChar())
    }
    return line.takeIf { it.isNotEmpty() }?.toString()
  }

  private companion object {
    const val TAG = "mpvKt"
    const val PATH_PREFIX = "smb/"
    const val LOOPBACK = "127.0.0.1"
    const val TOKEN_LENGTH = 32
    const val MAX_TARGETS = 64
    const val BACKLOG = 8
    const val BUFFER_SIZE = 64 * 1024
    const val REQUEST_TIMEOUT_MILLIS = 15_000
    const val MAX_LINE_LENGTH = 8 * 1024
    val STATUS_TEXT = mapOf(
      200 to "OK",
      206 to "Partial Content",
      404 to "Not Found",
      405 to "Method Not Allowed",
      416 to "Range Not Satisfiable",
      502 to "Bad Gateway",
    )
  }
}

private data class StreamTarget(val source: NetworkSource, val path: String)
