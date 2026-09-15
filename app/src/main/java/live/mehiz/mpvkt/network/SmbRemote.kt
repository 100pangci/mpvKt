package live.mehiz.mpvkt.network

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * Read-only SMB access to one [NetworkSource], backed by jcifs-ng. Every
 * call opens its own session/tree/file and closes it again, so an
 * unreachable NAS fails fast without poisoning later calls and instances
 * stay cheap to create on demand (browsing, font staging, streaming).
 *
 * A blank root path lists the server's shares, which only jcifs-ng can
 * enumerate (smbj cannot); selecting a share then browses it.
 */
internal class SmbRemote(private val source: NetworkSource) : AutoCloseable {

  private val context: CIFSContext = createContext()

  fun list(path: String): List<RemoteEntry> {
    val target = SmbRemotePath.resolve(source.basePath, path) ?: return listShares()
    return fileFor(target).listFiles().mapNotNull { it.toRemoteEntry() }
  }

  fun download(path: String, destination: File, expectedSize: Long): Boolean {
    fileFor(requireTarget(path)).openInputStream().use { input ->
      destination.outputStream().use { output -> input.copyTo(output) }
    }
    // A truncated copy must never count as staged.
    val length = destination.length()
    return length > 0 && (expectedSize <= 0 || length == expectedSize)
  }

  /** Opens [path] for random access; the reader is only valid inside [block]. */
  fun <T> withReader(path: String, block: (RemoteFileReader) -> T): T =
    SmbRandomAccessFile(fileFor(requireTarget(path)), "r").use { random ->
      block(SmbRandomFileReader(random, random.length()))
    }

  private fun fileFor(target: SmbRemotePath): SmbFile {
    val share = SmbFile(SmbFile(serverUrl(), context), target.share)
    return target.path.split('/')
      .filter { it.isNotEmpty() }
      .fold(share) { parent, name -> SmbFile(parent, name) }
  }

  /** Server root: jcifs-ng enumerates shares here, hidden ones ($) are dropped. */
  private fun listShares(): List<RemoteEntry> =
    SmbFile(serverUrl(), context).listFiles()
      .filterNot { it.name.endsWith("$") }
      .map { RemoteEntry(it.name, true, 0L) }

  private fun SmbFile.toRemoteEntry(): RemoteEntry? = runCatching {
    val directory = isDirectory
    RemoteEntry(name, directory, if (directory) 0L else length())
  }.getOrNull()

  private fun requireTarget(path: String): SmbRemotePath =
    SmbRemotePath.resolve(source.basePath, path)
      ?: throw IOException("Open a share on ${source.host} first")

  private fun serverUrl(): String = "smb://${source.host}:${source.port}/"

  private fun createContext(): CIFSContext {
    val properties = Properties().apply {
      setProperty("jcifs.smb.client.connTimeout", CONNECT_TIMEOUT_MILLIS.toString())
      setProperty("jcifs.smb.client.responseTimeout", TRANSFER_TIMEOUT_MILLIS.toString())
      setProperty("jcifs.smb.client.soTimeout", TRANSFER_TIMEOUT_MILLIS.toString())
      // DFS referrals and NetBIOS broadcast lookups only add latency on a
      // plain home network; direct DNS is enough.
      setProperty("jcifs.smb.client.dfs.disabled", "true")
      setProperty("jcifs.resolveOrder", "DNS")
    }
    val base = BaseContext(PropertyConfiguration(properties))
    val username = source.username.trim()
    return if (username.isEmpty()) {
      base.withGuestCrendentials()
    } else {
      // "DOMAIN\user" and "user@realm" are split up by the authenticator.
      base.withCredentials(NtlmPasswordAuthenticator(username, source.password))
    }
  }

  override fun close() {
    runCatching { context.close() }
  }

  private class SmbRandomFileReader(
    private val file: SmbRandomAccessFile,
    override val size: Long,
  ) : RemoteFileReader {
    override fun read(offset: Long, buffer: ByteArray, length: Int): Int {
      file.seek(offset)
      var total = 0
      while (total < length) {
        val read = file.read(buffer, total, length - total)
        if (read <= 0) break
        total += read
      }
      return total
    }
  }

  private companion object {
    const val CONNECT_TIMEOUT_MILLIS = 10_000
    const val TRANSFER_TIMEOUT_MILLIS = 30_000
  }
}
