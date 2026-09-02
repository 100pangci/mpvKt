package live.mehiz.mpvkt.database

import androidx.room.Database
import androidx.room.RoomDatabase
import live.mehiz.mpvkt.database.dao.CustomButtonDao
import live.mehiz.mpvkt.database.dao.FontDao
import live.mehiz.mpvkt.database.dao.PlaybackStateDao
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.database.entities.FontEntity
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity

@Database(entities = [PlaybackStateEntity::class, CustomButtonEntity::class, FontEntity::class], version = 9)
abstract class MpvKtDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao
  abstract fun customButtonDao(): CustomButtonDao
  abstract fun fontDao(): FontDao
}
