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

    suspend fun createChat(title: String = DEFAULT_TITLE): Long =
        chatDao.insertChat(ChatEntity(title = title))

    suspend fun updateChat(chat: ChatEntity) = chatDao.updateChat(chat)

    suspend fun renameChat(chatId: Long, title: String) {
        val chat = chatDao.getChatById(chatId) ?: return
        chatDao.updateChat(chat.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun touchChat(chatId: Long) = chatDao.updateChatTimestamp(chatId)

    suspend fun deleteChat(chatId: Long) = chatDao.deleteChatById(chatId)

    fun getMessagesByChatId(chatId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesByChatId(chatId)

    suspend fun getRecentMessages(chatId: Long, limit: Int = 20): List<MessageEntity> =
        messageDao.getRecentMessages(chatId, limit)

    suspend fun getMessageCount(chatId: Long): Int = messageDao.getMessageCount(chatId)

    /** Stored image names belonging to a conversation, for cleanup on delete. */
    suspend fun getChatImagePaths(chatId: Long): List<String> = messageDao.getChatImagePaths(chatId)

    /** Stored document names belonging to a conversation, for cleanup on delete. */
    suspend fun getChatDocumentPaths(chatId: Long): List<String> =
        messageDao.getChatDocumentPaths(chatId)

    suspend fun insertMessage(message: MessageEntity): Long = messageDao.insertMessage(message)

    suspend fun updateMessage(message: MessageEntity) = messageDao.updateMessage(message)

    suspend fun updateMessageContent(messageId: Long, content: String) =
        messageDao.updateMessageContent(messageId, content)

    suspend fun deleteMessage(messageId: Long) = messageDao.deleteMessageById(messageId)

    suspend fun deleteLastAssistantMessage(chatId: Long): MessageEntity? {
        val last = messageDao.getLastAssistantMessage(chatId) ?: return null
        messageDao.deleteMessageById(last.id)
        return last
    }

    companion object {
        const val DEFAULT_TITLE = "New conversation"
    }
}
