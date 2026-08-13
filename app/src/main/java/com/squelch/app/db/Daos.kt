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

    @Query("SELECT * FROM contacts WHERE xPub = :xPub LIMIT 1")
    suspend fun getByXPub(xPub: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: ContactEntity)

    @Query("DELETE FROM contacts WHERE pubkey = :pubkey")
    suspend fun delete(pubkey: String)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int

    /** All pubkeys currently in the local contact list. Cheap because
     *  it's a single-column projection without allocations. Used by the
     *  vault-contact restore flow. */
    @Query("SELECT pubkey FROM contacts")
    suspend fun pubkeys(): List<String>
}

@Dao
interface ChatRoomDao {

    @Query("SELECT * FROM chat_rooms ORDER BY lastReadAt DESC")
    fun observeAll(): Flow<List<ChatRoomEntity>>

    @Query("SELECT * FROM chat_rooms WHERE id = :id")
    suspend fun get(id: String): ChatRoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ChatRoomEntity)

    @Query("UPDATE chat_rooms SET joined = :joined WHERE id = :id")
    suspend fun setJoined(id: String, joined: Boolean)

    @Query("UPDATE chat_rooms SET lastReadAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query("DELETE FROM chat_rooms WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun lastN(conversationId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE msgId = :msgId LIMIT 1")
    suspend fun getByMsgId(msgId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MessageEntity): Long

    @Query("UPDATE messages SET delivery = :delivery WHERE msgId = :msgId")
    suspend fun updateDelivery(msgId: String, delivery: Int)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun purgeForConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE timestamp < :before")
    suspend fun purgeOlderThan(before: Long)
}

@Dao
interface SettingDao {

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)
}
