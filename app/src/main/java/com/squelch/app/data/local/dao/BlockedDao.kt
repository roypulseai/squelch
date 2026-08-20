package com.squelch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.squelch.app.data.local.entity.BlockedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDao {
    @Query("SELECT * FROM blocked")
    fun observeAll(): Flow<List<BlockedEntity>>

    @Query("SELECT * FROM blocked WHERE edPubHex = :edPubHex")
    suspend fun get(edPubHex: String): BlockedEntity?

    @Query("SELECT edPubHex FROM blocked")
    suspend fun allPubkeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun block(entity: BlockedEntity)

    @Query("DELETE FROM blocked WHERE edPubHex = :edPubHex")
    suspend fun unblock(edPubHex: String)

    @Query("SELECT COUNT(*) FROM blocked")
    suspend fun count(): Int
}
