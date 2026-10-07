package live.mehiz.mpvkt.ui.home

import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class PickerEntryInfo(
  val path: String,
  val name: String,
  val isDirectory: Boolean,
  val lastModified: Long?,
  val length: Long?,
)

internal val pickerEntryOrder: Comparator<PickerEntryInfo> =
  compareBy<PickerEntryInfo> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

internal fun isVisiblePickerEntry(name: String, isDirectory: Boolean): Boolean =
  !name.startsWith('.') && (isDirectory || name.substringAfterLast('.').lowercase() in Utils.MEDIA_EXTENSIONS)

internal data class PickerFileEntry(val file: AbstractFile, val info: PickerEntryInfo)
internal data class PickerDirectoryListing(val entries: List<PickerFileEntry>, val unavailable: Boolean = false)

internal fun retainPickerSelection(selectedPaths: List<String>, entries: List<PickerEntryInfo>): List<String> {
  val availablePaths = entries.filterNot { it.isDirectory }.mapTo(HashSet()) { it.path }
  return selectedPaths.filter { it in availablePaths }
}

// All provider calls and sorting happen on IO, never during composition or scrolling.
internal suspend fun loadPickerDirectoryListing(
  fileManager: FileManager,
  directory: AbstractFile,
): PickerDirectoryListing = try {
  val entries = fileManager.listFiles(directory).mapNotNull { file ->
    coroutineContext.ensureActive()
    val name = fileManager.getName(file)
    val isDirectory = fileManager.isDirectory(file)
    if (!isVisiblePickerEntry(name, isDirectory)) return@mapNotNull null
    PickerFileEntry(
      file,
      PickerEntryInfo(
        path = file.getFullPath(),
        name = name,
        isDirectory = isDirectory,
        lastModified = fileManager.lastModified(file),
        length = if (isDirectory) null else fileManager.getLength(file),
      ),
    )
  }.sortedWith { left, right -> pickerEntryOrder.compare(left.info, right.info) }
  PickerDirectoryListing(entries)
} catch (_: SecurityException) {
  PickerDirectoryListing(emptyList(), unavailable = true)
} catch (_: IllegalArgumentException) {
  PickerDirectoryListing(emptyList(), unavailable = true)
}
