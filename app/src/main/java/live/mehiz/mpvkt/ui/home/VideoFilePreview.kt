package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.graphics.Bitmap
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
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Composable
internal fun VideoFilePreview(source: String, lastModified: Long?, length: Long?, enabled: Boolean = true) {
  val context = LocalContext.current.applicationContext
  val key = remember(source, lastModified, length) { VideoPreviewKey(source, lastModified, length) }
  var thumbnail by remember(key) { mutableStateOf(VideoPreviewLoader.cached(key)) }
  LaunchedEffect(key, enabled) {
    if (enabled && thumbnail == null) thumbnail = VideoPreviewLoader.load(context, key)
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
        contentDescription = null, // The adjacent file name already labels this row.
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop,
      )
    }
  }
}

// Including metadata prevents a replaced file from retaining its old preview.
private data class VideoPreviewKey(val source: String, val lastModified: Long?, val length: Long?)

private object VideoPreviewLoader {
  private const val WIDTH = 288
  private const val HEIGHT = 162
  private const val CACHE_BYTES = 12 * 1024 * 1024
  private val cache = object : LruCache<VideoPreviewKey, Bitmap>(CACHE_BYTES) {
    override fun sizeOf(key: VideoPreviewKey, value: Bitmap): Int = value.allocationByteCount
  }

  // Unsupported codecs should not be retried every time a row scrolls into view.
  private val failures = LruCache<VideoPreviewKey, Boolean>(128)
  private val decoders = Semaphore(1)

  fun cached(key: VideoPreviewKey): Bitmap? = cache.get(key)

  suspend fun load(context: Context, key: VideoPreviewKey): Bitmap? = withContext(Dispatchers.IO) {
    cache.get(key)?.let { return@withContext it }
    if (failures.get(key) == true) return@withContext null
    decoders.withPermit {
      coroutineContext.ensureActive()
      cache.get(key)?.let { return@withPermit it }
      if (failures.get(key) == true) return@withPermit null
      val bitmap = extractFrame(context, key.source)
      if (bitmap == null) {
        failures.put(key, true)
      } else {
        bitmap.prepareToDraw()
        cache.put(key, bitmap)
      }
      bitmap
    }
  }

  private fun extractFrame(context: Context, source: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
      if (setDataSource(retriever, context, source)) extractAndScaleFrame(retriever) else null
    } catch (_: Exception) {
      // Missing grants, deleted files and unsupported media keep the video icon.
      null
    } finally {
      // Always close the decoder/descriptor, including when setDataSource fails.
      runCatching { retriever.release() }
    }
  }

  private fun setDataSource(retriever: MediaMetadataRetriever, context: Context, source: String): Boolean {
    val uri = source.toUri()
    return when (uri.scheme) {
      null -> {
        retriever.setDataSource(source)
        true
      }

      "file" -> uri.path?.let {
        retriever.setDataSource(it)
        true
      } ?: false

      else -> {
        retriever.setDataSource(context, uri) // SAF content:// document URIs.
        true
      }
    }
  }

  private fun extractAndScaleFrame(retriever: MediaMetadataRetriever): Bitmap? {
    // Prefer a frame near the beginning; fall back for very short videos.
    val frame = frameAt(retriever, 1_000_000L) ?: frameAt(retriever, -1L)
    return frame?.let(::scaleFrame)
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

  private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT)
    } else {
      retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }
}
