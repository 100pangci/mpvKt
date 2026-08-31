package live.mehiz.mpvkt.ui.player.controls.components

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
import androidx.compose.ui.Alignment
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
private val CancelDistance = 64.dp
private const val KEEP_ALIVE_INTERVAL_MILLIS = 250L

@Composable
fun FrameStepButton(
  icon: ImageVector,
  contentDescription: String,
  onTap: () -> Unit,
  onFrameStep: (forward: Boolean) -> Unit,
  onFrameStepEnd: () -> Unit,
  onCancelChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  color: Color = Color.White,
  overlay: (@Composable () -> Unit)? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val clickEvent = LocalPlayerButtonsClickEvent.current
  Box(
    modifier = modifier
      .clip(CircleShape)
      .indication(interactionSource, ripple())
      .pointerInput(onTap, onFrameStep, onFrameStepEnd, onCancelChange) {
        val tracker = FrameStepTracker(
          stepThreshold = FrameStepDistance.toPx(),
          cancelThreshold = CancelDistance.toPx(),
          onTap = onTap,
          onFrameStep = onFrameStep,
          onFrameStepEnd = onFrameStepEnd,
          onCancelChange = onCancelChange,
        )
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val press = PressInteraction.Press(down.position)
          tracker.reset()
          interactionSource.tryEmit(press)
          clickEvent()
          var lastKeepAlive = down.uptimeMillis
          var received = false
          try {
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id }
              if (change == null || !change.pressed) break
              received = true
              if (change.uptimeMillis - lastKeepAlive > KEEP_ALIVE_INTERVAL_MILLIS) {
                clickEvent()
                lastKeepAlive = change.uptimeMillis
              }
              val delta = change.positionChange()
              tracker.onMove(delta.x, delta.y)
              change.consume()
            }
            interactionSource.tryEmit(PressInteraction.Release(press))
          } catch (e: CancellationException) {
            interactionSource.tryEmit(PressInteraction.Cancel(press))
            if (received) tracker.capture()
            throw e
          }
          tracker.capture()
        }
      }
      .padding(MaterialTheme.spacing.medium),
  ) {
    Box(modifier = Modifier.size(20.dp)) {
      Icon(
        icon,
        contentDescription,
        tint = color,
        modifier = Modifier.size(20.dp),
      )
      overlay?.let {
        Box(modifier = Modifier.align(Alignment.BottomEnd)) { it() }
      }
    }
  }
}

private class FrameStepTracker(
  stepThreshold: Float,
  private val cancelThreshold: Float,
  private val onTap: () -> Unit,
  private val onFrameStep: (forward: Boolean) -> Unit,
  private val onFrameStepEnd: () -> Unit,
  private val onCancelChange: (Boolean) -> Unit,
) {
  private val stepThreshold = stepThreshold
  private var stepped = false
  private var cancelled = false
  private var accumulatedX = 0f
  private var accumulatedY = 0f

  fun reset() {
    stepped = false
    cancelled = false
    accumulatedX = 0f
    accumulatedY = 0f
  }

  fun onMove(dx: Float, dy: Float) {
    accumulatedY += dy
    if (cancelled) {
      accumulatedX = 0f
      if (accumulatedY > -cancelThreshold / 2f) {
        cancelled = false
        accumulatedY = 0f
        onCancelChange(false)
      }
    } else if (accumulatedY <= -cancelThreshold) {
      cancelled = true
      accumulatedX = 0f
      onCancelChange(true)
    } else {
      accumulatedX += dx
    }
    while (accumulatedX >= stepThreshold) {
      onFrameStep(true)
      accumulatedX -= stepThreshold
      stepped = true
    }
    while (accumulatedX <= -stepThreshold) {
      onFrameStep(false)
      accumulatedX += stepThreshold
      stepped = true
    }
  }

  fun capture() {
    when {
      cancelled -> onCancelChange(false)
      stepped -> onFrameStepEnd()
      else -> onTap()
    }
  }
}
