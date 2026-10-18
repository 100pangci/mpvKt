package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.player.MPVLib
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.preferences.preference.deleteAndGet
import live.mehiz.mpvkt.presentation.components.ExpandableCard
import live.mehiz.mpvkt.presentation.components.SliderItem
import live.mehiz.mpvkt.ui.player.controls.CARDS_MAX_WIDTH
import live.mehiz.mpvkt.ui.player.controls.components.sheets.toFixed
import live.mehiz.mpvkt.ui.player.controls.panelCardsColors
import live.mehiz.mpvkt.ui.theme.spacing
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Composable
fun SubtitlesMiscellaneousCard(modifier: Modifier = Modifier) {
  val preferences = koinInject<SubtitlesPreferences>()
  var isExpanded by remember { mutableStateOf(true) }
  ExpandableCard(
    isExpanded,
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(Icons.Default.Tune, null)
        Text(stringResource(R.string.player_sheets_sub_misc_card_title))
      }
    },
    onExpand = { isExpanded = !isExpanded },
    modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    ProvidePreferenceLocals {
      Column {
        var overrideAssSubs by remember {
          mutableStateOf(MPVLib.getPropertyString("sub-ass-override") == "force")
        }
        SwitchPreference(
          overrideAssSubs,
          onValueChange = {
            overrideAssSubs = it
            preferences.overrideAssSubs.set(it)
            MPVLib.setPropertyString("sub-ass-override", if (it) "force" else "no")
          },
          { Text(stringResource(R.string.player_sheets_sub_override_ass)) },
        )
        // Inverse of the switch above: per-subtitle font matching and forced
        // default-font rendering are mutually exclusive.
        SwitchPreference(
          !overrideAssSubs,
          onValueChange = {
            overrideAssSubs = !it
            preferences.overrideAssSubs.set(!it)
            MPVLib.setPropertyString("sub-ass-override", if (!it) "force" else "no")
          },
          { Text(stringResource(R.string.player_sheets_sub_match_fonts)) },
        )
        SubtitleLoadingSwitches(preferences)
        val subScale by MPVLib.propFloat["sub-scale"].collectAsState()
        val subPos by MPVLib.propInt["sub-pos"].collectAsState()
        SliderItem(
          label = stringResource(R.string.player_sheets_sub_scale),
          value = subScale!!,
          valueText = subScale!!.toFixed(2).toString(),
          onChange = {
            preferences.subScale.set(it)
            MPVLib.setPropertyFloat("sub-scale", it)
          },
          max = 5f,
          icon = {
            Icon(
              Icons.Default.FormatSize,
              null,
            )
          },
        )
        SliderItem(
          label = stringResource(R.string.player_sheets_sub_position),
          value = subPos ?: preferences.subPos.get(),
          valueText = subPos.toString(),
          onChange = {
            preferences.subPos.set(it)
            MPVLib.setPropertyInt("sub-pos", it)
          },
          max = 150,
          icon = {
            Icon(
              Icons.Default.AlignVerticalCenter,
              null,
            )
          },
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.medium),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = { resetMiscellaneous(preferences) { overrideAssSubs = it } },
          ) {
            Row {
              Icon(Icons.Default.EditOff, null)
              Text(stringResource(R.string.generic_reset))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SubtitleLoadingSwitches(preferences: SubtitlesPreferences) {
  val fallback by preferences.fallback.collectAsState()
  SwitchPreference(
    fallback,
    onValueChange = {
      preferences.fallback.set(it)
      MPVLib.setPropertyString("subs-fallback", if (it) "yes" else "default")
    },
    { Text(stringResource(R.string.pref_subtitles_fallback_title)) },
  )
  val scaleSigns by preferences.scaleSigns.collectAsState()
  SwitchPreference(
    scaleSigns,
    onValueChange = {
      preferences.scaleSigns.set(it)
      MPVLib.setPropertyString("sub-scale-signs", if (it) "yes" else "no")
    },
    { Text(stringResource(R.string.pref_subtitles_scale_signs_title)) },
  )
  val useMargins by preferences.useMargins.collectAsState()
  SwitchPreference(
    useMargins,
    onValueChange = {
      preferences.useMargins.set(it)
      MPVLib.setPropertyString("sub-use-margins", if (it) "yes" else "no")
    },
    { Text(stringResource(R.string.pref_subtitles_use_margins_title)) },
  )
}

private fun resetMiscellaneous(
  preferences: SubtitlesPreferences,
  onOverrideAssSubs: (Boolean) -> Unit,
) {
  preferences.subPos.deleteAndGet().let {
    MPVLib.setPropertyInt("sub-pos", it)
  }
  preferences.subScale.deleteAndGet().let {
    MPVLib.setPropertyFloat("sub-scale", it)
  }
  preferences.overrideAssSubs.deleteAndGet().let {
    onOverrideAssSubs(it)
    MPVLib.setPropertyString("sub-ass-override", if (it) "force" else "no")
  }
  preferences.fallback.deleteAndGet().let {
    MPVLib.setPropertyString("subs-fallback", if (it) "yes" else "default")
  }
  preferences.scaleSigns.deleteAndGet().let {
    MPVLib.setPropertyString("sub-scale-signs", if (it) "yes" else "no")
  }
  preferences.useMargins.deleteAndGet().let {
    MPVLib.setPropertyString("sub-use-margins", if (it) "yes" else "no")
  }
}
