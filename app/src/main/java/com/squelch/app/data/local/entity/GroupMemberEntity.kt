package com.squelch.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "edPubHex"],
    foreignKeys = [ForeignKey(
        entity = GroupEntity::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("groupId")]
)
data class GroupMemberEntity(
    val groupId: String,
    val edPubHex: String,
    val displayName: String = "",
    val role: Int = 0,
    val joinedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_MEMBER = 0
        const val ROLE_ADMIN = 1
    }
}
