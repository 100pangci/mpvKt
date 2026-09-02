package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import `is`.xyz.mpv.Utils
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.player.audioExtensions
import live.mehiz.mpvkt.ui.player.imageExtensions
import live.mehiz.mpvkt.ui.player.videoExtensions
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.FilesComparator
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.ui.utils.NaturalOrderComparator
import org.koin.compose.koinInject
import java.lang.Long.signum
import java.text.StringCharacterIterator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Serializable
data class FilePickerScreen(val uri: String) : Screen {

  @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val fileManager = koinInject<FileManager>()
    val context = LocalContext.current
    val subtitlesPreferences = koinInject<SubtitlesPreferences>()
    var multiSelectMode by remember { mutableStateOf(false) }
    // Selected file paths in tap order: the queue must follow the user's
    // pick order, not the directory listing.
    val selectedPaths = remember { mutableStateListOf<String>() }

    fun exitMultiSelect() {
      multiSelectMode = false
      selectedPaths.clear()
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = if (multiSelectMode) {
                pluralStringResource(R.plurals.plural_items, selectedPaths.size, selectedPaths.size)
              } else {
                stringResource(id = R.string.home_pick_file)
              },
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                if (multiSelectMode) exitMultiSelect() else backstack.removeAll { it is FilePickerScreen }
              },
            ) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, null)
            }
          },
        )
      },
      bottomBar = {
        if (multiSelectMode) {
          Surface(tonalElevation = 3.dp) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.smaller),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              TextButton(onClick = ::exitMultiSelect) {
                Text(stringResource(R.string.generic_cancel))
              }
              Button(
                onClick = {
                  // enabled guards the empty case, first() is always valid.
                  val paths = selectedPaths.toList()
                  exitMultiSelect()
                  playFileFromQueue(paths.first(), paths, emptyList(), context)
                },
                enabled = selectedPaths.isNotEmpty(),
              ) {
                Text(stringResource(R.string.home_play_selected, selectedPaths.size))
              }
            }
          }
        }
      },
    ) { paddingValues ->
      val directory = fileManager.fromUri(uri.toUri())!!
      FilePicker(
        directory = directory,
        onNavigate = { newFile ->
          when {
            multiSelectMode -> toggleSelection(newFile, fileManager, selectedPaths)
            fileManager.isFile(newFile) -> playTappedFile(
              newFile,
              directory,
              fileManager,
              subtitlesPreferences.autoLoadExternal.get(),
              context,
            )

            else -> backstack.add(FilePickerScreen(newFile.getFullPath()))
          }
        },
        onLongPressFile = { file ->
          if (!multiSelectMode && fileManager.isFile(file) && fileManager.getName(file).isVideoFile()) {
            multiSelectMode = true
            selectedPaths.add(file.getFullPath())
          }
        },
        isSelectedFile = { file ->
          multiSelectMode && fileManager.isFile(file) && file.getFullPath() in selectedPaths
        },
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
      )
    }
  }

  private fun toggleSelection(
    file: AbstractFile,
    fileManager: FileManager,
    selectedPaths: SnapshotStateList<String>,
  ) {
    if (!fileManager.isFile(file)) return
    val path = file.getFullPath()
    if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
  }

  private fun playTappedFile(
    file: AbstractFile,
    directory: AbstractFile,
    fileManager: FileManager,
    autoLoadSubtitles: Boolean,
    context: Context,
  ) {
    val path = file.getFullPath()
    if (fileManager.getName(file).isVideoFile()) {
      // Opening one video queues every video of the directory in natural
      // order and starts at the tapped one.
      val queue = fileManager.listFiles(directory)
        .filter { fileManager.isFile(it) && fileManager.getName(it).isVideoFile() }
        .map { it.getFullPath() }
        .sortedWith(NaturalOrderComparator)
      val subtitlePaths = if (autoLoadSubtitles) {
        collectSiblingSubtitles(file, directory, fileManager)
      } else {
        emptyList()
      }
      playFileFromQueue(path, queue, subtitlePaths, context)
      return
    }
    if (autoLoadSubtitles) {
      playFileWithSubtitles(path, collectSiblingSubtitles(file, directory, fileManager), context)
    } else {
      HomeScreen.playFile(path, context)
    }
  }

  private fun collectSiblingSubtitles(
    video: AbstractFile,
    directory: AbstractFile,
    fileManager: FileManager,
  ): List<String> {
    val videoNameWithoutExt = fileManager.getName(video).substringBeforeLast(".")
    val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt", "sub")
    return fileManager.listFiles(directory).filter { potentialSubFile ->
      if (fileManager.isDirectory(potentialSubFile)) {
        false
      } else {
        val subFileName = fileManager.getName(potentialSubFile)
        val subFileNameWithoutExt = subFileName.substringBeforeLast('.')
        val subFileExt = subFileName.substringAfterLast('.').lowercase()
        // Matching rule: File names have the same prefix and the extension is a subtitle format
        subFileNameWithoutExt.startsWith(videoNameWithoutExt) && subFileExt in subtitleExtensions
      }
    }.map { it.getFullPath() }
  }

  @Composable
  fun FilePicker(
    directory: AbstractFile,
    onNavigate: (AbstractFile) -> Unit,
    modifier: Modifier = Modifier,
    onLongPressFile: (AbstractFile) -> Unit = {},
    isSelectedFile: (AbstractFile) -> Boolean = { false },
  ) {
    val navigator = LocalBackStack.current
    val fileManager = koinInject<FileManager>()
    val fileList = fileManager.listFiles(directory).filterNot {
      !Utils.MEDIA_EXTENSIONS.contains(fileManager.getName(it).substringAfterLast('.')) &&
        fileManager.isFile(it) || fileManager.getName(it).startsWith('.')
    }.sortedWith(FilesComparator(fileManager))

    LazyColumn(modifier) {
      item {
        FileListing(
          name = "..",
          isDirectory = true,
          lastModified = null,
          length = 0L,
          onClick = { navigator.removeLastOrNull() },
          modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        )
      }
      itemsIndexed(fileList, key = { _, file -> fileManager.getName(file) }) { index, file ->
        FileListing(
          name = fileManager.getName(file),
          isDirectory = fileManager.isDirectory(file),
          lastModified = fileManager.lastModified(file),
          length = if (fileManager.isFile(file)) fileManager.getLength(file) else null,
          modifier = Modifier.background(
            when {
              isSelectedFile(file) -> MaterialTheme.colorScheme.primaryContainer
              index % 2 == 1 -> MaterialTheme.colorScheme.surfaceContainerLow
              else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
          ),
          items = if (fileManager.isDirectory(file)) fileManager.listFiles(file).size else null,
          onClick = { onNavigate(file) },
          onLongClick = { onLongPressFile(file) },
        )
      }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  fun FileListing(
    name: String,
    isDirectory: Boolean,
    lastModified: Long?,
    length: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    items: Int? = null,
    onLongClick: (() -> Unit)? = null,
  ) {
    var size: String? by remember { mutableStateOf(null) }
    var time: String? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        lastModified?.let {
          time = Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss"))
        }
      }
      if (isDirectory) return@LaunchedEffect
      length?.let { size = it.asHumanReadableByteCountBin() }
    }
    Row(
      modifier = modifier
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        )
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .padding(vertical = MaterialTheme.spacing.smaller, horizontal = MaterialTheme.spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = fileIcon(isDirectory = isDirectory, fileExtension = name.substringAfterLast('.')),
        contentDescription = null,
      )
      Column {
        Text(
          text = name,
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.bodyLarge,
        )
        if (isDirectory && lastModified == null) return@Column
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = time ?: "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
          )
          if (size != null || items != null) {
            Text(
              text = if (isDirectory) {
                pluralStringResource(
                  id = R.plurals.plural_items,
                  count = items!!,
                  items,
                )
              } else {
                size!!
              },
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }
  }

  @Composable
  fun fileIcon(
    isDirectory: Boolean,
    fileExtension: String,
  ): ImageVector {
    if (isDirectory) return Icons.Filled.Folder
    return when (fileExtension) {
      in videoExtensions -> Icons.Filled.Movie
      in audioExtensions -> Icons.Filled.Audiotrack
      in imageExtensions -> Icons.Filled.Image
      else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
  }

  fun playFileWithSubtitles(
    filepath: String,
    subtitlePaths: List<String>,
    context: Context,
  ) {
    val i = Intent(Intent.ACTION_VIEW, filepath.toUri())
    i.setClass(context, PlayerActivity::class.java)
    if (subtitlePaths.isNotEmpty()) {
      val subtitleUris = subtitlePaths.map { it.toUri() }.toTypedArray()
      i.putExtra("subs", subtitleUris)
      i.putExtra("subs.enable", arrayOf(subtitleUris.first()))
    }
    context.startActivity(i)
  }

  /**
   * Launches the player with an explicit queue: [startPath] is the entry to
   * play (also the intent data, so sibling subtitle/font resolution works),
   * [queue] holds every entry in playback order.
   */
  fun playFileFromQueue(
    startPath: String,
    queue: List<String>,
    subtitlePaths: List<String>,
    context: Context,
  ) {
    val i = Intent(Intent.ACTION_VIEW, startPath.toUri())
    i.setClass(context, PlayerActivity::class.java)
    if (queue.size > 1) {
      i.putExtra("queue", ArrayList(queue))
    }
    if (subtitlePaths.isNotEmpty()) {
      val subtitleUris = subtitlePaths.map { it.toUri() }.toTypedArray()
      i.putExtra("subs", subtitleUris)
      i.putExtra("subs.enable", arrayOf(subtitleUris.first()))
    }
    context.startActivity(i)
  }

  private fun Long.asHumanReadableByteCountBin(): String {
    val absB = if (this == Long.MIN_VALUE) Long.MAX_VALUE else abs(this)
    if (absB < 1024) return "$this B"
    var value = absB
    val units = StringCharacterIterator("KMGTPE")
    var i = 40
    while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
      value = value shr 10
      units.next()
      i -= 10
    }
    value *= signum(this)
    return String.format(
      locale = java.util.Locale.US,
      format = "%.1f %ciB",
      value / 1024.0,
      units.current(),
    )
  }
}

private fun String.isVideoFile(): Boolean = substringAfterLast('.').lowercase() in videoExtensions
