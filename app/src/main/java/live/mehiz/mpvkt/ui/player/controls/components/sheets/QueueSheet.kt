package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.collections.immutable.ImmutableList
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.components.PlayerSheet
import live.mehiz.mpvkt.ui.player.PlaylistNode
import live.mehiz.mpvkt.ui.theme.spacing

@Composable
fun QueueSheet(
  entries: ImmutableList<PlaylistNode>,
  onJump: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onClear: () -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  PlayerSheet(onDismissRequest) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .padding(vertical = MaterialTheme.spacing.medium),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.player_sheets_queue_title),
          style = MaterialTheme.typography.headlineMedium,
        )
        TextButton(onClick = onClear) {
          Text(stringResource(R.string.player_sheets_queue_clear))
        }
      }
      LazyColumn {
        itemsIndexed(entries) { index, entry ->
          QueueEntry(
            entry = entry,
            onClick = { onJump(index) },
            onRemove = { onRemove(index) },
          )
        }
      }
    }
  }
}

@Composable
private fun QueueEntry(
  entry: PlaylistNode,
  onClick: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.smaller),
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Default.PlayCircle,
      contentDescription = null,
      tint = if (entry.isCurrent) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
    Text(
      text = entry.displayName,
      fontStyle = if (entry.isCurrent) FontStyle.Italic else FontStyle.Normal,
      fontWeight = if (entry.isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
      color = if (entry.isCurrent) {
        MaterialTheme.colorScheme.onSurface
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    IconButton(onClick = onRemove) {
      Icon(Icons.Outlined.Close, contentDescription = null)
    }
  }
}
