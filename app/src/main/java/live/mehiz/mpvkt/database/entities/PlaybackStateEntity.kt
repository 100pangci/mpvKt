package live.mehiz.mpvkt.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaybackStateEntity(
  @PrimaryKey val mediaTitle: String,
  val lastPosition: Int, // in seconds
  val playbackSpeed: Double,
  val sid: Int,
  val subDelay: Int,
  val subSpeed: Double,
  val secondarySid: Int,
  val secondarySubDelay: Int,
  val aid: Int,
  val audioDelay: Int,
  val duration: Int = 0, // in seconds, feeds the watch history progress bar
  val lastPlayedAt: Long = 0, // epoch millis, orders the watch history
  val uri: String = "", // source the media was last opened from, lets history resume playback
)
