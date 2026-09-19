package com.goldmedal.aillm.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val hasImage: Boolean = false,
    val imagePath: String? = null,
    val isStreaming: Boolean = false
)
