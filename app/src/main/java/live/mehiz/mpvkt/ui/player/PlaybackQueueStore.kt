package live.mehiz.mpvkt.ui.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

@Serializable
internal data class PlaybackQueueRequest(
  val paths: List<String>,
  val subtitles: Map<String, List<String>> = emptyMap(),
)

/** Private disk handoff: only a random token, never an entire playlist, crosses Binder. */
internal class PlaybackQueueStore(private val directory: File) {
  private val json = Json { ignoreUnknownKeys = true }

  fun save(request: PlaybackQueueRequest): String {
    require(request.paths.isNotEmpty())
    ensureDirectory()
    val token = UUID.randomUUID().toString()
    val target = File(directory, "$token.json")
    val temporary = File.createTempFile("queue-", ".tmp", directory)
    try {
      writeRequest(request, temporary, target)
    } finally {
      temporary.delete()
    }
    // Keep requests for task/process restoration, but don't retain them indefinitely.
    val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
    directory.listFiles()?.filter { it.name.endsWith(".json") && it.lastModified() < cutoff }
      ?.forEach { it.delete() }
    return token
  }

  fun load(token: String): PlaybackQueueRequest? {
    // PlayerActivity is exported. Never accept a caller-controlled file path.
    if (!TOKEN.matches(token)) return null
    val file = File(directory, "$token.json")
    return runCatching {
      if (!file.isFile || file.length() > MAX_REQUEST_BYTES) {
        null
      } else {
        json.decodeFromString<PlaybackQueueRequest>(file.readText(Charsets.UTF_8))
          .takeIf { it.paths.isNotEmpty() }
      }
    }.getOrNull()
  }

  private fun ensureDirectory() {
    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
      throw IOException("Cannot create queue store")
    }
  }

  private fun writeRequest(request: PlaybackQueueRequest, temporary: File, target: File) {
    temporary.writeText(json.encodeToString(request), Charsets.UTF_8)
    if (temporary.length() > MAX_REQUEST_BYTES) {
      throw IOException("Queue request exceeds storage limit")
    }
    if (!temporary.renameTo(target)) {
      throw IOException("Cannot save queue request")
    }
  }

  companion object {
    const val DIRECTORY = "playback-queue-requests"
    private const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_REQUEST_BYTES = 64L * 1024 * 1024
    private val TOKEN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
  }
}
