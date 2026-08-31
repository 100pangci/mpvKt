package live.mehiz.mpvkt.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migrations: Array<Migration> = arrayOf(
  MIGRATION1to2,
  MIGRATION2to3,
  MIGRATION3to4,
  MIGRATION4to5,
  MIGRATION5to6,
  MIGRATION6to7,
)

private object MIGRATION1to2 : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    listOf("subDelay", "secondarySubDelay", "audioDelay").forEach {
      db.execSQL("ALTER TABLE PlaybackStateEntity ADD COLUMN $it INTEGER NOT NULL DEFAULT 0")
    }
    db.execSQL("ALTER TABLE PlaybackStateEntity ADD COLUMN subSpeed REAL NOT NULL DEFAULT 0")
  }
}

private object MIGRATION2to3 : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE PlaybackStateEntity ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 0")
  }
}

private object MIGRATION3to4 : Migration(3, 4) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `CustomButtonEntity` (
        `id` INTEGER NOT NULL,
        `title` TEXT NOT NULL, 
        `content` TEXT NOT NULL, 
        `index` INTEGER NOT NULL, 
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
  }
}

private object MIGRATION4to5 : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
        ALTER TABLE CustomButtonEntity ADD COLUMN longPressContent TEXT NOT NULL DEFAULT ''
      """.trimIndent()
    )
  }
}

private object MIGRATION5to6 : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `FontEntity` (
        `path` TEXT NOT NULL,
        `family` TEXT NOT NULL,
        `lastModified` INTEGER NOT NULL,
        `size` INTEGER NOT NULL,
        `source` TEXT NOT NULL,
        PRIMARY KEY(`path`, `family`)
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_FontEntity_family` ON `FontEntity` (`family`)")
  }
}

private object MIGRATION6to7 : Migration(6, 7) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_FontEntity_family` ON `FontEntity` (`family`)")
  }
}
