package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.preferences.preference.PreferenceStore

class AppPreferences(preferenceStore: PreferenceStore) {
  /**
   * Only set when the user explicitly opts out of the all-files-access
   * prompt. The old "prompted" flag fired once at dialog display time, so a
   * single dismissal (or a later revocation) silenced the request forever.
   */
  val storageAccessDontAsk = preferenceStore.getBoolean("storage_access_dont_ask", false)
}
