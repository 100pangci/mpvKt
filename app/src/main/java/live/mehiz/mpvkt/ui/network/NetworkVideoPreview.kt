package live.mehiz.mpvkt.ui.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.network.NetworkSource
import live.mehiz.mpvkt.network.RemoteClientFactory
import live.mehiz.mpvkt.ui.home.fitVideoPreviewSize
import org.koin.compose.koinInject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Composable
internal fun NetworkVideoPreview(
  source: NetworkSource,
  path: String,
  size: Long,
  lastModified: Long?,
) {
  val context = LocalContext.current.applicationContext
  val clientFactory = koinInject<RemoteClientFactory>()
  val key = remember(
    source.id,
    source.host,
    source.port,
    source.basePath,
    source.smbDialect,
    path,
    size,
    lastModified,
  ) {
    NetworkPreviewKey(
      sourceId = source.id,
      host = source.host,
      port = source.port,
      basePath = source.basePath,
      dialect = source.smbDialect.name,
      path = path,
      size = size,
      lastModified = lastModified,
    )
  }
  var thumbnail by remember(key) { mutableStateOf(NetworkPreviewLoader.cached(key)) }
  LaunchedEffect(key) {
    if (thumbnail == null) {
      thumbnail = NetworkPreviewLoader.load(context, key) {
        clientFactory.create(source).use { it.fileUrl(path) }
      }
    }
  }

  Box(
    modifier = Modifier
      .size(width = 96.dp, height = 54.dp)
      .clip(MaterialTheme.shapes.small)
      .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center,
  ) {
    val bitmap = thumbnail
    if (bitmap == null) {
      Icon(Icons.Default.Movie, contentDescription = null)
    } else {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop,
      )
    }
  }
}

private data class NetworkPreviewKey(
  val sourceId: Long,
  val host: String,
  val port: Int,
  val basePath: String,
  val dialect: String,
  val path: String,
  val size: Long,
  val lastModified: Long?,
) {
  fun cacheToken(): String = listOf(
    sourceId.toString(),
    host,
    port.toString(),
    basePath,
    dialect,
    path,
    size.toString(),
    lastModified?.toString().orEmpty(),
  ).joinToString("\u0000")
}

private object NetworkPreviewLoader {
  private const val WIDTH = 288
  private const val HEIGHT = 162
  private const val MEMORY_CACHE_BYTES = 12 * 1024 * 1024
  private const val DISK_CACHE_BYTES = 64L * 1024 * 1024
  private const val FAILURE_RETRY_MILLIS = 60_000L
  private const val DISK_CACHE_DIRECTORY = "network-video-previews"

  private val cache = object : LruCache<NetworkPreviewKey, Bitmap>(MEMORY_CACHE_BYTES) {
    override fun sizeOf(key: NetworkPreviewKey, value: Bitmap): Int = value.allocationByteCount
  }
  private val failures = object : LruCache<NetworkPreviewKey, Long>(128) {}
  private val decoder = Semaphore(1)
  private val diskLock = Any()

  fun cached(key: NetworkPreviewKey): Bitmap? = cache.get(key)

  suspend fun load(context: Context, key: NetworkPreviewKey, sourceUrl: () -> String): Bitmap? =
    withContext(Dispatchers.IO) {
      cache.get(key)?.let { return@withContext it }
      readDiskCache(context, key)?.let { bitmap ->
        cache.put(key, bitmap)
        return@withContext bitmap
      }

      decoder.withPermit {
        cache.get(key)?.let { return@withPermit it }
        readDiskCache(context, key)?.let { bitmap ->
          cache.put(key, bitmap)
          return@withPermit bitmap
        }
        if (isFailureCached(key)) return@withPermit null
        val bitmap = try {
          extractFrame(sourceUrl())
        } catch (error: CancellationException) {
          throw error
        } catch (_: Exception) {
          null
        }
        if (bitmap == null) {
          failures.put(key, System.currentTimeMillis())
        } else {
          failures.remove(key)
          bitmap.prepareToDraw()
          cache.put(key, bitmap)
          writeDiskCache(context, key, bitmap)
        }
        bitmap
      }
    }

  private fun isFailureCached(key: NetworkPreviewKey): Boolean {
    val failedAt = failures.get(key) ?: return false
    val isCached = System.currentTimeMillis() - failedAt < FAILURE_RETRY_MILLIS
    if (!isCached) failures.remove(key)
    return isCached
  }

  private fun extractFrame(source: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(source)
      val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        retriever.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT)
          ?: retriever.getScaledFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT)
      } else {
        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          ?: retriever.getFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      }
      frame?.let(::scaleFrame)
    } finally {
      runCatching { retriever.release() }
    }
  }

  private fun scaleFrame(frame: Bitmap): Bitmap {
    val (width, height) = fitVideoPreviewSize(frame.width, frame.height, WIDTH, HEIGHT)
    if (width == frame.width && height == frame.height) return frame
    return try {
      Bitmap.createScaledBitmap(frame, width, height, true)
    } finally {
      frame.recycle()
    }
  }

  private fun readDiskCache(context: Context, key: NetworkPreviewKey): Bitmap? = synchronized(diskLock) {
    val file = cacheFile(context, key)
    if (!file.isFile) return@synchronized null
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    if (bitmap == null) {
      file.delete()
    } else {
      file.setLastModified(System.currentTimeMillis())
    }
    bitmap
  }

  private fun writeDiskCache(context: Context, key: NetworkPreviewKey, bitmap: Bitmap) = synchronized(diskLock) {
    val directory = File(context.cacheDir, DISK_CACHE_DIRECTORY)
    if (!directory.exists() && !directory.mkdirs()) return@synchronized
    val destination = cacheFile(context, key)
    val temporary = runCatching { File.createTempFile("preview-", ".tmp", directory) }.getOrNull()
      ?: return@synchronized
    try {
      val compressed = FileOutputStream(temporary).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it) }
      if (!compressed) return@synchronized
      destination.delete()
      if (!temporary.renameTo(destination)) return@synchronized
      pruneDiskCache(directory, destination)
    } finally {
      temporary.delete()
    }
  }

  private fun cacheFile(context: Context, key: NetworkPreviewKey): File {
    val directory = File(context.cacheDir, DISK_CACHE_DIRECTORY)
    val digest = MessageDigest.getInstance("SHA-256")
      .digest(key.cacheToken().toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return File(directory, "$digest.jpg")
  }

  private fun pruneDiskCache(directory: File, newest: File) {
    val files = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
    var totalBytes = files.sumOf { it.length() }
    for (file in files) {
      if (totalBytes <= DISK_CACHE_BYTES) break
      if (file != newest) {
        val size = file.length()
        if (file.delete()) totalBytes -= size
      }
    }
  }
}
