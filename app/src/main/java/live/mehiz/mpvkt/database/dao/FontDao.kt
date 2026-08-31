package live.mehiz.mpvkt.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import live.mehiz.mpvkt.database.entities.FontEntity

@Dao
interface FontDao {
  @Query(
    """
    SELECT * FROM FontEntity
    WHERE family = :name COLLATE NOCASE
    """,
  )
  suspend fun findByName(name: String): List<FontEntity>

  @Query("SELECT * FROM FontEntity WHERE path = :path")
  suspend fun findByPath(path: String): List<FontEntity>

  @Query("SELECT DISTINCT path FROM FontEntity WHERE source = :source")
  suspend fun pathsForSource(source: String): List<String>

  @Query("SELECT COUNT(DISTINCT path) FROM FontEntity WHERE source = :source")
  suspend fun countForSource(source: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(fonts: List<FontEntity>)

  @Query("DELETE FROM FontEntity WHERE path = :path")
  suspend fun deleteByPath(path: String)

  @Query("DELETE FROM FontEntity WHERE source = :source")
  suspend fun clearSource(source: String)
}
