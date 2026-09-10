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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.theme.spacing

private val WHITESPACE_RUN = Regex("\\s+")

@Composable
fun PlayerUpdate(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit = {},
) {
  BoxWithConstraints(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center,
  ) {
    // Auto-width: the pill grows with the text but never crosses a fixed
    // margin from either screen edge, whatever the orientation.
    val sideMargin = MaterialTheme.spacing.medium
    Box(
      modifier = Modifier
        .widthIn(max = maxWidth - sideMargin - sideMargin)
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
  // Break wherever the width cap lands instead of preferring spaces:
  // space-boundary wrapping leaves rows ending early on titles like
  // "[Group] Show! [01][tags]-00-31-27-N0001.png". Non-breaking spaces
  // turn the message into one run that fills every row edge to edge.
  PlayerUpdate(modifier) {
    Text(text.trim().replace(WHITESPACE_RUN, "\u00A0"))
  }
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
