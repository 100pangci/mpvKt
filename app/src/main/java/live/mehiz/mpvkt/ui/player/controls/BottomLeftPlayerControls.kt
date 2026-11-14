// Modified from upstream mpvKt; see LICENSE-APACHE.
package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.vivvvek.seeker.Segment
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.player.Sheets
import live.mehiz.mpvkt.ui.player.controls.components.ControlsButton
import live.mehiz.mpvkt.ui.player.controls.components.CurrentChapter

@Composable
fun BottomLeftPlayerControls(
  playbackSpeed: Float,
  currentChapter: Segment?,
  showChapterIndicator: Boolean,
  onPlaybackSpeedChange: (Float) -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ControlsButton(
      text = stringResource(R.string.player_speed, playbackSpeed),
      onClick = { onPlaybackSpeedChange(if (playbackSpeed >= 2) 0.25f else playbackSpeed + 0.25f) },
      onLongClick = { onOpenSheet(Sheets.PlaybackSpeed) },
    )
    AnimatedVisibility(
      showChapterIndicator && currentChapter != null,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      currentChapter?.let { chapter ->
        CurrentChapter(
          chapter = chapter,
          onClick = { onOpenSheet(Sheets.Chapters) }
        )
      }
    }
  }
}
