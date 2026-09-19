package com.goldmedal.aillm.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: Long): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Long)

    @Query("UPDATE chats SET updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatTimestamp(chatId: Long, timestamp: Long = System.currentTimeMillis())
}
