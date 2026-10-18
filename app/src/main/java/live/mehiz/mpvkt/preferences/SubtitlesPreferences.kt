package live.mehiz.mpvkt.preferences

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.preference.PreferenceStore
import live.mehiz.mpvkt.preferences.preference.getEnum
import live.mehiz.mpvkt.ui.player.controls.components.panels.SubtitlesBorderStyle

/**
 * mpv's own default value for `sub-font`: the generic fontconfig family,
 * resolved by the platform instead of pinning a specific (potentially
 * commercially licensed) family. On Android this means the system font when
 * system fonts are indexed, and the bundled CJK fallback otherwise.
 */
const val SYSTEM_SUBTITLE_FONT = "sans-serif"

class SubtitlesPreferences(preferenceStore: PreferenceStore) {
  val autoLoadExternal = preferenceStore.getBoolean("sub_autoload", true)
  val preferredLanguages = preferenceStore.getString(
    "sub_preferred_languages",
    defaultPreferredSubtitleLanguages,
  )

  val fontsFolder = preferenceStore.getString("sub_fonts_folder")
  val useSystemFonts = preferenceStore.getBoolean("sub_use_system_fonts", false)
  val fontIndexScanAt = preferenceStore.getLong("sub_font_index_scan_at", 0L)
  val font = preferenceStore.getString("sub_font", SYSTEM_SUBTITLE_FONT)
  val fontSize = preferenceStore.getInt("sub_font_size", 44)
  val subScale = preferenceStore.getFloat("sub_scale", 1f)
  val borderSize = preferenceStore.getInt("sub_border_size", 2)
  val bold = preferenceStore.getBoolean("sub_bold")
  val italic = preferenceStore.getBoolean("sub_italic")

  val textColor = preferenceStore.getInt("sub_color_text", Color.White.toArgb())

  val borderColor = preferenceStore.getInt("sub_color_border", Color.Black.toArgb())
  val borderStyle = preferenceStore.getEnum("sub_border_style", SubtitlesBorderStyle.OutlineAndShadow)
  val outlineEnabled = preferenceStore.getBoolean("sub_outline_enabled", true)
  val shadowEnabled = preferenceStore.getBoolean("sub_shadow_enabled", true)

  // Default shadow: 50% black, equivalent to mpv-lazy's "#000000/0.5".
  val shadowColor = preferenceStore.getInt("sub_color_shadow", 0x80000000.toInt())
  val shadowOffset = preferenceStore.getFloat("sub_shadow_offset", 1.5f)
  val backgroundColor = preferenceStore.getInt("sub_color_bg", Color.Transparent.toArgb())

  val justification = preferenceStore.getEnum("sub_justify", SubtitleJustification.Auto)
  val subPos = preferenceStore.getInt("sub_pos", 100)

  val overrideAssSubs = preferenceStore.getBoolean("sub_override_ass")

  // External (SRT & friends) subtitle behavior, mirroring the mpv-lazy
  // portable_config: search subtitle subdirectories, decode GBK-era files,
  // fall back to any full track, scale signs together with dialogue and keep
  // plain-text subtitles out of the black bars.
  val filePaths = preferenceStore.getString("sub_file_paths", "sub;Subs;subtitles")
  val codepage = preferenceStore.getString("sub_codepage", "GB18030")
  val fallback = preferenceStore.getBoolean("subs_fallback", true)
  val scaleSigns = preferenceStore.getBoolean("sub_scale_signs", true)
  val useMargins = preferenceStore.getBoolean("sub_use_margins", false)
  val hdrPeak = preferenceStore.getString("sub_hdr_peak", "100")
  val assStyleOverrides = preferenceStore.getString("sub_ass_style_overrides")

  val defaultSubDelay = preferenceStore.getInt("sub_default_delay")
  val defaultSubSpeed = preferenceStore.getFloat("sub_default_speed", 1f)
  val defaultSecondarySubDelay = preferenceStore.getInt("sub_default_secondary_delay")
}

enum class SubtitleJustification(
  val value: String,
  val icon: ImageVector,
  @param:StringRes val titleRes: Int,
) {
  Left("left", Icons.AutoMirrored.Default.FormatAlignLeft, R.string.subtitle_align_left),
  Center("center", Icons.Default.FormatAlignCenter, R.string.subtitle_align_center),
  Right("right", Icons.AutoMirrored.Default.FormatAlignRight, R.string.subtitle_align_right),
  Auto("auto", Icons.Default.FormatAlignJustify, R.string.subtitle_align_auto)
}
