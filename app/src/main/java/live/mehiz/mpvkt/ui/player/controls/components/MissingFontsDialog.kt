package live.mehiz.mpvkt.ui.player.controls.components

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.MainActivity
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.theme.spacing

@Composable
fun MissingFontsDialog(
  fonts: Set<String>,
  onCopy: () -> Unit,
  onDismiss: () -> Unit,
) {
  val clipboard = LocalClipboard.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.missing_fonts_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Text(
          text = stringResource(R.string.missing_fonts_body),
          style = MaterialTheme.typography.bodyMedium,
        )
        Column(
          modifier = Modifier
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
        ) {
          fonts.sorted().forEach { font ->
            Text(
              text = "• $font",
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(vertical = MaterialTheme.spacing.smaller),
            )
          }
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        TextButton(
          onClick = {
            // Jump straight to the screen where a fonts folder can be
            // configured instead of leaving the user to find it.
            context.startActivity(
              Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_SUBTITLE_SETTINGS, true)
              },
            )
            onDismiss()
          },
        ) {
          Text(text = stringResource(R.string.missing_fonts_open_settings))
        }
        TextButton(
          onClick = {
            scope.launch {
              clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, fonts.sorted().joinToString(", "))))
            }
            onCopy()
          },
        ) {
          Text(text = stringResource(R.string.missing_fonts_copy))
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(R.string.generic_cancel))
      }
    },
  )
}
