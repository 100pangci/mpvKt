package live.mehiz.mpvkt.ui.player.controls

/** Coalesces drag previews while preserving the latest release target. Uses a monotonic clock. */
internal class SeekbarSeekLimiter(private val intervalMillis: Long = 150L) {
  private var latestPosition: Int? = null
  private var lastSentPosition: Int? = null
  private var lastSentAt: Long? = null

  init {
    require(intervalMillis >= 0)
  }

  fun update(position: Int, nowMillis: Long): Int? {
    latestPosition = position
    if (position == lastSentPosition) return null
    val sentAt = lastSentAt
    if (sentAt != null && nowMillis - sentAt < intervalMillis) return null
    lastSentAt = nowMillis
    lastSentPosition = position
    return position
  }

  fun finish(precise: Boolean): Int? {
    // A keyframe preview already at the final target needs no duplicate seek.
    // Exact mode still refines that preview once, and only once, on release.
    val target = latestPosition?.takeIf { precise || it != lastSentPosition }
    latestPosition = null
    lastSentPosition = null
    lastSentAt = null
    return target
  }
}
