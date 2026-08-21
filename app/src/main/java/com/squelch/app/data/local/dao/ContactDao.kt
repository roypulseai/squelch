package com.squelch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.squelch.app.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE pubkey = :pubkey")
    suspend fun get(pubkey: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: ContactEntity)

    @Query("DELETE FROM contacts WHERE pubkey = :pubkey")
    suspend fun delete(pubkey: String)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int

    @Query("SELECT pubkey FROM contacts")
    suspend fun pubkeys(): List<String>

    @Query("SELECT firebaseUid FROM contacts WHERE firebaseUid != ''")
    suspend fun firebaseUids(): List<String>

    @Query("SELECT * FROM contacts")
    suspend fun getAll(): List<ContactEntity>
}
