package live.mehiz.mpvkt.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.presentation.history.components.HistoryListItem
import live.mehiz.mpvkt.ui.theme.spacing

@Suppress("ModifierNotUsedAtRoot", "ModifierMissing")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  history: List<PlaybackStateEntity>,
  onResume: (PlaybackStateEntity) -> Unit,
  onDelete: (PlaybackStateEntity) -> Unit,
  onClickClearAll: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  val lazyListState = rememberLazyListState()
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(stringResource(R.string.history_title))
        },
        navigationIcon = {
          IconButton(onClick = { onNavigateBack() }) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, null)
          }
        },
        actions = {
          if (history.isNotEmpty()) {
            IconButton(onClick = onClickClearAll) {
              Icon(Icons.Outlined.DeleteSweep, null)
            }
          }
        },
      )
    },
  ) { padding ->
    if (history.isEmpty()) {
      Column(
        modifier = Modifier
          .padding(padding)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = stringResource(id = R.string.history_empty),
          textAlign = TextAlign.Center,
        )
      }
      return@Scaffold
    }

    val layoutDirection = LocalLayoutDirection.current
    HistoryContent(
      history = history,
      lazyListState = lazyListState,
      paddingValues = PaddingValues(
        top = MaterialTheme.spacing.small + padding.calculateTopPadding(),
        start = MaterialTheme.spacing.medium + padding.calculateStartPadding(layoutDirection),
        end = MaterialTheme.spacing.medium + padding.calculateEndPadding(layoutDirection),
        bottom = padding.calculateBottomPadding(),
      ),
      onResume = onResume,
      onDelete = onDelete,
    )
  }
}

@Composable
private fun HistoryContent(
  history: List<PlaybackStateEntity>,
  lazyListState: LazyListState,
  paddingValues: PaddingValues,
  onResume: (PlaybackStateEntity) -> Unit,
  onDelete: (PlaybackStateEntity) -> Unit,
) {
  LazyColumn(
    state = lazyListState,
    contentPadding = paddingValues,
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
  ) {
    itemsIndexed(
      items = history,
      key = { _, item -> item.mediaTitle },
    ) { _, item ->
      HistoryListItem(
        modifier = Modifier.animateItem(),
        item = item,
        onResume = onResume,
        onDelete = onDelete,
      )
    }
  }
}
