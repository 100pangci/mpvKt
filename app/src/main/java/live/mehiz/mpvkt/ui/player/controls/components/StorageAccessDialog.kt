package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.theme.spacing

/**
 * Shown when a video was opened from another app without "All files access":
 * the file itself is playable through its SAF grant, but subtitles and fonts
 * sitting next to it cannot be read, so automatic subtitle matching silently
 * fails. The dialog offers the grant; declining it once is remembered for
 * the session, "don't ask" is remembered forever.
 */
@Composable
fun StorageAccessDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  onDontAskAgain: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.player_storage_access_title)) },
    text = { Text(text = stringResource(R.string.player_storage_access_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(text = stringResource(R.string.storage_all_files_grant))
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
        TextButton(onClick = onDontAskAgain) {
          Text(text = stringResource(R.string.storage_all_files_dont_ask))
        }
        TextButton(onClick = onDismiss) {
          Text(text = stringResource(R.string.generic_cancel))
        }
      }
    },
  )
}
