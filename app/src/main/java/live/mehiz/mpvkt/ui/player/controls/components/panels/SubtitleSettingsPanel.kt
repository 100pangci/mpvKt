package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.player.controls.components.panels.components.MultiCardPanel

@Composable
fun SubtitleSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MultiCardPanel(
    onDismissRequest = onDismissRequest,
    titleRes = R.string.player_sheets_subtitles_settings_title,
    // Typography and colors apply to every text subtitle: external formats
    // (SRT & friends) always use the player-side style, ASS scripts only
    // follow it when overriding is enabled in the miscellaneous card.
    cardCount = 3,
    modifier = modifier,
  ) { index, cardModifier ->
    when (index) {
      0 -> SubtitleSettingsTypographyCard(cardModifier)
      1 -> SubtitleSettingsColorsCard(cardModifier)
      2 -> SubtitlesMiscellaneousCard(cardModifier)
      else -> {}
    }
  }
}
