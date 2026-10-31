package app.passage.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.passage.core.database.dao.SecretDao
import app.passage.core.database.entity.SecretEntity

@Database(entities = [SecretEntity::class], version = 1, exportSchema = false)
abstract class PassageDatabase : RoomDatabase() {

    abstract fun secretDao(): SecretDao

    companion object {
        @Volatile
        private var INSTANCE: PassageDatabase? = null

        fun getInstance(context: Context): PassageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PassageDatabase::class.java,
                    "passage_metadata.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
