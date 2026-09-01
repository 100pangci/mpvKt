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

  @Query(
    """
    SELECT path, lastModified, size FROM FontEntity
    WHERE source = :source
    GROUP BY path
    """,
  )
  suspend fun metasForSource(source: String): List<FontMeta>

  @Query(
    """
    SELECT family FROM FontEntity
    WHERE rowid IN (SELECT MIN(rowid) FROM FontEntity GROUP BY path)
    """,
  )
  suspend fun primaryFamilies(): List<String>

  @Query("SELECT COUNT(DISTINCT path) FROM FontEntity WHERE source = :source")
  suspend fun countForSource(source: String): Int

  @Query("SELECT COUNT(DISTINCT path) FROM FontEntity")
  suspend fun countDistinctPaths(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(fonts: List<FontEntity>)

  @Query("DELETE FROM FontEntity WHERE path IN (:paths)")
  suspend fun deleteByPaths(paths: List<String>)

  @Query("DELETE FROM FontEntity WHERE source = :source")
  suspend fun clearSource(source: String)
}

data class FontMeta(
  val path: String,
  val lastModified: Long,
  val size: Long,
)
