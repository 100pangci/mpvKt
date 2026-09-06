package live.mehiz.mpvkt.ui.player

import dev.vivvvek.seeker.Segment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterNode(
  val time: Float,
  val title: String,
) {
  fun toSegment(): Segment = Segment(title, time)
}

/** One entry of mpv's native `playlist` property. */
@Serializable
data class PlaylistNode(
  val filename: String,
  val current: Boolean? = null,
  val playing: Boolean? = null,
  val title: String? = null,
  val id: Long? = null,
) {
  val isCurrent = current == true
  val isPlaying = playing == true

  /**
   * Human-readable file name for the queue sheet. mpv's `filename` is the
   * raw string loadfile received: local real paths, SAF content URIs whose
   * document path is percent-encoded ("primary%3AFolder%2Fmovie.mp4"),
   * fd:// handles and network URLs with embedded credentials. Percent signs
   * are decoded (without ever turning "+" into a space, so file names keep
   * their literal plus signs) and everything up to the last path separator
   * is dropped, which also folds encoded separators back into plain slashes.
   */
  val displayName: String
    get() {
      title?.takeIf { it.isNotBlank() }?.let { return it }
      val path = filename.substringBefore('?').substringBefore('#')
      val lastSegment = path.substringAfterLast('/')
      val decoded = percentDecode(lastSegment)
      val name = if (decoded != lastSegment) decoded.substringAfterLast('/') else lastSegment
      return name.takeIf { it.isNotBlank() } ?: filename
    }
}

/** Decodes %XX escapes only; "+" and invalid escapes pass through untouched. */
private fun percentDecode(value: String): String {
  val bytes = java.io.ByteArrayOutputStream(value.length)
  var i = 0
  while (i < value.length) {
    val c = value[i]
    if (c == '%' && i + 2 < value.length) {
      val hi = value[i + 1].digitToIntOrNull(16)
      val lo = value[i + 2].digitToIntOrNull(16)
      if (hi != null && lo != null) {
        bytes.write(hi * 16 + lo)
        i += 3
        continue
      }
    }
    bytes.write(c.toString().toByteArray(Charsets.UTF_8))
    i += 1
  }
  return bytes.toString(Charsets.UTF_8.name())
}

@Serializable
data class TrackNode(
  val id: Int,
  val type: String,
  @SerialName("src-id") val srcId: Long? = null,
  val title: String? = null,
  val lang: String? = null,
  val image: Boolean? = null,
  @SerialName("albumArt") val albumArt: Boolean? = null,
  val default: Boolean? = null,
  val forced: Boolean? = null,
  val dependent: Boolean? = null,
  @SerialName("visual-impaired") val visualImpaired: Boolean? = null,
  @SerialName("hearing-impaired") val hearingImpaired: Boolean? = null,
  @SerialName("hls-bitrate") val hlsBitrate: Long? = null,
  @SerialName("program-id") val programId: Long? = null,
  val selected: Boolean? = null,
  @SerialName("main-selection") val mainSelection: Long? = null,
  val external: Boolean? = null,
  @SerialName("external-filename") val externalFilename: String? = null,
  val codec: String? = null,
  @SerialName("codec-desc") val codecDesc: String? = null,
  @SerialName("codec-profile") val codecProfile: String? = null,
  @SerialName("ff-index") val ffIndex: Long? = null,
  val decoder: String? = null,
  @SerialName("decoder-desc") val decoderDesc: String? = null,
  @SerialName("demux-w") val demuxW: Long? = null,
  @SerialName("demux-h") val demuxH: Long? = null,
  @SerialName("demux-crop-x") val demuxCropX: Long? = null,
  @SerialName("demux-crop-y") val demuxCropY: Long? = null,
  @SerialName("demux-crop-w") val demuxCropW: Long? = null,
  @SerialName("demux-crop-h") val demuxCropH: Long? = null,
  @SerialName("demux-channel-count") val demuxChannelCount: Long? = null,
  @SerialName("demux-channels") val demuxChannels: String? = null,
  @SerialName("demux-samplerate") val demuxSampleRate: Long? = null,
  @SerialName("demux-fps") val demuxFps: Double? = null,
  @SerialName("demux-bitrate") val demuxBitrate: Long? = null,
  @SerialName("demux-rotation") val demuxRotation: Long? = null,
  @SerialName("demux-par") val demuxPar: Double? = null,
  @SerialName("format-name") val formatName: String? = null,
  @SerialName("audio-channels") val audioChannels: Long? = null,
  @SerialName("replaygain-track-peak") val replayGainTrackPeak: Double? = null,
  @SerialName("replaygain-track-gain") val replayGainTrackGain: Double? = null,
  @SerialName("replaygain-album-peak") val replayGainAlbumPeak: Double? = null,
  @SerialName("replaygain-album-gain") val replayGainAlbumGain: Double? = null,
  @SerialName("dolby-vision-profile") val dolbyVisionProfile: Long? = null,
  @SerialName("dolby-vision-level") val dolbyVisionLevel: Long? = null,
  val metadata: Map<String, String?>? = null
) {
  val isVideo = type == "video"
  val isAudio = type == "audio"
  val isSubtitle = type == "sub"
  val isSelected = selected == true

  fun getMetadata(key: String): String? = metadata?.get(key)
  fun hasMetadata(): Boolean = !metadata.isNullOrEmpty()
}
