package com.squelch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.squelch.app.data.local.entity.GroupEntity
import com.squelch.app.data.local.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY lastMessageTimestamp DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun get(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun observeGroup(groupId: String): Flow<GroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity)

    @Query("UPDATE groups SET name = :name WHERE id = :groupId")
    suspend fun updateName(groupId: String, name: String)

    @Query("UPDATE groups SET muted = :muted WHERE id = :groupId")
    suspend fun setMuted(groupId: String, muted: Boolean)

    @Query("UPDATE groups SET lastMessagePreview = :preview, lastMessageTimestamp = :timestamp WHERE id = :groupId")
    suspend fun updateLastMessage(groupId: String, preview: String, timestamp: Long)

    @Query("UPDATE groups SET unreadCount = unreadCount + 1 WHERE id = :groupId")
    suspend fun incrementUnread(groupId: String)

    @Query("UPDATE groups SET unreadCount = 0 WHERE id = :groupId")
    suspend fun clearUnread(groupId: String)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun delete(groupId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: GroupMemberEntity)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY joinedAt ASC")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE edPubHex = :edPubHex")
    suspend fun getGroupsFor(edPubHex: String): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY joinedAt ASC")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun removeMembers(groupId: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND edPubHex = :edPubHex")
    suspend fun removeMember(groupId: String, edPubHex: String)

    @Query("UPDATE group_members SET role = :role WHERE groupId = :groupId AND edPubHex = :edPubHex")
    suspend fun setRole(groupId: String, edPubHex: String, role: Int)
}
