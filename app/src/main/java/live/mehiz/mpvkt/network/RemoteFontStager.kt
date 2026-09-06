package live.mehiz.mpvkt.network

import android.util.Log
import java.io.File

/**
 * Bridges remote playback and the subtitle font pipeline: WebDAV/FTP
 * directories often ship a sibling `fonts/` folder which fontconfig cannot
 * index (remote path), so the fonts are downloaded into the subtitle font
 * cache — mpv's `sub-fonts-dir` already points there, no mpv config change
 * needed. Only called by the player right before a remote file plays.
 */
class RemoteFontStager(private val factory: RemoteClientFactory) {

  /**
   * Downloads the fonts next to the video at [videoDirPath] into [destDir].
   * Same name+size skips; per-file and total caps keep a runaway server from
   * filling the cache. Every failure is logged and swallowed: this must
   * never block or break playback. @return whether any font was downloaded.
   */
  fun stageFonts(source: NetworkSource, videoDirPath: String, destDir: File): Boolean {
    val client = factory.create(source)
    return try {
      val fontsPath = NetworkSource.joinPath(videoDirPath, FONTS_DIR)
      val fonts = runCatching { client.list(fontsPath) }
        .onFailure { Log.w(TAG, "remote fonts list failed: ${it.message}") }
        .getOrDefault(emptyList())
        .filter { !it.isDirectory && it.name.isFontFile() }
      var stagedAny = false
      var totalBytes = 0L
      for (font in fonts) {
        val skipReason = when {
          font.size > MAX_FONT_FILE_BYTES -> "too large (${font.size} bytes)"
          totalBytes + font.size > MAX_TOTAL_BYTES -> "budget exhausted"
          // Same name+size already cached: nothing to do.
          File(destDir, font.name).isFile && File(destDir, font.name).length() == font.size -> null
          else -> null
        }
        if (skipReason != null) {
          Log.w(TAG, "remote font ${font.name} skipped: $skipReason")
          continue
        }
        val target = File(destDir, font.name)
        val downloaded = runCatching {
          client.download("$fontsPath/${font.name}", target, font.size)
        }.onFailure { Log.w(TAG, "remote font ${font.name} download failed: ${it.message}") }
          .getOrDefault(false)
        if (downloaded) {
          stagedAny = true
          totalBytes += font.size
        }
      }
      stagedAny
    } finally {
      client.close()
    }
  }

  private fun String.isFontFile(): Boolean = substringAfterLast('.', "").lowercase() in FONT_EXTENSIONS

  private companion object {
    const val TAG = "mpvKt"
    const val FONTS_DIR = "fonts"
    const val MAX_FONT_FILE_BYTES = 50L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
    val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
  }
}
