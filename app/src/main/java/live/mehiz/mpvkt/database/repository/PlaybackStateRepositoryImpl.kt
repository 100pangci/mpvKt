package live.mehiz.mpvkt.database.repository

import kotlinx.coroutines.flow.Flow
import live.mehiz.mpvkt.database.MpvKtDatabase
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.domain.playbackstate.repository.PlaybackStateRepository

class PlaybackStateRepositoryImpl(
  private val database: MpvKtDatabase
) : PlaybackStateRepository {
  override suspend fun upsert(playbackState: PlaybackStateEntity) {
    database.videoDataDao().upsert(playbackState)
  }

  override suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity? {
    return database.videoDataDao().getVideoDataByTitle(mediaTitle)
  }

  override fun getAllPlaybackStates(): Flow<List<PlaybackStateEntity>> {
    return database.videoDataDao().getAllPlaybackStates()
  }

  override suspend fun deletePlaybackState(mediaTitle: String) {
    database.videoDataDao().deletePlaybackState(mediaTitle)
  }

  override suspend fun clearAllPlaybackStates() {
    database.videoDataDao().clearAllPlaybackStates()
  }
}
