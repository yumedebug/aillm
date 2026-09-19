package com.goldmedal.aillm.chat.repository

import com.goldmedal.aillm.core.database.ChatDao
import com.goldmedal.aillm.core.database.ChatEntity
import com.goldmedal.aillm.core.database.MessageDao
import com.goldmedal.aillm.core.database.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) {
    fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    suspend fun getChatById(chatId: Long): ChatEntity? = chatDao.getChatById(chatId)

    suspend fun createChat(title: String = "New Chat"): Long {
        val chat = ChatEntity(title = title)
        return chatDao.insertChat(chat)
    }

    suspend fun updateChat(chat: ChatEntity) = chatDao.updateChat(chat)

    suspend fun deleteChat(chatId: Long) = chatDao.deleteChatById(chatId)

    fun getMessagesByChatId(chatId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesByChatId(chatId)

    suspend fun getRecentMessages(chatId: Long, limit: Int = 20): List<MessageEntity> =
        messageDao.getRecentMessages(chatId, limit)

    suspend fun insertMessage(message: MessageEntity): Long =
        messageDao.insertMessage(message)

    suspend fun updateMessage(message: MessageEntity) =
        messageDao.updateMessage(message)

    suspend fun deleteMessagesByChatId(chatId: Long) =
        messageDao.deleteMessagesByChatId(chatId)
}
