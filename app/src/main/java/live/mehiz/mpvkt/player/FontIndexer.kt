package live.mehiz.mpvkt.player

import com.github.k1rakishou.fsaf.FileManager
import com.yubyf.truetypeparser.TTFFile
import live.mehiz.mpvkt.database.dao.FontDao
import live.mehiz.mpvkt.database.dao.FontMeta
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
  val isScanning = kotlinx.coroutines.flow.MutableStateFlow(false)
  val scanDone = kotlinx.coroutines.flow.MutableStateFlow(0)
  val scanTotal = kotlinx.coroutines.flow.MutableStateFlow(0)

  private suspend fun <T> withScanProgress(block: suspend () -> T): T {
    isScanning.value = true
    try {
      return block()
    } finally {
      isScanning.value = false
    }
  }
  suspend fun reindexUserFolder(folderUri: String) = withScanProgress {
    val uri = android.net.Uri.parse(folderUri)
    // Real-filesystem walk when the tree URI maps to an accessible directory:
    // one listFiles() per directory yields name/size/mtime in a single call,
    // vs one binder round-trip per file through the documents provider.
    val files = fileDirFromTreeUri(uri)?.let { dir -> fastFontFiles(dir) }
      ?: run {
        val root = fileManager.fromUri(uri) ?: return@withScanProgress
        if (!fileManager.exists(root)) return@withScanProgress
        collectFontFiles(root)
      }
    reindexSource(SOURCE_USER, files)
  }

  suspend fun reindexSystemFonts() = withScanProgress {
    val files = listOf(SYSTEM_FONT_DIR, STORAGE_FONTS_DIR).flatMap { dirName ->
      val dir = File(dirName)
      if (!dir.isDirectory) {
        emptyList()
      } else {
        dir.listFiles { f -> f.isFile && FONT_EXTENSIONS.matches(f.name) }
          ?.map { f ->
            FontFileInfo(f.absolutePath, f.lastModified(), f.length()) { f.inputStream() }
          }
          ?: emptyList()
      }
    }
    reindexSource(SOURCE_SYSTEM, files)
  }

  suspend fun indexIsEmpty(): Boolean =
    fontDao.countForSource(SOURCE_USER) == 0 && fontDao.countForSource(SOURCE_SYSTEM) == 0

  suspend fun findFontPaths(family: String): List<String> =
    fontDao.findByName(family).map { it.path }.distinct()

  /**
   * Opens an indexed font file by its stored relative path. Real filesystem
   * first (cheap, exact), documents-provider walk as the fallback for tree
   * URIs that do not map to a direct path.
   */
  suspend fun openIndexedFile(folderUri: String, virtualPath: String): (() -> InputStream?)? {
    val uri = android.net.Uri.parse(folderUri)
    val direct = fileDirFromTreeUri(uri)
      ?.let { dir -> File(dir, virtualPath).takeIf { it.isFile } }
    return direct?.let { file -> { runCatching { file.inputStream() }.getOrNull() } }
      ?: fileManager.fromUri(uri)?.let { root ->
        collectFontFiles(root).firstOrNull { it.path == virtualPath }?.openStream
      }
  }

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
    scanTotal.value = files.size
    scanDone.value = 0
    // One query for the whole source: per-file lookups would dominate scan time.
    val existing = fontDao.metasForSource(source).associateBy(FontMeta::path)
    val seen = mutableSetOf<String>()
    val changed = mutableListOf<String>()
    val inserts = mutableListOf<FontEntity>()
    for (file in files) {
      scanDone.value = scanDone.value + 1
      seen.add(file.path)
      val meta = existing[file.path]
      if (meta != null && meta.lastModified == file.lastModified && meta.size == file.size) continue
      changed.add(file.path)
      parseFamilies(file).forEach { family ->
        inserts.add(FontEntity(file.path, family, file.lastModified, file.size, source))
      }
    }
    // files that vanished from the folder lose their rows
    val gone = existing.keys.filterNot { it in seen }
    if (changed.isNotEmpty() || gone.isNotEmpty()) fontDao.deleteByPaths(changed + gone)
    if (inserts.isNotEmpty()) fontDao.insertAll(inserts)
  }

  private fun parseFamilies(info: FontFileInfo): List<String> {
    val stream = info.openStream() ?: return emptyList()
    return stream.use { input ->
      runCatching {
        val font = TTFFile.open(input)
        val families = font.families.values.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }
        val subfamilies = font.subfamilies.values.filterNotNull().map { it.trim() }
        val preferFamilies = font.preferFamilies.values.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }
        val preferSubfamilies = font.preferSubfamilies.values.filterNotNull().map { it.trim() }
        val fullNames = font.fullNames.values.filterNotNull().map { it.trim() }
        val postscript = font.postscriptNames.values.filterNotNull().map { it.trim() }
        // ASS scripts reference any of: family, preferred family, family+style
        // combinations, full names or postscript names — index them all.
        val combined = buildList {
          (families + preferFamilies).forEach { family ->
            add(family)
            subfamilies.forEach { add("$family $it") }
            preferSubfamilies.forEach { add("$family $it") }
          }
        }
        (families + preferFamilies + fullNames + postscript + combined)
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .distinct()
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
    const val STORAGE_FONTS_DIR = "/storage/emulated/0/Fonts"
    val FONT_EXTENSIONS = Regex(".*\\.(ttf|otf|ttc)\\z", RegexOption.IGNORE_CASE)
  }
}

private fun fileDirFromTreeUri(folderUri: android.net.Uri): File? {
  val segment = folderUri.lastPathSegment?.takeIf { it.contains(':') } ?: return null
  val volume = segment.substringBefore(':')
  val rest = segment.substringAfter(':', "")
  val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
  return File(base, rest).takeIf { it.isDirectory }
}

/** Returns null when the directory tree is unreadable (caller falls back to SAF). */
private fun fastFontFiles(root: File): List<FontIndexer.FontFileInfo>? {
  val out = mutableListOf<FontIndexer.FontFileInfo>()
  val queue = ArrayDeque(listOf(root))
  while (queue.isNotEmpty()) {
    val children = queue.removeFirst().listFiles() ?: return null
    children.forEach { file ->
      if (file.isDirectory) {
        queue.add(file)
      } else if (FontIndexer.FONT_EXTENSIONS.matches(file.name)) {
        out += FontIndexer.FontFileInfo(
          file.toRelativeString(root),
          file.lastModified(),
          file.length(),
        ) { runCatching { file.inputStream() }.getOrNull() }
      }
    }
  }
  return out
}
