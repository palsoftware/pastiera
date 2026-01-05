package it.palsoftware.pastiera.core.suggestions.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserWordEntity::class, NGramEntity::class], version = 1, exportSchema = false)
abstract class UserDictionaryDatabase : RoomDatabase() {
    abstract fun userDictionaryDao(): UserDictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: UserDictionaryDatabase? = null

        fun getDatabase(context: Context): UserDictionaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDictionaryDatabase::class.java,
                    "user_dictionary_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

