package live.mehiz.mpvkt.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity

@Dao
interface PlaybackStateDao {
  @Upsert
  suspend fun upsert(playbackStateEntity: PlaybackStateEntity)

  @Query("SELECT * FROM PlaybackStateEntity WHERE mediaTitle = :mediaTitle LIMIT 1")
  suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity?

  @Query("SELECT * FROM PlaybackStateEntity ORDER BY lastPlayedAt DESC")
  fun getAllPlaybackStates(): Flow<List<PlaybackStateEntity>>

  @Query("DELETE FROM PlaybackStateEntity WHERE mediaTitle = :mediaTitle")
  suspend fun deletePlaybackState(mediaTitle: String)

  @Query("DELETE FROM PlaybackStateEntity")
  suspend fun clearAllPlaybackStates()
}
