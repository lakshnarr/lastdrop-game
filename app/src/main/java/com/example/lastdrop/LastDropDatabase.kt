package com.example.lastdrop

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlayerEntity::class, GameEntity::class, RollEventEntity::class],
    version = 1
)
abstract class LastDropDatabase : RoomDatabase() {
    abstract fun dao(): LastDropDao

    companion object {
        @Volatile private var INSTANCE: LastDropDatabase? = null

        fun getInstance(context: Context): LastDropDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LastDropDatabase::class.java,
                    "lastdrop.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
