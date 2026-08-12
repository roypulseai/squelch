package com.squelch.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ContactEntity::class,
        ChatRoomEntity::class,
        MessageEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contacts(): ContactDao
    abstract fun chatRooms(): ChatRoomDao
    abstract fun messages(): MessageDao
    abstract fun settings(): SettingDao

    companion object {
        private const val DB_NAME = "squelch.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Build (or return) the [AppDatabase]. The caller is responsible for
         * having populated [Keyring] with a passphrase (32 bytes) before
         * calling this. The DB will throw a SQLCipher error if [kDb] is
         * empty because SQLCipher does not support an empty passphrase.
         */
        fun openOrCreate(context: Context, kDb: ByteArray): AppDatabase {
            require(kDb.isNotEmpty()) { "K_db must be populated before opening the SQLCipher database" }
            return instance ?: synchronized(this) {
                instance ?: build(context, kDb).also { instance = it }
            }
        }

        /**
         * Same as [openOrCreate] but using whatever passphrase the [Keyring]
         * currently holds. Returns null if the keyring is locked.
         */
        fun openOrNull(context: Context): AppDatabase? {
            val kDb = Keyring.kDbOrEmpty()
            return if (kDb.isEmpty()) null else openOrCreate(context, kDb)
        }

        /** Test-only: discard the singleton (next call will rebuild). */
        internal fun resetForTest() {
            synchronized(this) { instance = null }
        }

        private fun build(context: Context, kDb: ByteArray): AppDatabase {
            // SQLCipher's SupportOpenHelperFactory wraps the passphrase into
            // a SupportSQLiteOpenHelper that opens the SQLite file via the
            // cipher extension. The DB file lives at the standard location
            // getDatabasePath("squelch.db") so files survive uninstall/reinstall.
            val factory = SupportOpenHelperFactory(kDb)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            ).openHelperFactory(factory).build()
        }
    }
}
