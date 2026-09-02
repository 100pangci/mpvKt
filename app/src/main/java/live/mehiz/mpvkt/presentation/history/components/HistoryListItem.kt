package live.mehiz.mpvkt.presentation.history.components

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.ui.theme.spacing
import kotlin.math.roundToInt

@Composable
fun HistoryListItem(
  item: PlaybackStateEntity,
  onResume: (PlaybackStateEntity) -> Unit,
  onDelete: (PlaybackStateEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val progress = item.duration.takeIf { it > 0 }?.let { item.lastPosition.toFloat() / it } ?: 0f
  val percent = stringResource(R.string.value_percentage_int, (progress * 100).roundToInt())
  val watchedAt = remember(item.lastPlayedAt) {
    DateUtils.getRelativeTimeSpanString(item.lastPlayedAt).toString()
  }
  // Entries written before the history columns existed have no timestamp:
  // fall back to the watched percentage alone instead of the epoch.
  val subtitle = if (item.lastPlayedAt == 0L) percent else "$watchedAt · $percent"
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(enabled = item.uri.isNotBlank()) { onResume(item) }
      .padding(
        start = MaterialTheme.spacing.medium,
        top = MaterialTheme.spacing.small,
        end = MaterialTheme.spacing.smaller,
        bottom = MaterialTheme.spacing.small,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.mediaTitle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (item.duration > 0) {
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.extraSmall),
        )
      }
    }
    IconButton(onClick = { onDelete(item) }) {
      Icon(Icons.Outlined.Delete, null)
    }
  }
}
