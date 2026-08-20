package com.squelch.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squelch.app.data.local.converter.Converters
import com.squelch.app.data.local.dao.ContactDao
import com.squelch.app.data.local.dao.ConversationDao
import com.squelch.app.data.local.dao.MessageDao
import com.squelch.app.data.local.dao.SettingDao
import com.squelch.app.data.local.entity.ContactEntity
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.data.local.entity.SettingEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ContactEntity::class,
        MessageEntity::class,
        ConversationEntity::class,
        SettingEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SquelchDatabase : RoomDatabase() {

    abstract fun contacts(): ContactDao
    abstract fun messages(): MessageDao
    abstract fun conversations(): ConversationDao
    abstract fun settings(): SettingDao

    companion object {
        private const val DB_NAME = "squelch.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN firebaseUid TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context, kDb: ByteArray): SquelchDatabase {
            require(kDb.isNotEmpty()) { "K_db must not be empty" }
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(kDb)
            return Room.databaseBuilder(
                context.applicationContext,
                SquelchDatabase::class.java,
                DB_NAME
            ).openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
