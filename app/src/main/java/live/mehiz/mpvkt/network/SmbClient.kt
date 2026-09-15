package live.mehiz.mpvkt.network

import java.io.File

/**
 * SMB source backed by smbj. Playback goes through [SmbStreamServer]: mpv's
 * bundled ffmpeg has no SMB protocol, so [fileUrl] hands it a loopback HTTP
 * URL while the credentials stay inside the app.
 */
class SmbClient(
  private val source: NetworkSource,
  private val streamServer: SmbStreamServer,
) : RemoteClient {

  override fun fileUrl(path: String): String = streamServer.register(source, path)

  override fun list(path: String): List<RemoteEntry> =
    SmbRemote(source).use { it.list(path) }

  override fun download(path: String, destination: File, expectedSize: Long): Boolean =
    SmbRemote(source).use { it.download(path, destination, expectedSize) }
}
