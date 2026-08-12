package com.squelch.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE pubkey = :pubkey")
    suspend fun get(pubkey: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE xPub = :xPub")
    suspend fun getByXPub(xPub: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, title: String, updatedAt: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE msgId = :msgId LIMIT 1")
    suspend fun getByMsgId(msgId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET delivery = :delivery WHERE msgId = :msgId")
    suspend fun updateDelivery(msgId: String, delivery: Int)

    @Query("DELETE FROM messages WHERE timestamp < :before")
    suspend fun purgeBefore(before: Long)

    @Query("SELECT * FROM messages ORDER BY id DESC LIMIT 1")
    suspend fun lastMessage(): MessageEntity?
}

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY name ASC")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun get(id: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(room: RoomEntity)

    @Query("UPDATE rooms SET joined = :joined WHERE id = :id")
    suspend fun setJoined(id: String, joined: Boolean)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}
