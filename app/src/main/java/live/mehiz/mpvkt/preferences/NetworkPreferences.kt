package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.preferences.preference.PreferenceStore

class NetworkPreferences(preferenceStore: PreferenceStore) {
  /**
   * Serialized List<NetworkSource>. Passwords inside are Base64-obfuscated
   * only — the storage itself is plain shared preferences.
   */
  val sources = preferenceStore.getString("network_sources", "[]")
}
