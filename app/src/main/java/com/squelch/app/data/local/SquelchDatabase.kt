package com.squelch.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squelch.app.data.local.converter.Converters
import com.squelch.app.data.local.dao.BlockedDao
import com.squelch.app.data.local.dao.ContactDao
import com.squelch.app.data.local.dao.ConversationDao
import com.squelch.app.data.local.dao.GroupDao
import com.squelch.app.data.local.dao.MessageDao
import com.squelch.app.data.local.dao.SettingDao
import com.squelch.app.data.local.entity.BlockedEntity
import com.squelch.app.data.local.entity.ContactEntity
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.GroupEntity
import com.squelch.app.data.local.entity.GroupMemberEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.data.local.entity.SettingEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ContactEntity::class,
        MessageEntity::class,
        ConversationEntity::class,
        SettingEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        BlockedEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SquelchDatabase : RoomDatabase() {

    abstract fun contacts(): ContactDao
    abstract fun messages(): MessageDao
    abstract fun conversations(): ConversationDao
    abstract fun settings(): SettingDao
    abstract fun groups(): GroupDao
    abstract fun blocked(): BlockedDao

    companion object {
        private const val DB_NAME = "squelch.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN firebaseUid TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastMessagePreview` TEXT NOT NULL, `lastMessageTimestamp` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, `muted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `group_members` (`groupId` TEXT NOT NULL, `edPubHex` TEXT NOT NULL, `displayName` TEXT NOT NULL, `joinedAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`, `edPubHex`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_groupId` ON `group_members` (`groupId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `blocked` (`edPubHex` TEXT NOT NULL, `displayName` TEXT NOT NULL, `blockedAt` INTEGER NOT NULL, PRIMARY KEY(`edPubHex`))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `group_members` ADD COLUMN `role` INTEGER NOT NULL DEFAULT 0")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
