package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import live.mehiz.mpvkt.ui.player.controls.LocalPlayerButtonsClickEvent
import live.mehiz.mpvkt.ui.theme.spacing

private val FrameStepDistance = 32.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FrameStepButton(
  icon: ImageVector,
  contentDescription: String,
  onTap: () -> Unit,
  onFrameStep: (forward: Boolean) -> Unit,
  onFrameStepEnd: () -> Unit,
  modifier: Modifier = Modifier,
  color: Color = Color.White,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val clickEvent = LocalPlayerButtonsClickEvent.current
  Box(
    modifier = modifier
      .indication(interactionSource, ripple())
      .clip(CircleShape)
      .pointerInput(Unit) {
        val stepThreshold = FrameStepDistance.toPx()
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val press = PressInteraction.Press(down.position)
          interactionSource.tryEmit(press)
          clickEvent()
          var accumulated = 0f
          var stepped = false
          try {
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id }
              if (change == null || !change.pressed) break
              accumulated += change.positionChange().x
              while (accumulated >= stepThreshold) {
                onFrameStep(true)
                accumulated -= stepThreshold
                stepped = true
              }
              while (accumulated <= -stepThreshold) {
                onFrameStep(false)
                accumulated += stepThreshold
                stepped = true
              }
              change.consume()
            }
            interactionSource.tryEmit(PressInteraction.Release(press))
          } catch (e: CancellationException) {
            interactionSource.tryEmit(PressInteraction.Cancel(press))
            throw e
          }
          if (stepped) onFrameStepEnd() else onTap()
        }
      }
      .padding(MaterialTheme.spacing.medium),
  ) {
    Icon(
      icon,
      contentDescription,
      tint = color,
      modifier = Modifier.size(20.dp),
    )
  }
}
