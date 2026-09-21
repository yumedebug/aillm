package com.goldmedal.aillm.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun getMessagesByChatId(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(chatId: Long, limit: Int = 20): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun getMessageCount(chatId: Long): Int

    @Query("SELECT imagePath FROM messages WHERE chatId = :chatId AND imagePath IS NOT NULL")
    suspend fun getChatImagePaths(chatId: Long): List<String>

    @Query("SELECT documentPath FROM messages WHERE chatId = :chatId AND documentPath IS NOT NULL")
    suspend fun getChatDocumentPaths(chatId: Long): List<String>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND role = 'assistant' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastAssistantMessage(chatId: Long): MessageEntity?

    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: Long)
}
