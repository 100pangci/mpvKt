package live.mehiz.mpvkt.ui.player.controls

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.ui.player.controls.components.ControlsButton
import live.mehiz.mpvkt.ui.player.execute
import live.mehiz.mpvkt.ui.player.executeLongClick
import live.mehiz.mpvkt.ui.theme.spacing

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("NewApi")
@Composable
fun BottomRightPlayerControls(
  customButton: CustomButtonEntity?,
  customButtonTitle: String,
  isPipAvailable: Boolean,
  onScreenshotSubsTap: () -> Unit,
  onScreenshotRawTap: () -> Unit,
  onFrameStep: (forward: Boolean) -> Unit,
  onAspectClick: () -> Unit,
  onPipClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier) {
    if (customButton != null) {
      Box(modifier = Modifier.padding(end = MaterialTheme.spacing.smaller)) {
        Button(onClick = {}) {
          Text(text = customButtonTitle)
        }
        Box(
          modifier = Modifier
            .matchParentSize()
            .combinedClickable(
              onClick = customButton::execute,
              onLongClick = customButton::executeLongClick,
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ),
        )
      }
    }

    if (isPipAvailable) {
      ControlsButton(
        Icons.Default.PictureInPictureAlt,
        onClick = onPipClick,
      )
    }

    Box(modifier = Modifier.padding(end = MaterialTheme.spacing.smaller)) {
      ControlsButton(
        Icons.Default.Screenshot,
        onClick = onScreenshotSubsTap,
      )
      SubtitlesBadge(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .offset(x = 3.dp, y = 3.dp)
          .zIndex(1f),
      )
    }
    ControlsButton(
      Icons.Default.Screenshot,
      onClick = onScreenshotRawTap,
    )

    ControlsButton(
      Icons.Default.SkipPrevious,
      onClick = { onFrameStep(false) },
    )
    ControlsButton(
      Icons.Default.SkipNext,
      onClick = { onFrameStep(true) },
    )

    ControlsButton(
      Icons.Default.AspectRatio,
      onClick = onAspectClick,
    )
  }
}

@Composable
private fun SubtitlesBadge(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(12.dp)
      .background(Color.Black, CircleShape)
      .border(0.5.dp, Color.White, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Default.Subtitles,
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(8.dp),
    )
  }
}
