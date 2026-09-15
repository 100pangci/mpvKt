package live.mehiz.mpvkt.network

/** A single HTTP byte range, inclusive at both ends. */
data class HttpRange(val start: Long, val endInclusive: Long) {
  val length: Long get() = endInclusive - start + 1
}

/**
 * The outcome of parsing an HTTP `Range` header. ffmpeg/mpv only ever ask
 * for one byte range; multi-range requests, unknown units and malformed
 * numbers fall back to [Full], which is always a valid response.
 */
sealed interface RangeSpec {
  data object Full : RangeSpec
  data class Partial(val range: HttpRange) : RangeSpec
  data object Unsatisfiable : RangeSpec

  companion object {
    fun parse(header: String?, size: Long): RangeSpec {
      val spec = header?.trim()
        ?.takeIf { it.startsWith("bytes=", ignoreCase = true) && ',' !in it }
        ?.substringAfter('=')
        ?.trim()
      val dash = spec?.indexOf('-') ?: -1
      if (spec == null || dash < 0) return Full
      val startText = spec.substring(0, dash).trim()
      val endText = spec.substring(dash + 1).trim()
      return if (startText.isEmpty()) suffixSpec(endText, size) else startSpec(startText, endText, size)
    }

    private fun suffixSpec(endText: String, size: Long): RangeSpec {
      val suffix = endText.toLongOrNull() ?: return Full
      return if (suffix <= 0 || size == 0L) {
        Unsatisfiable
      } else {
        Partial(HttpRange((size - suffix).coerceAtLeast(0), size - 1))
      }
    }

    private fun startSpec(startText: String, endText: String, size: Long): RangeSpec {
      val start = startText.toLongOrNull() ?: return Full
      return when {
        start < 0 || start >= size -> Unsatisfiable
        endText.isEmpty() -> Partial(HttpRange(start, size - 1))
        else -> endSpec(start, endText, size)
      }
    }

    private fun endSpec(start: Long, endText: String, size: Long): RangeSpec {
      val end = endText.toLongOrNull() ?: return Full
      return if (end < start) Unsatisfiable else Partial(HttpRange(start, minOf(end, size - 1)))
    }
  }
}
