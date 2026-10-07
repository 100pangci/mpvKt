package live.mehiz.mpvkt.ui.home

import kotlin.math.roundToInt

internal fun fitVideoPreviewSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
  require(width > 0 && height > 0 && maxWidth > 0 && maxHeight > 0)
  val scale = minOf(1.0, maxWidth.toDouble() / width, maxHeight.toDouble() / height)
  return (width * scale).roundToInt().coerceIn(1, maxWidth) to
    (height * scale).roundToInt().coerceIn(1, maxHeight)
}
