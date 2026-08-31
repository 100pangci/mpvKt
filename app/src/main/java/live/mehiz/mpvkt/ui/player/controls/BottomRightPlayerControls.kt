package live.mehiz.mpvkt.ui.player.controls

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoCameraBack
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.ui.player.controls.components.ControlsButton
import live.mehiz.mpvkt.ui.player.controls.components.FrameStepButton
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
  onFrameStepEnd: (withSubtitles: Boolean) -> Unit,
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

    FrameStepButton(
      icon = Icons.Default.PhotoCamera,
      contentDescription = stringResource(R.string.screenshot_subtitles),
      onTap = onScreenshotSubsTap,
      onFrameStep = onFrameStep,
      onFrameStepEnd = { onFrameStepEnd(true) },
    )
    FrameStepButton(
      icon = Icons.Default.PhotoCameraBack,
      contentDescription = stringResource(R.string.screenshot_raw),
      onTap = onScreenshotRawTap,
      onFrameStep = onFrameStep,
      onFrameStepEnd = { onFrameStepEnd(false) },
    )

    ControlsButton(
      Icons.Default.AspectRatio,
      onClick = onAspectClick,
    )
  }
}
