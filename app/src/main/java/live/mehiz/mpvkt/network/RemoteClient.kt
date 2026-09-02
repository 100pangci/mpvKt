package live.mehiz.mpvkt.network

import java.io.File

data class RemoteEntry(
  val name: String,
  val isDirectory: Boolean,
  val size: Long,
)

/**
 * Minimal read-only client for a network media source: list a directory and
 * stream a file down. Implementations are blocking and must only be called
 * off the main thread.
 */
interface RemoteClient : AutoCloseable {
  /**
   * The mpv-openable URL for a remote path. Credentials are embedded in the
   * URL (mpv's stream layer resolves them); treat the result as a secret.
   */
  fun fileUrl(path: String): String

  fun list(path: String): List<RemoteEntry>

  /** @return true when [destination] holds a complete copy of the file. */
  fun download(path: String, destination: File, expectedSize: Long): Boolean

  override fun close() {}
}
