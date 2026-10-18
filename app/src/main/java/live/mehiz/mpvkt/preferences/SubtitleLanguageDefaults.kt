package live.mehiz.mpvkt.preferences

import java.util.Locale

/**
 * Default value for mpv's `slang` option (preferred subtitle languages),
 * chosen from the system language on first run. The codes are matched by mpv
 * against subtitle track tags and are tried in order.
 *
 * Extend or improve this table via pull requests — every language is welcome.
 */
val defaultPreferredSubtitleLanguages: String
  get() = when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
    "zh" -> "chs,sc,zh-cn,chi,zh,zh-tw,cht,zho,ze"
    else -> ""
  }
