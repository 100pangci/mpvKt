package live.mehiz.mpvkt.database.entities

import androidx.room.Entity

/**
 * One row per (font file, family name alias). A font file may expose several
 * names (localized family, English family, full name), each gets its own row so
 * ASS style lookups can hit any of them.
 */
@Entity(tableName = "FontEntity", primaryKeys = ["path", "family"])
data class FontEntity(
  val path: String,
  val family: String,
  val lastModified: Long,
  val size: Long,
  val source: String,
)
