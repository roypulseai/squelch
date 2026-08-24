package com.squelch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.squelch.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun lastN(conversationId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE msgId = :msgId LIMIT 1")
    suspend fun getByMsgId(msgId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MessageEntity): Long

    @Query("UPDATE messages SET delivery = :delivery WHERE msgId = :msgId")
    suspend fun updateDelivery(msgId: String, delivery: Int)

    @Query("UPDATE messages SET readAt = :readAt WHERE msgId = :msgId")
    suspend fun markRead(msgId: String, readAt: Long)

    @Query("UPDATE messages SET readAt = :readAt WHERE conversationId = :conversationId AND readAt = 0")
    suspend fun markAllRead(conversationId: String, readAt: Long)

    @Query("DELETE FROM messages WHERE msgId = :msgId")
    suspend fun delete(msgId: String): Int

    @Query("UPDATE messages SET body = :body WHERE msgId = :msgId")
    suspend fun updateBody(msgId: String, body: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun purgeForConversation(conversationId: String)
}
