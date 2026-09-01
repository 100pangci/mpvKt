package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.ui.player.controls.components.panels.components.MultiCardPanel
import org.koin.compose.koinInject

@Composable
fun SubtitleSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val forceDefaultFont = koinInject<SubtitlesPreferences>().overrideAssSubs.collectAsState()
  MultiCardPanel(
    onDismissRequest = onDismissRequest,
    titleRes = R.string.player_sheets_subtitles_settings_title,
    // Native font-matching mode keeps ONLY the switch card: typography and
    // colors would intrude on script-defined ASS styling.
    cardCount = if (forceDefaultFont.value) 3 else 1,
    modifier = modifier,
  ) { index, cardModifier ->
    when (index) {
      0 -> if (forceDefaultFont.value) {
        SubtitleSettingsTypographyCard(cardModifier)
      } else {
        SubtitlesMiscellaneousCard(cardModifier)
      }
      1 -> if (forceDefaultFont.value) SubtitleSettingsColorsCard(cardModifier)
      2 -> if (forceDefaultFont.value) SubtitlesMiscellaneousCard(cardModifier)
      else -> {}
    }
  }
}
