package live.mehiz.mpvkt.ui.player.controls

internal data class HorizontalSeekCompletion(val target: Int?, val precise: Boolean, val resume: Boolean)

/** Tracks pause ownership so normal end, cancellation and disposal share one cleanup path. */
internal class HorizontalSeekSession {
  private val limiter = SeekbarSeekLimiter()
  private var wasPaused = true
  var isActive = false
    private set

  fun start(paused: Boolean) {
    limiter.finish(precise = false)
    wasPaused = paused
    isActive = true
  }

  fun update(position: Int, nowMillis: Long): Int? =
    if (isActive) limiter.update(position, nowMillis) else null

  fun finish(precise: Boolean, cancelled: Boolean = false): HorizontalSeekCompletion? {
    if (!isActive) return null
    isActive = false
    val target = limiter.finish(precise)
    return HorizontalSeekCompletion(if (cancelled) null else target, precise, resume = !wasPaused)
  }
}
