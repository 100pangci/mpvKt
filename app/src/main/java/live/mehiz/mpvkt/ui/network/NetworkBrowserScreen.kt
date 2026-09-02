package live.mehiz.mpvkt.ui.network

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.network.NetworkSource
import live.mehiz.mpvkt.network.RemoteClientFactory
import live.mehiz.mpvkt.network.RemoteEntry
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.player.videoExtensions
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.ui.utils.NaturalOrderComparator
import org.koin.compose.koinInject

@Serializable
data class NetworkBrowserScreen(
  val source: NetworkSource,
  val path: String = "",
) : Screen {

  @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val factory = koinInject<RemoteClientFactory>()
    val json = koinInject<Json>()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var retryKey by remember { mutableIntStateOf(0) }
    var multiSelectMode by remember { mutableStateOf(false) }
    // Selected file names in tap order: the queue must follow the user's
    // pick order, not the directory listing.
    val selectedNames = remember { mutableStateListOf<String>() }

    fun exitMultiSelect() {
      multiSelectMode = false
      selectedNames.clear()
    }

    LaunchedEffect(source, path, retryKey) {
      loading = true
      error = false
      withContext(Dispatchers.IO) {
        runCatching { factory.create(source).use { it.list(path) } }
      }.onSuccess {
        entries = it
        loading = false
      }.onFailure {
        entries = emptyList()
        error = true
        loading = false
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = if (multiSelectMode) {
                  pluralStringResource(R.plurals.plural_items, selectedNames.size, selectedNames.size)
                } else {
                  source.name
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "/${path.trimStart('/')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
          navigationIcon = {
            IconButton(
              onClick = {
                if (multiSelectMode) exitMultiSelect() else backstack.removeLastOrNull()
              },
            ) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, null)
            }
          },
        )
      },
      bottomBar = {
        if (multiSelectMode) {
          MultiSelectBar(
            selectedCount = selectedNames.size,
            onCancel = ::exitMultiSelect,
            onPlay = {
              // enabled guards the empty case, first() is always valid.
              val names = selectedNames.toList()
              exitMultiSelect()
              playSelectedFiles(names, factory, json, context)
            },
          )
        }
      },
    ) { padding ->
      when {
        loading -> Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        error -> Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(MaterialTheme.spacing.large),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(stringResource(R.string.network_browse_error))
          Button(
            onClick = { retryKey++ },
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
          ) {
            Text(stringResource(R.string.network_retry))
          }
        }

        else -> {
          val sorted = remember(entries) {
            entries.sortedWith(
              compareBy<RemoteEntry> { !it.isDirectory }.thenBy(NaturalOrderComparator) { it.name },
            )
          }
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
          ) {
            if (path.isNotEmpty()) {
              item {
                DirectoryRow(name = "..", onClick = { backstack.removeLastOrNull() })
              }
            }
            items(sorted, key = { "${it.isDirectory}-${it.name}" }) { entry ->
              val childPath = NetworkSource.joinPath(path, entry.name)
              val selected = entry.name in selectedNames
              RemoteEntryRow(
                entry = entry,
                isSelected = multiSelectMode && selected,
                onOpen = {
                  when {
                    multiSelectMode && !entry.isDirectory ->
                      toggleSelection(entry, selected, selectedNames)

                    entry.isDirectory -> backstack.add(NetworkBrowserScreen(source, childPath))
                    entry.name.isVideoFile() -> playRemoteFile(entry, factory, json, context)
                  }
                },
                onLongClick = {
                  if (!multiSelectMode && entry.name.isVideoFile()) {
                    multiSelectMode = true
                    selectedNames.add(entry.name)
                  }
                },
              )
            }
          }
        }
      }
    }
  }

  private fun toggleSelection(
    entry: RemoteEntry,
    selected: Boolean,
    selectedNames: SnapshotStateList<String>,
  ) {
    if (selected) selectedNames.remove(entry.name) else selectedNames.add(entry.name)
  }

  private fun playRemoteFile(
    entry: RemoteEntry,
    factory: RemoteClientFactory,
    json: Json,
    context: Context,
  ) {
    val fileUrl = fileUrlFor(entry.name, factory)
    val i = Intent(Intent.ACTION_VIEW, fileUrl.toUri())
    i.setClass(context, PlayerActivity::class.java)
    i.putExtra(PlayerActivity.REMOTE_SOURCE_EXTRA, json.encodeToString(source))
    i.putExtra(PlayerActivity.REMOTE_PLAY_PATH_EXTRA, path)
    context.startActivity(i)
  }

  /**
   * Plays the picked files as an explicit mpv queue (native playlist), in
   * tap order and starting at the first pick. The remote source and the
   * browsed directory ride along so per-episode fonts/ staging keeps
   * working when mpv advances through the queue by itself.
   */
  private fun playSelectedFiles(
    names: List<String>,
    factory: RemoteClientFactory,
    json: Json,
    context: Context,
  ) {
    val urls = names.map { fileUrlFor(it, factory) }
    val i = Intent(Intent.ACTION_VIEW, urls.first().toUri())
    i.setClass(context, PlayerActivity::class.java)
    if (urls.size > 1) {
      i.putExtra(PlayerActivity.QUEUE_EXTRA, ArrayList(urls))
    }
    i.putExtra(PlayerActivity.REMOTE_SOURCE_EXTRA, json.encodeToString(source))
    i.putExtra(PlayerActivity.REMOTE_PLAY_PATH_EXTRA, path)
    context.startActivity(i)
  }

  private fun fileUrlFor(name: String, factory: RemoteClientFactory): String =
    factory.create(source).fileUrl(NetworkSource.joinPath(path, name))
}

@Composable
private fun MultiSelectBar(
  selectedCount: Int,
  onCancel: () -> Unit,
  onPlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier, tonalElevation = 3.dp) {
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
      Button(onClick = onPlay, enabled = selectedCount > 0) {
        Text(stringResource(R.string.home_play_selected, selectedCount))
      }
    }
  }
}

@Composable
private fun DirectoryRow(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .heightIn(min = 64.dp)
      .padding(vertical = MaterialTheme.spacing.smaller, horizontal = MaterialTheme.spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.Folder, contentDescription = null)
    Text(name)
  }
}

@Composable
private fun RemoteEntryRow(
  entry: RemoteEntry,
  isSelected: Boolean,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = onOpen,
        onLongClick = onLongClick,
      )
      .background(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified,
      )
      .heightIn(min = 64.dp)
      .padding(vertical = MaterialTheme.spacing.smaller, horizontal = MaterialTheme.spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      when {
        entry.isDirectory -> Icons.Filled.Folder
        entry.name.isVideoFile() -> Icons.Filled.Movie
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
      },
      contentDescription = null,
    )
    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

private fun String.isVideoFile(): Boolean = substringAfterLast('.').lowercase() in videoExtensions
