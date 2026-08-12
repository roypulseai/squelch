package com.squelch.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ContactEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        RoomEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun rooms(): RoomDao
    abstract fun settings(): SettingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "squelch.db"
                ).build().also { instance = it }
            }
    }
}
