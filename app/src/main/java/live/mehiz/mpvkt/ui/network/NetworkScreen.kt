package live.mehiz.mpvkt.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.network.NetworkSource
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.ConfirmDialog
import live.mehiz.mpvkt.presentation.network.NetworkSourceDialog
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object NetworkScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val viewModel = koinViewModel<NetworkViewModel>()
    val sources by viewModel.sources.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NetworkSource?>(null) }
    var pendingEdit by remember { mutableStateOf<NetworkSource?>(null) }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.home_network_sources)) },
          navigationIcon = {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, null)
            }
          },
        )
      },
      floatingActionButton = {
        ExtendedFloatingActionButton(
          onClick = { showAddDialog = true },
          icon = { Icon(Icons.Filled.Add, null) },
          text = { Text(stringResource(R.string.network_add_source)) },
        )
      },
    ) { padding ->
      if (sources.isEmpty()) {
        Column(
          modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = stringResource(R.string.network_empty),
            textAlign = TextAlign.Center,
          )
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(
            top = MaterialTheme.spacing.small + padding.calculateTopPadding(),
            start = MaterialTheme.spacing.medium,
            end = MaterialTheme.spacing.medium,
            bottom = MaterialTheme.spacing.larger + padding.calculateBottomPadding(),
          ),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
          items(sources, key = { it.id }) { source ->
            NetworkSourceListItem(
              source = source,
              onOpen = { backstack.add(NetworkBrowserScreen(source)) },
              onEdit = { pendingEdit = source },
              onDelete = { pendingDelete = source },
            )
          }
        }
      }
    }

    if (showAddDialog) {
      NetworkSourceDialog(
        title = stringResource(R.string.network_add_source),
        initial = null,
        onConfirm = { draft ->
          viewModel.addSource(draft)
          showAddDialog = false
        },
        onDismissRequest = { showAddDialog = false },
      )
    }
    pendingEdit?.let { source ->
      NetworkSourceDialog(
        title = stringResource(R.string.network_edit_source),
        initial = source,
        onConfirm = { draft ->
          viewModel.updateSource(source, draft)
          pendingEdit = null
        },
        onDismissRequest = { pendingEdit = null },
      )
    }
    pendingDelete?.let { source ->
      ConfirmDialog(
        title = stringResource(R.string.network_delete_confirm),
        subtitle = source.name,
        onConfirm = {
          viewModel.removeSource(source.id)
          pendingDelete = null
        },
        onCancel = { pendingDelete = null },
      )
    }
  }
}

@Composable
private fun NetworkSourceListItem(
  source: NetworkSource,
  onOpen: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Filled.Dns, contentDescription = null)
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = source.name,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${source.type.name.lowercase()}://${source.host}:${source.port}${source.basePath}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onEdit) {
        Icon(Icons.Outlined.Edit, contentDescription = null)
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Outlined.Delete, contentDescription = null)
      }
    }
  }
}
