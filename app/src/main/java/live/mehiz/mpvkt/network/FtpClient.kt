package live.mehiz.mpvkt.network

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.net.URLEncoder

class FtpClient(private val source: NetworkSource) : RemoteClient {

  private fun connect(): FTPClient = FTPClient().apply {
    connectTimeout = CONNECT_TIMEOUT_MILLIS
    controlEncoding = "UTF-8"
    connect(source.host, source.port)
    enterLocalPassiveMode()
    setFileType(FTP.BINARY_FILE_TYPE)
    if (source.username.isNotEmpty()) {
      login(source.username, source.password)
    } else {
      login(ANONYMOUS, ANONYMOUS)
    }
  }

  private fun remotePath(path: String): String = "/${path.trimStart('/')}"

  override fun list(path: String): List<RemoteEntry> {
    val client = connect()
    try {
      val files = client.listFiles(remotePath(path))
      return files.filterNotNull().map { RemoteEntry(it.name, it.isDirectory, it.size.toLong()) }
    } finally {
      runCatching { client.logout() }
      client.disconnect()
    }
  }

  override fun download(path: String, destination: File, expectedSize: Long): Boolean {
    val client = connect()
    try {
      val stream = client.retrieveFileStream(remotePath(path)) ?: return false
      stream.use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
      }
      // retrieveFileStream requires draining the reply before reusing the
      // connection; a truncated copy must never count as staged.
      val complete = client.completePendingCommand()
      val sized = destination.length() > 0 && (expectedSize <= 0 || destination.length() == expectedSize)
      return complete && sized
    } finally {
      runCatching { client.logout() }
      client.disconnect()
    }
  }

  override fun fileUrl(path: String): String {
    val auth = if (source.username.isEmpty()) {
      ""
    } else {
      "${urlEncode(source.username)}:${urlEncode(source.password)}@"
    }
    val base = source.basePath.trimEnd('/')
    return "ftp://$auth${source.host}:${source.port}$base/${path.trimStart('/')}"
  }

  private fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

  private companion object {
    const val CONNECT_TIMEOUT_MILLIS = 10_000
    const val ANONYMOUS = "anonymous"
  }
}
