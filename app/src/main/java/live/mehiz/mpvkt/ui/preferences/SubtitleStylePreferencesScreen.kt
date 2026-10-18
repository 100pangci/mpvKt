package live.mehiz.mpvkt.ui.preferences

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.dao.FontDao
import live.mehiz.mpvkt.preferences.SYSTEM_SUBTITLE_FONT
import live.mehiz.mpvkt.preferences.SubtitleJustification
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.ExposedTextDropDownMenu
import live.mehiz.mpvkt.presentation.components.SliderItem
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubColorType
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubtitlesColorPicker
import live.mehiz.mpvkt.ui.player.controls.components.panels.icon
import live.mehiz.mpvkt.ui.player.controls.components.sheets.toFixed
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.koin.compose.koinInject

/**
 * Global defaults for the player-side text subtitle style. Everything here is
 * applied the next time a video starts; the in-player subtitle settings sheet
 * edits the same values live.
 */
@Serializable
object SubtitleStylePreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<SubtitlesPreferences>()
    val fontDao = koinInject<FontDao>()

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.pref_subtitles_style)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding),
        ) {
          SubtitleFontPicker(preferences, fontDao)
          SubtitleFontSizePreferences(preferences)
          SubtitleOutlinePreferences(preferences)
          SubtitleColorPreferences(preferences)
          SubtitlePositionPreferences(preferences)
          SubtitleAdvancedPreferences(preferences)
        }
      }
    }
  }
}

@Composable
private fun SubtitleFontPicker(
  preferences: SubtitlesPreferences,
  fontDao: FontDao,
) {
  var families by remember { mutableStateOf(emptyList<String>()) }
  var loading by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    val result = withContext(Dispatchers.IO) {
      runCatching { fontDao.primaryFamilies().sortedBy { it.lowercase() } }.getOrDefault(emptyList())
    }
    families = result
    loading = false
  }
  val font by preferences.font.collectAsState()
  val systemDefaultFont = stringResource(R.string.subtitle_font_system_default)
  val displayFont = if (font == SYSTEM_SUBTITLE_FONT) systemDefaultFont else font
  val options = remember(displayFont, systemDefaultFont, families) {
    (listOf(displayFont, systemDefaultFont) + families).distinct().toImmutableList()
  }
  val loadingIcon: (@Composable () -> Unit)? = if (loading) {
    { CircularProgressIndicator(Modifier.size(32.dp)) }
  } else {
    null
  }
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.smaller),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
      Icon(painterResource(R.drawable.outline_brand_family_24), null, Modifier.size(32.dp))
      ExposedTextDropDownMenu(
        selectedValue = displayFont,
        options = options,
        label = stringResource(R.string.pref_subtitles_font),
        onValueChangedEvent = {
          preferences.font.set(if (it == systemDefaultFont) SYSTEM_SUBTITLE_FONT else it)
        },
        leadingIcon = loadingIcon,
        modifier = Modifier.weight(1f),
      )
    }
    if (!loading && families.isEmpty()) {
      Text(
        text = stringResource(R.string.typography_fonts_empty_hint),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
      )
    }
  }
}

@Composable
private fun SubtitleFontSizePreferences(preferences: SubtitlesPreferences) {
  val context = LocalContext.current
  val fontSize by preferences.fontSize.collectAsState()
  SliderItem(
    label = stringResource(R.string.pref_subtitles_font_size),
    value = fontSize,
    valueText = fontSize.toString(),
    onChange = preferences.fontSize::set,
    min = 1,
    max = 100,
    icon = { Icon(Icons.Default.FormatSize, null) },
  )
  val bold by preferences.bold.collectAsState()
  SwitchPreference(
    value = bold,
    onValueChange = preferences.bold::set,
    title = { Text(stringResource(R.string.pref_subtitles_bold)) },
  )
  val italic by preferences.italic.collectAsState()
  SwitchPreference(
    value = italic,
    onValueChange = preferences.italic::set,
    title = { Text(stringResource(R.string.pref_subtitles_italic)) },
  )
  val justification by preferences.justification.collectAsState()
  ListPreference(
    value = justification,
    onValueChange = preferences.justification::set,
    title = { Text(stringResource(R.string.pref_subtitles_align)) },
    valueToText = { AnnotatedString(context.getString(it.titleRes)) },
    values = SubtitleJustification.entries,
    type = ListPreferenceType.DROPDOWN_MENU,
    summary = { Text(stringResource(justification.titleRes)) },
  )
}

@Composable
private fun SubtitleOutlinePreferences(preferences: SubtitlesPreferences) {
  val outlineEnabled by preferences.outlineEnabled.collectAsState()
  SwitchPreference(
    value = outlineEnabled,
    onValueChange = preferences.outlineEnabled::set,
    title = { Text(stringResource(R.string.player_sheets_sub_outline_title)) },
  )
  val borderSize by preferences.borderSize.collectAsState()
  SliderItem(
    label = stringResource(R.string.player_sheets_sub_typography_border_size),
    value = borderSize,
    valueText = borderSize.toString(),
    onChange = preferences.borderSize::set,
    max = 100,
    icon = { Icon(Icons.Default.BorderColor, null) },
  )
  val shadowEnabled by preferences.shadowEnabled.collectAsState()
  SwitchPreference(
    value = shadowEnabled,
    onValueChange = preferences.shadowEnabled::set,
    title = { Text(stringResource(R.string.player_sheets_sub_shadow_title)) },
  )
  val shadowOffset by preferences.shadowOffset.collectAsState()
  SliderItem(
    label = stringResource(R.string.player_sheets_subtitles_shadow_offset),
    value = shadowOffset,
    valueText = shadowOffset.toFixed(1).toString(),
    onChange = preferences.shadowOffset::set,
    max = 10f,
    icon = { Icon(painterResource(R.drawable.sharp_shadow_24), null) },
  )
}

@Composable
private fun SubtitleColorPreferences(preferences: SubtitlesPreferences) {
  var currentType by remember { mutableStateOf(SubColorType.Text) }
  var currentColor by remember { mutableIntStateOf(currentType.preference(preferences).get()) }
  LaunchedEffect(currentType) {
    currentColor = currentType.preference(preferences).get()
  }
  Column {
    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = MaterialTheme.spacing.medium),
    ) {
      SubColorType.entries.forEach { type ->
        IconToggleButton(
          checked = currentType == type,
          onCheckedChange = { currentType = type },
        ) {
          Icon(type.icon, null)
        }
      }
      Text(stringResource(currentType.titleRes))
    }
    SubtitlesColorPicker(
      currentColor,
      onColorChange = {
        currentColor = it
        currentType.preference(preferences).set(it)
      },
    )
  }
}

@Composable
private fun SubtitlePositionPreferences(preferences: SubtitlesPreferences) {
  val subPos by preferences.subPos.collectAsState()
  SliderItem(
    label = stringResource(R.string.player_sheets_sub_position),
    value = subPos,
    valueText = subPos.toString(),
    onChange = preferences.subPos::set,
    max = 150,
    icon = { Icon(Icons.Default.AlignVerticalCenter, null) },
  )
  val subScale by preferences.subScale.collectAsState()
  SliderItem(
    label = stringResource(R.string.player_sheets_sub_scale),
    value = subScale,
    valueText = subScale.toFixed(2).toString(),
    onChange = preferences.subScale::set,
    max = 5f,
    icon = { Icon(Icons.Default.FormatSize, null) },
  )
}

@Composable
private fun SubtitleAdvancedPreferences(preferences: SubtitlesPreferences) {
  val codepage by preferences.codepage.collectAsState()
  TextFieldPreference(
    value = codepage,
    onValueChange = preferences.codepage::set,
    textToValue = { it },
    title = { Text(stringResource(R.string.pref_subtitles_codepage_title)) },
    summary = { if (codepage.isNotBlank()) Text(codepage) },
    textField = { value, onValueChange, _ ->
      Column {
        Text(stringResource(R.string.pref_subtitles_codepage_summary))
        TextField(value, onValueChange, modifier = Modifier.fillMaxWidth())
      }
    },
  )
  val filePaths by preferences.filePaths.collectAsState()
  TextFieldPreference(
    value = filePaths,
    onValueChange = preferences.filePaths::set,
    textToValue = { it },
    title = { Text(stringResource(R.string.pref_subtitles_sub_file_paths_title)) },
    summary = { if (filePaths.isNotBlank()) Text(filePaths) },
    textField = { value, onValueChange, _ ->
      Column {
        Text(stringResource(R.string.pref_subtitles_sub_file_paths_summary))
        TextField(value, onValueChange, modifier = Modifier.fillMaxWidth())
      }
    },
  )
  val assStyleOverrides by preferences.assStyleOverrides.collectAsState()
  TextFieldPreference(
    value = assStyleOverrides,
    onValueChange = preferences.assStyleOverrides::set,
    textToValue = { it },
    title = { Text(stringResource(R.string.pref_subtitles_ass_style_overrides_title)) },
    summary = { if (assStyleOverrides.isNotBlank()) Text(assStyleOverrides) },
    textField = { value, onValueChange, _ ->
      Column {
        Text(stringResource(R.string.pref_subtitles_ass_style_overrides_summary))
        TextField(value, onValueChange, modifier = Modifier.fillMaxWidth())
      }
    },
  )
  val hdrPeak by preferences.hdrPeak.collectAsState()
  TextFieldPreference(
    value = hdrPeak,
    onValueChange = preferences.hdrPeak::set,
    textToValue = { it },
    title = { Text(stringResource(R.string.pref_subtitles_hdr_peak_title)) },
    summary = { if (hdrPeak.isNotBlank()) Text(hdrPeak) },
    textField = { value, onValueChange, _ ->
      Column {
        Text(stringResource(R.string.pref_subtitles_hdr_peak_summary))
        TextField(value, onValueChange, modifier = Modifier.fillMaxWidth())
      }
    },
  )
  val fallback by preferences.fallback.collectAsState()
  SwitchPreference(
    value = fallback,
    onValueChange = preferences.fallback::set,
    title = { Text(stringResource(R.string.pref_subtitles_fallback_title)) },
    summary = { Text(stringResource(R.string.pref_subtitles_fallback_summary)) },
  )
  val scaleSigns by preferences.scaleSigns.collectAsState()
  SwitchPreference(
    value = scaleSigns,
    onValueChange = preferences.scaleSigns::set,
    title = { Text(stringResource(R.string.pref_subtitles_scale_signs_title)) },
    summary = { Text(stringResource(R.string.pref_subtitles_scale_signs_summary)) },
  )
  val useMargins by preferences.useMargins.collectAsState()
  SwitchPreference(
    value = useMargins,
    onValueChange = preferences.useMargins::set,
    title = { Text(stringResource(R.string.pref_subtitles_use_margins_title)) },
    summary = { Text(stringResource(R.string.pref_subtitles_use_margins_summary)) },
  )
}
