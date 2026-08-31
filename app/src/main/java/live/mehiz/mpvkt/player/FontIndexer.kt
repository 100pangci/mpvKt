package live.mehiz.mpvkt.player

import com.github.k1rakishou.fsaf.FileManager
import com.yubyf.truetypeparser.TTFFile
import live.mehiz.mpvkt.database.dao.FontDao
import live.mehiz.mpvkt.database.entities.FontEntity
import java.io.File
import java.io.InputStream

private typealias FsaFile = com.github.k1rakishou.fsaf.file.AbstractFile

/**
 * Scans font folders, parses family names out of the font files and keeps the
 * [FontDao] index up to date. The index is what makes per-video on-demand font
 * staging possible without copying entire (potentially thousand-file) font
 * libraries on every playback.
 */
class FontIndexer(
  private val fontDao: FontDao,
  private val fileManager: FileManager,
) {
  suspend fun reindexUserFolder(folderUri: String) {
    val root = fileManager.fromUri(android.net.Uri.parse(folderUri)) ?: return
    if (!fileManager.exists(root)) return
    reindexSource(SOURCE_USER, collectFontFiles(root))
  }

  suspend fun reindexSystemFonts() {
    val systemDir = File(SYSTEM_FONT_DIR)
    if (!systemDir.isDirectory) return
    val files = systemDir.listFiles { f -> f.isFile && FONT_EXTENSIONS.matches(f.name) }
      ?.map { f ->
        FontFileInfo(f.absolutePath, f.lastModified(), f.length()) { f.inputStream() }
      }
      ?: emptyList()
    reindexSource(SOURCE_SYSTEM, files)
  }

  suspend fun findFontPaths(family: String): List<String> =
    fontDao.findByName(family).map { it.path }.distinct()

  suspend fun collectFontFiles(root: FsaFile): List<FontFileInfo> {
    fun walk(dir: FsaFile, prefix: String): List<FontFileInfo> {
      if (!fileManager.isDirectory(dir)) {
        val name = fileManager.getName(dir)
        return if (fileManager.isFile(dir) && FONT_EXTENSIONS.matches(name)) {
          listOf(FontFileInfo("$prefix$name", 0L, 0L) { fileManager.getInputStream(dir) })
        } else {
          emptyList()
        }
      }
      return fileManager.listFiles(dir).flatMap { child ->
        walk(child, "$prefix${fileManager.getName(child)}/")
      }
    }
    return walk(root, "")
  }

  private suspend fun reindexSource(source: String, files: List<FontFileInfo>) {
    val seen = mutableSetOf<String>()
    for (file in files) {
      seen.add(file.path)
      val existing = fontDao.findByPath(file.path)
      val unchanged = existing.isNotEmpty() &&
        existing.first().lastModified == file.lastModified &&
        existing.first().size == file.size
      if (!unchanged) {
        fontDao.deleteByPath(file.path)
        val families = parseFamilies(file)
        if (families.isNotEmpty()) {
          fontDao.insertAll(
            families.map { family ->
              FontEntity(file.path, family, file.lastModified, file.size, source)
            },
          )
        }
      }
    }
    fontDao.pathsForSource(source).forEach { path ->
      if (path !in seen) fontDao.deleteByPath(path)
    }
  }

  private fun parseFamilies(info: FontFileInfo): List<String> {
    val stream = info.openStream() ?: return emptyList()
    return stream.use { input ->
      runCatching {
        TTFFile.open(input).families.values
          .filterNotNull()
          .map { it.trim() }
          .filter { it.isNotEmpty() }
      }.getOrDefault(emptyList())
    }.distinct()
  }

  data class FontFileInfo(
    val path: String,
    val lastModified: Long,
    val size: Long,
    val openStream: () -> InputStream?,
  )

  companion object {
    const val SOURCE_USER = "user"
    const val SOURCE_SYSTEM = "system"
    const val SYSTEM_FONT_DIR = "/system/fonts"
    val FONT_EXTENSIONS = Regex(".*\\.(ttf|otf|ttc)\\z", RegexOption.IGNORE_CASE)
  }
}
