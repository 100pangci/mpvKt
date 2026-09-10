package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.theme.spacing
import kotlin.math.roundToInt

private val PLAYER_UPDATE_EDGE_MARGIN = 32.dp

@Composable
fun PlayerUpdate(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit = {},
) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = PLAYER_UPDATE_EDGE_MARGIN),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .widthIn(max = maxWidth)
        .clip(RoundedCornerShape(16.dp))
        .background(Color.Black.copy(0.4f))
        .padding(vertical = MaterialTheme.spacing.smaller, horizontal = MaterialTheme.spacing.medium)
        .animateContentSize(),
      contentAlignment = Alignment.Center,
    ) { content() }
  }
}

@Composable
fun TextPlayerUpdate(
  text: String,
  modifier: Modifier = Modifier
) {
  PlayerUpdate(modifier) {
    BoxWithConstraints {
      val textMeasurer = rememberTextMeasurer()
      val textStyle = LocalTextStyle.current
      val maxTextWidth = with(LocalDensity.current) { maxWidth.toPx().roundToInt() }
      val wrappedText = remember(text, textStyle, maxTextWidth, textMeasurer) {
        wrapTextByCharacterWidth(text.trim(), maxTextWidth, textMeasurer, textStyle)
      }
      Text(
        text = wrappedText,
        style = textStyle,
        softWrap = false,
        overflow = TextOverflow.Clip,
      )
    }
  }
}

private fun wrapTextByCharacterWidth(
  text: String,
  maxWidth: Int,
  textMeasurer: TextMeasurer,
  textStyle: TextStyle,
): String {
  if (text.isEmpty() || maxWidth <= 0) return text

  val wrapped = StringBuilder(text.length + text.length / 20)
  val line = StringBuilder()
  var index = 0
  while (index < text.length) {
    val end = if (
      text[index].isHighSurrogate() &&
      index + 1 < text.length &&
      text[index + 1].isLowSurrogate()
    ) {
      index + 2
    } else {
      index + 1
    }
    val character = text.substring(index, end)
    index = end

    if (character == "\n") {
      wrapped.append(line).append('\n')
      line.clear()
      continue
    }

    val candidate = line.toString() + character
    val candidateWidth = textMeasurer.measure(
      text = candidate,
      style = textStyle,
      softWrap = false,
      maxLines = 1,
      constraints = Constraints(),
    ).size.width
    if (line.isNotEmpty() && candidateWidth > maxWidth) {
      wrapped.append(line).append('\n')
      line.clear()
    }
    line.append(character)
  }
  wrapped.append(line)
  return wrapped.toString()
}

@Composable
fun MultipleSpeedPlayerUpdate(
  currentSpeed: Float,
  modifier: Modifier = Modifier
) {
  PlayerUpdate(modifier) {
    Row(
      verticalAlignment = Alignment.Bottom,
    ) {
      Text(
        stringResource(R.string.player_speed, currentSpeed),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyLarge,
      )
      Icon(
        Icons.Filled.DoubleArrow,
        null,
      )
    }
  }
}

@Composable
@Preview
private fun PreviewMultipleSpeedPlayerUpdate() {
  MultipleSpeedPlayerUpdate(currentSpeed = 2f)
}
