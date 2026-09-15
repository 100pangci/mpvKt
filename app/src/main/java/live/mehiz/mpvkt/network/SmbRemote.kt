package live.mehiz.mpvkt.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import com.hierynomus.smbj.share.File as SmbFile

/**
 * One read-only SMB2/3 conversation with a [NetworkSource]. Every call
 * connects, authenticates, tree-connects, acts and tears down on its own, so
 * an unreachable NAS fails fast without poisoning later calls and instances
 * stay cheap to create on demand (browsing, font staging, streaming).
 */
internal class SmbRemote(private val source: NetworkSource) : AutoCloseable {

  private val client = SMBClient(
    SmbConfig.builder()
      .withTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .withSoTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .build(),
  )

  fun list(path: String): List<RemoteEntry> = withShare(path) { share, relative ->
    share.list(relative)
      .asSequence()
      .filterNot { it.fileName == "." || it.fileName == ".." }
      .map {
        RemoteEntry(
          name = it.fileName,
          isDirectory = it.fileAttributes and DIRECTORY_ATTRIBUTE != 0L,
          size = it.endOfFile,
        )
      }
      .toList()
  }

  fun download(path: String, destination: File, expectedSize: Long): Boolean =
    withShare(path) { share, relative ->
      open(share, relative).use { file ->
        file.inputStream.use { input ->
          destination.outputStream().use { output -> input.copyTo(output) }
        }
      }
      // A truncated copy must never count as staged.
      val length = destination.length()
      length > 0 && (expectedSize <= 0 || length == expectedSize)
    }

  /** Opens [path] for random access; the reader is only valid inside [block]. */
  fun <T> withReader(path: String, block: (RemoteFileReader) -> T): T =
    withShare(path) { share, relative ->
      open(share, relative).use { file ->
        block(SmbFileReader(file, file.fileInformation.standardInformation.endOfFile))
      }
    }

  private fun open(share: DiskShare, path: String): SmbFile = share.openFile(
    path,
    READ_ACCESS,
    null,
    SHARE_ACCESS,
    SMB2CreateDisposition.FILE_OPEN,
    null,
  )

  private fun <T> withShare(path: String, block: (DiskShare, String) -> T): T {
    val target = SmbRemotePath.resolve(source.basePath, path)
      ?: throw IOException("SMB root path must start with a share name, e.g. /media")
    val connection = client.connect(source.host, source.port)
    try {
      val session = connection.authenticate(credentials())
      try {
        val share = session.connectShare(target.share) as? DiskShare
          ?: throw IOException("SMB ${target.share} is not a disk share")
        try {
          return block(share, target.path)
        } finally {
          share.close()
        }
      } finally {
        session.close()
      }
    } finally {
      connection.close()
    }
  }

  /**
   * "DOMAIN\user" in the username field selects the NTLM domain; anything
   * else (including user@realm) goes through as the bare username. An empty
   * username authenticates as guest.
   */
  private fun credentials(): AuthenticationContext {
    val raw = source.username.trim()
    if (raw.isEmpty()) return AuthenticationContext.guest()
    val separator = raw.indexOf('\\')
    return if (separator > 0) {
      AuthenticationContext(
        raw.substring(separator + 1),
        source.password.toCharArray(),
        raw.substring(0, separator),
      )
    } else {
      AuthenticationContext(raw, source.password.toCharArray(), null)
    }
  }

  override fun close() {
    runCatching { client.close() }
  }

  private class SmbFileReader(private val file: SmbFile, override val size: Long) : RemoteFileReader {
    override fun read(offset: Long, buffer: ByteArray, length: Int): Int {
      var total = 0
      while (total < length) {
        val read = file.read(buffer, offset + total, total, length - total)
        if (read <= 0) break
        total += read
      }
      return total
    }
  }

  private companion object {
    const val TRANSFER_TIMEOUT_SECONDS = 30L
    val DIRECTORY_ATTRIBUTE = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value
    val READ_ACCESS: Set<AccessMask> = EnumSet.of(AccessMask.GENERIC_READ)
    val SHARE_ACCESS: Set<SMB2ShareAccess> = EnumSet.allOf(SMB2ShareAccess::class.java)
  }
}
