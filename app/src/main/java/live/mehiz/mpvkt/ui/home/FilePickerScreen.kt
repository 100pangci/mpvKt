package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.player.PlaybackQueueRequest
import live.mehiz.mpvkt.ui.player.PlaybackQueueStore
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.player.audioExtensions
import live.mehiz.mpvkt.ui.player.imageExtensions
import live.mehiz.mpvkt.ui.player.videoExtensions
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.ui.utils.NaturalOrderComparator
import org.koin.compose.koinInject
import java.io.File
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
  @Suppress("CyclomaticComplexMethod")
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
    val scope = rememberCoroutineScope()
    var preparingPlayback by remember { mutableStateOf(false) }

    fun exitMultiSelect() {
      multiSelectMode = false
      selectedPaths.clear()
    }

    fun requestPlayback(prepare: () -> Intent?) {
      if (preparingPlayback) return
      preparingPlayback = true
      scope.launch {
        try {
          val playbackIntent = withContext(Dispatchers.IO) { prepare() }
          if (playbackIntent != null) {
            exitMultiSelect()
            context.startActivity(playbackIntent)
          } else {
            Toast.makeText(context, R.string.home_playback_failed, Toast.LENGTH_LONG).show()
          }
        } catch (error: CancellationException) {
          throw error
        } catch (_: Exception) {
          Toast.makeText(context, R.string.home_playback_failed, Toast.LENGTH_LONG).show()
        } finally {
          preparingPlayback = false
        }
      }
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
            if (preparingPlayback) CircularProgressIndicator(Modifier.size(24.dp))
            IconButton(onClick = { refreshRevision++ }, enabled = !preparingPlayback) {
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
              val playback = SelectedPlayback(
                paths = selectedPaths.toList(),
                directory = directory,
                fileManager = fileManager,
                autoLoadSubtitles = subtitlesPreferences.autoLoadExternal.get(),
                context = context,
              )
              requestPlayback { prepareSelectedFiles(playback) }
            },
            enabled = !preparingPlayback,
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
        onNavigate = { entry ->
          if (!preparingPlayback) {
            when {
              multiSelectMode -> if (!entry.info.isDirectory) toggleSelection(entry.info.path, selectedPaths)
              !entry.info.isDirectory -> requestPlayback {
                prepareTappedFile(
                  entry.file,
                  directory,
                  fileManager,
                  subtitlesPreferences.autoLoadExternal.get(),
                  context,
                )
              }

              else -> backstack.add(FilePickerScreen(entry.info.path))
            }
          }
        },
        onLongPressFile = { entry ->
          val canStartMultiSelect = !preparingPlayback && !multiSelectMode &&
            !entry.info.isDirectory && entry.info.name.isVideoFile()
          if (canStartMultiSelect) {
            multiSelectMode = true
            selectedPaths.add(entry.info.path)
          }
        },
        isSelectedFile = { path ->
          multiSelectMode && path in selectedPaths
        },
        refreshRevision = refreshRevision,
        onListingUpdate = { entries ->
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
    path: String,
    selectedPaths: SnapshotStateList<String>,
  ) {
    if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
  }

  private fun prepareTappedFile(
    file: AbstractFile,
    directory: AbstractFile,
    fileManager: FileManager,
    autoLoadSubtitles: Boolean,
    context: Context,
  ): Intent? {
    val path = file.getFullPath()
    val directoryFiles = fileManager.listFiles(directory)
    val actualFile = directoryFiles.firstOrNull { it.getFullPath() == path && fileManager.isFile(it) }
    return actualFile?.let { selectedFile ->
      val subtitleIndex = if (autoLoadSubtitles) buildSubtitleIndex(directoryFiles, fileManager) else null
      if (fileManager.getName(selectedFile).isVideoFile()) {
        // Opening one video queues every video of the directory in natural
        // order and starts at the tapped one.
        val videoFiles = directoryFiles
          .filter { fileManager.isFile(it) && fileManager.getName(it).isVideoFile() }
        val queue = videoFiles.map { it.getFullPath() }
          .sortedWith(NaturalOrderComparator)
        val queueSubtitles = videoFiles.associate { video ->
          video.getFullPath() to subtitleIndex?.forVideoName(fileManager.getName(video)).orEmpty()
        }.filterValues { it.isNotEmpty() }
        prepareQueueIntent(path, queue, context, queueSubtitles)
      } else {
        val subtitles = subtitleIndex?.forVideoName(fileManager.getName(selectedFile)).orEmpty()
        prepareQueueIntent(
          path,
          listOf(path),
          context,
          if (subtitles.isEmpty()) emptyMap() else mapOf(path to subtitles),
        )
      }
    }
  }

  @Composable
  private fun FilePicker(
    directory: AbstractFile,
    onNavigate: (PickerFileEntry) -> Unit,
    modifier: Modifier = Modifier,
    onLongPressFile: (PickerFileEntry) -> Unit = {},
    isSelectedFile: (String) -> Boolean = { false },
    refreshRevision: Int = 0,
    onListingUpdate: (List<PickerEntryInfo>) -> Unit = {},
  ) {
    val navigator = LocalBackStack.current
    val fileManager = koinInject<FileManager>()
    val directoryPath = directory.getFullPath()
    val notifyListingUpdate by rememberUpdatedState(onListingUpdate)
    val listing by produceState<PickerDirectoryListing?>(null, directoryPath, fileManager, refreshRevision) {
      value = null // Don't leave deleted rows clickable while the new snapshot loads.
      val fresh = withContext(Dispatchers.IO) { loadPickerDirectoryListing(fileManager, directory) }
      notifyListingUpdate(fresh.entries.map { it.info })
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
    Box(modifier, contentAlignment = Alignment.Center) {
      when {
        loadedListing == null -> CircularProgressIndicator()
        loadedListing.unavailable -> Text(stringResource(R.string.home_directory_unavailable))
        else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
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
              onClick = { onNavigate(entry) },
              onLongClick = { onLongPressFile(entry) },
              previewSource = info.path,
              loadPreview = loadPreviews,
            )
          }
        }
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
      } else {
        null
      }
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
  enabled: Boolean = true,
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
      TextButton(onClick = onCancel, enabled = enabled) {
        Text(stringResource(R.string.generic_cancel))
      }
      Button(
        onClick = onPlay,
        enabled = enabled && selectedCount > 0,
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

private fun prepareSelectedFiles(
  playback: SelectedPlayback,
): Intent? {
  val directoryFiles = playback.fileManager.listFiles(playback.directory)
  val filesByPath = directoryFiles.associateBy { it.getFullPath() }
  // Revalidate when playing too: a file can disappear after the last refresh.
  val availablePaths = playback.paths.filter { path ->
    filesByPath[path]?.let { playback.fileManager.isFile(it) } == true
  }
  if (availablePaths.isEmpty()) {
    return null
  }
  val selectedFiles = availablePaths.mapNotNull(filesByPath::get)
  val queueSubtitles = if (playback.autoLoadSubtitles) {
    val index = buildSubtitleIndex(directoryFiles, playback.fileManager)
    selectedFiles.associate { file -> file.getFullPath() to index.forVideoName(playback.fileManager.getName(file)) }
      .filterValues { it.isNotEmpty() }
  } else {
    emptyMap()
  }
  return prepareQueueIntent(availablePaths.first(), availablePaths, playback.context, queueSubtitles)
}

private fun buildSubtitleIndex(
  directoryFiles: List<AbstractFile>,
  fileManager: FileManager,
): SiblingSubtitleIndex = SiblingSubtitleIndex(
  directoryFiles.filter { fileManager.isFile(it) }.map { fileManager.getName(it) to it.getFullPath() },
)

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

/**
 * Launches the player with an explicit queue: [startPath] is the entry to
 * play (also the intent data, so sibling subtitle/font resolution works),
 * [queue] holds every entry in playback order.
 */
private fun prepareQueueIntent(
  startPath: String,
  queue: List<String>,
  context: Context,
  queueSubtitles: Map<String, List<String>> = emptyMap(),
): Intent {
  val i = Intent(Intent.ACTION_VIEW, startPath.toUri())
  i.setClass(context, PlayerActivity::class.java)
  val store = PlaybackQueueStore(File(context.filesDir, PlaybackQueueStore.DIRECTORY))
  i.putExtra(PlayerActivity.QUEUE_REQUEST_EXTRA, store.save(PlaybackQueueRequest(queue, queueSubtitles)))
  return i
}

private fun String.isVideoFile(): Boolean = substringAfterLast('.').lowercase() in videoExtensions
