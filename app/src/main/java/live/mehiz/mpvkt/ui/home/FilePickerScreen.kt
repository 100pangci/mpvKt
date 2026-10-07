package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.player.audioExtensions
import live.mehiz.mpvkt.ui.player.imageExtensions
import live.mehiz.mpvkt.ui.player.videoExtensions
import live.mehiz.mpvkt.ui.theme.spacing
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
    var refreshRevision by remember(uri) { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
      // Files can be added/deleted by another app while the picker is in the background.
      refreshRevision++
    }
    // Navigation state can outlive a SAF grant or the directory itself.
    // A restored screen must not assume its URI is still accessible.
    var resolvingDirectory by remember(uri) { mutableStateOf(true) }
    val resolvedDirectory by produceState<AbstractFile?>(null, uri, fileManager, refreshRevision) {
      value = withContext(Dispatchers.IO) { fileManager.resolvePickerDirectory(uri.toUri()) }
      resolvingDirectory = false
    }
    val directory = resolvedDirectory
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
          actions = {
            IconButton(onClick = { refreshRevision++ }) {
              Icon(Icons.Default.Refresh, stringResource(R.string.home_refresh_directory))
            }
          },
        )
      },
      bottomBar = {
        if (multiSelectMode && directory != null) {
          MultiSelectBottomBar(
            selectedCount = selectedPaths.size,
            onCancel = ::exitMultiSelect,
            onPlay = {
              playSelectedFiles(
                playback = SelectedPlayback(
                  paths = selectedPaths.toList(),
                  directory = directory,
                  fileManager = fileManager,
                  autoLoadSubtitles = subtitlesPreferences.autoLoadExternal.get(),
                  context = context,
                ),
                onFinished = ::exitMultiSelect,
              )
            },
          )
        }
      },
    ) { paddingValues ->
      if (resolvingDirectory) {
        Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
        return@Scaffold
      }
      if (directory == null) {
        Column(
          modifier = Modifier.fillMaxSize().padding(paddingValues).padding(MaterialTheme.spacing.medium),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(stringResource(R.string.home_directory_unavailable))
          TextButton(onClick = { backstack.removeAll { it is FilePickerScreen } }) {
            Text(stringResource(R.string.home_return_home))
          }
        }
        return@Scaffold
      }
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
        isSelectedFile = { path ->
          multiSelectMode && path in selectedPaths
        },
        refreshRevision = refreshRevision,
        onListingLoaded = { entries ->
          val retained = retainPickerSelection(selectedPaths, entries)
          if (retained != selectedPaths) {
            selectedPaths.clear()
            selectedPaths.addAll(retained)
          }
          if (selectedPaths.isEmpty()) multiSelectMode = false
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
      val directoryFiles = fileManager.listFiles(directory)
      val videoFiles = directoryFiles
        .filter { fileManager.isFile(it) && fileManager.getName(it).isVideoFile() }
      val queue = videoFiles.map { it.getFullPath() }
        .sortedWith(NaturalOrderComparator)
      val subtitlePaths = if (autoLoadSubtitles) {
        collectSiblingSubtitles(file, directoryFiles, fileManager)
      } else {
        emptyList()
      }
      val queueSubtitles = if (autoLoadSubtitles) {
        buildQueueSubtitlePaths(videoFiles, directoryFiles, fileManager)
      } else {
        null
      }
      playFileFromQueue(path, queue, subtitlePaths, context, queueSubtitles)
      return
    }
    if (autoLoadSubtitles) {
      playFileWithSubtitles(
        path,
        collectSiblingSubtitles(file, fileManager.listFiles(directory), fileManager),
        context,
      )
    } else {
      HomeScreen.playFile(path, context)
    }
  }

  @Composable
  private fun FilePicker(
    directory: AbstractFile,
    onNavigate: (AbstractFile) -> Unit,
    modifier: Modifier = Modifier,
    onLongPressFile: (AbstractFile) -> Unit = {},
    isSelectedFile: (String) -> Boolean = { false },
    refreshRevision: Int = 0,
    onListingLoaded: (List<PickerEntryInfo>) -> Unit = {},
  ) {
    val navigator = LocalBackStack.current
    val fileManager = koinInject<FileManager>()
    val directoryPath = directory.getFullPath()
    val notifyListingLoaded by rememberUpdatedState(onListingLoaded)
    val listing by produceState<PickerDirectoryListing?>(null, directoryPath, fileManager, refreshRevision) {
      value = null // Don't leave deleted rows clickable while the new snapshot loads.
      val fresh = withContext(Dispatchers.IO) { loadPickerDirectoryListing(fileManager, directory) }
      notifyListingLoaded(fresh.entries.map { it.info })
      value = fresh
    }
    val listState = rememberLazyListState()
    var loadPreviews by remember { mutableStateOf(false) }
    val directoryItemCounts = remember(directoryPath, refreshRevision) { mutableStateMapOf<String, Int>() }
    LaunchedEffect(listState) {
      snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
        loadPreviews = false
        if (!scrolling) {
          delay(180) // Ignore brief gaps between drag/fling and rapidly changing visible rows.
          loadPreviews = true
        }
      }
    }
    val loadedListing = listing
    if (loadedListing == null) {
      Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      return
    }
    if (loadedListing.unavailable) {
      Box(modifier, contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.home_directory_unavailable))
      }
      return
    }
    LazyColumn(modifier, state = listState) {
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
      itemsIndexed(
        loadedListing.entries,
        key = { _, entry -> entry.info.path },
        contentType = { _, entry -> entry.info.isDirectory },
      ) { index, entry ->
        val info = entry.info
        val file = entry.file
        // Child counts are loaded only for visible, settled directory rows, once per screen.
        LaunchedEffect(info.path, loadPreviews, refreshRevision) {
          if (info.isDirectory && loadPreviews && info.path !in directoryItemCounts) {
            val count = withContext(Dispatchers.IO) {
              runCatching { fileManager.listFiles(file).size }.getOrNull()
            }
            count?.let { directoryItemCounts[info.path] = it }
          }
        }
        FileListing(
          name = info.name,
          isDirectory = info.isDirectory,
          lastModified = info.lastModified,
          length = info.length,
          modifier = Modifier.background(
            when {
              isSelectedFile(info.path) -> MaterialTheme.colorScheme.primaryContainer
              index % 2 == 1 -> MaterialTheme.colorScheme.surfaceContainerLow
              else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
          ),
          items = directoryItemCounts[info.path],
          onClick = { onNavigate(file) },
          onLongClick = { onLongPressFile(file) },
          previewSource = info.path,
          loadPreview = loadPreviews,
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
    previewSource: String? = null,
    loadPreview: Boolean = true,
  ) {
    val size = remember(isDirectory, length) {
      if (isDirectory) null else length?.asHumanReadableByteCountBin()
    }
    val time = remember(lastModified) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        lastModified?.let {
          Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss"))
        }
      } else null
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
      if (!isDirectory && name.isVideoFile() && previewSource != null) {
        VideoFilePreview(
          source = previewSource,
          lastModified = lastModified,
          length = length,
          enabled = loadPreview,
        )
      } else {
        Icon(
          imageVector = fileIcon(isDirectory = isDirectory, fileExtension = name.substringAfterLast('.')),
          contentDescription = null,
        )
      }
      Column(Modifier.weight(1f)) {
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

@Composable
private fun MultiSelectBottomBar(
  selectedCount: Int,
  onCancel: () -> Unit,
  onPlay: () -> Unit,
) {
  Surface(tonalElevation = 3.dp) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.smaller),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onCancel) {
        Text(stringResource(R.string.generic_cancel))
      }
      Button(
        onClick = onPlay,
        enabled = selectedCount > 0,
      ) {
        Text(stringResource(R.string.home_play_selected, selectedCount))
      }
    }
  }
}

private data class SelectedPlayback(
  val paths: List<String>,
  val directory: AbstractFile,
  val fileManager: FileManager,
  val autoLoadSubtitles: Boolean,
  val context: Context,
)

private fun playSelectedFiles(
  playback: SelectedPlayback,
  onFinished: () -> Unit,
) {
  val directoryFiles = playback.fileManager.listFiles(playback.directory)
  val filesByPath = directoryFiles.associateBy { it.getFullPath() }
  // Revalidate when playing too: a file can disappear after the last refresh.
  val availablePaths = playback.paths.filter { path ->
    filesByPath[path]?.let { playback.fileManager.isFile(it) } == true
  }
  if (availablePaths.isEmpty()) {
    onFinished()
    return
  }
  val selectedFiles = availablePaths.mapNotNull(filesByPath::get)
  val (subtitlePaths, queueSubtitles) = if (playback.autoLoadSubtitles) {
    val subtitles = selectedFiles.firstOrNull()?.let {
      collectSiblingSubtitles(it, directoryFiles, playback.fileManager)
    }.orEmpty()
    subtitles to buildQueueSubtitlePaths(selectedFiles, directoryFiles, playback.fileManager)
  } else {
    emptyList<String>() to null
  }
  onFinished()
  playFileFromQueue(availablePaths.first(), availablePaths, subtitlePaths, playback.context, queueSubtitles)
}

private fun collectSiblingSubtitles(
  video: AbstractFile,
  directoryFiles: List<AbstractFile>,
  fileManager: FileManager,
): List<String> {
  val videoNameWithoutExt = fileManager.getName(video).substringBeforeLast(".")
  val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt", "sub")
  return directoryFiles.filter { potentialSubFile ->
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

private fun buildQueueSubtitlePaths(
  videoFiles: List<AbstractFile>,
  directoryFiles: List<AbstractFile>,
  fileManager: FileManager,
): Bundle = Bundle().apply {
  videoFiles.forEach { video ->
    val subtitles = collectSiblingSubtitles(video, directoryFiles, fileManager)
    if (subtitles.isNotEmpty()) {
      putStringArrayList(video.getFullPath(), ArrayList(subtitles))
    }
  }
}

@Composable
private fun fileIcon(
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

private fun playFileWithSubtitles(
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
private fun playFileFromQueue(
  startPath: String,
  queue: List<String>,
  subtitlePaths: List<String>,
  context: Context,
  queueSubtitles: Bundle? = null,
) {
  val i = Intent(Intent.ACTION_VIEW, startPath.toUri())
  i.setClass(context, PlayerActivity::class.java)
  if (queue.size > 1) {
    i.putExtra(PlayerActivity.QUEUE_EXTRA, ArrayList(queue))
    queueSubtitles?.let { i.putExtra(PlayerActivity.QUEUE_SUBTITLES_EXTRA, it) }
  }
  if (subtitlePaths.isNotEmpty()) {
    val subtitleUris = subtitlePaths.map { it.toUri() }.toTypedArray()
    i.putExtra("subs", subtitleUris)
    i.putExtra("subs.enable", arrayOf(subtitleUris.first()))
  }
  context.startActivity(i)
}

private fun String.isVideoFile(): Boolean = substringAfterLast('.').lowercase() in videoExtensions
