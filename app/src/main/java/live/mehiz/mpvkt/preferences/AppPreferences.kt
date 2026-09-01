package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.preferences.preference.PreferenceStore

class AppPreferences(preferenceStore: PreferenceStore) {
  val storageAccessPrompted = preferenceStore.getBoolean("storage_access_prompted", false)
}
