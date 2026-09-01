package live.mehiz.mpvkt.player

import android.content.Context
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import java.io.File

/**
 * Generates the `fonts.conf` consumed by mpv/libass. mpv reads this file from
 * its config directory (filesDir) on every subtitle renderer initialization
 * and hands it to libass's fontconfig provider, which indexes the listed
 * directories *in place*: fonts are matched by the family names inside their
 * name tables (never by file name) and opened lazily by path, so nothing is
 * ever copied. Per-directory binary caches are written to a writable
 * `<cachedir>` in the app cache, making warm renderer initializations take
 * milliseconds — desktop-mpv behavior.
 */
class FontConfigManager(
  private val context: Context,
  private val subtitlesPreferences: SubtitlesPreferences,
) {

  val configFile: File get() = File(context.filesDir, "fonts.conf")

  private val fcCacheDir: File get() = File(context.cacheDir, "fontconfig").apply { mkdirs() }

  /**
   * Rewrites `fonts.conf` from the current preferences. [videoPath] adds the
   * video's own directory (and its `fonts` subdirectory) so sibling fonts are
   * indexed in place; the renderer re-reads the file on every track load.
   */
  fun regenerate(videoPath: String? = null) {
    runCatching {
      val dirs = buildList {
        if (subtitlesPreferences.useSystemFonts.get()) {
          add(FontIndexer.SYSTEM_FONT_DIR)
          add(FontIndexer.STORAGE_FONTS_DIR)
        }
        fileDirFromTreeUri(
          android.net.Uri.parse(subtitlesPreferences.fontsFolder.get()),
        )?.let { add(it.absolutePath) }
        addAll(videoDirs(videoPath))
      }.filter { File(it).isDirectory }.distinct()
      configFile.writeText(render(dirs))
    }
  }

  private fun videoDirs(videoPath: String?): List<String> {
    val parent = videoPath
      ?.takeUnless { it.startsWith("fd://") }
      ?.let { runCatching { File(it) }.getOrNull() }
      ?.takeIf { it.isFile }
      ?.parentFile
      ?: return emptyList()
    return listOf(parent.absolutePath, File(parent, "fonts").absolutePath)
  }

  private fun render(dirs: List<String>): String = buildString {
    append("<fontconfig>\n")
    dirs.forEach { dir -> append("  <dir>").append(dir.escapeXml()).append("</dir>\n") }
    append("  <cachedir>").append(fcCacheDir.absolutePath.escapeXml()).append("</cachedir>\n")
    append("</fontconfig>\n")
  }

  private fun String.escapeXml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
