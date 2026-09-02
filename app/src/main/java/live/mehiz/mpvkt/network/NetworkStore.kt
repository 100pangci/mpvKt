package live.mehiz.mpvkt.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import live.mehiz.mpvkt.preferences.NetworkPreferences

/**
 * Persists the list of saved network servers in the shared preferences.
 * Passwords live in the serialized instances, Base64-obfuscated (see
 * [NetworkSource]); this is deliberate obfuscation, not encryption.
 */
class NetworkStore(
  private val preferences: NetworkPreferences,
  private val json: Json,
) {
  fun sources(): Flow<List<NetworkSource>> = preferences.sources.changes().map { decode(it) }

  suspend fun add(source: NetworkSource) {
    val current = decode(preferences.sources.get())
    preferences.sources.set(json.encodeToString(current + source))
  }

  suspend fun remove(id: Long) {
    val current = decode(preferences.sources.get())
    preferences.sources.set(json.encodeToString(current.filterNot { it.id == id }))
  }

  private fun decode(raw: String): List<NetworkSource> =
    runCatching { json.decodeFromString<List<NetworkSource>>(raw) }.getOrDefault(emptyList())
}
