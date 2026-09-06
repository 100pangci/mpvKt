package live.mehiz.mpvkt.domain.playbackstate.repository

import kotlinx.coroutines.flow.Flow
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity

interface PlaybackStateRepository {

  suspend fun upsert(playbackState: PlaybackStateEntity)

  suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity?

  fun getAllPlaybackStates(): Flow<List<PlaybackStateEntity>>

  suspend fun deletePlaybackState(mediaTitle: String)

  suspend fun clearAllPlaybackStates()
}
