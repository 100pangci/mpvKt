package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.ui.player.controls.LocalPlayerButtonsClickEvent
import live.mehiz.mpvkt.ui.theme.spacing

private const val FRAME_STEP_REPEAT_INTERVAL_MILLIS = 90L

@Composable
fun FrameStepButton(
  icon: ImageVector,
  contentDescription: String,
  forward: Boolean,
  onFrameStep: (forward: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  color: Color = Color.White,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val clickEvent = LocalPlayerButtonsClickEvent.current
  val currentClickEvent by rememberUpdatedState(clickEvent)
  val currentOnFrameStep by rememberUpdatedState(onFrameStep)

  Box(
    modifier = modifier
      .clip(CircleShape)
      .indication(interactionSource, ripple())
      .pointerInput(forward) {
        var repeatJob: Job? = null
        val repeatScope = CoroutineScope(currentCoroutineContext())
        detectTapGestures(
          onTap = { currentOnFrameStep(forward) },
          onLongPress = {
            repeatJob = repeatScope.launch {
              while (isActive) {
                currentOnFrameStep(forward)
                delay(FRAME_STEP_REPEAT_INTERVAL_MILLIS)
              }
            }
          },
          onPress = { position ->
            currentClickEvent()
            val press = PressInteraction.Press(position)
            interactionSource.emit(press)
            try {
              if (tryAwaitRelease()) {
                interactionSource.emit(PressInteraction.Release(press))
              } else {
                interactionSource.emit(PressInteraction.Cancel(press))
              }
            } finally {
              repeatJob?.cancel()
              repeatJob = null
            }
          },
        )
      }
      .padding(MaterialTheme.spacing.medium),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = color,
      modifier = Modifier.size(20.dp),
    )
  }
}
