package com.goldmedal.aillm.chat.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.chat.document.ChatDocumentStore
import com.goldmedal.aillm.chat.image.ChatImageStore
import com.goldmedal.aillm.chat.repository.ChatRepository
import com.goldmedal.aillm.chat.session.ChatSessionController
import com.goldmedal.aillm.core.database.ChatEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the History tab: the full flat list of saved conversations.
 * Deletion is applied immediately and reflected through the Room Flow.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val sessionController: ChatSessionController
) : ViewModel() {

    private val _chats = MutableStateFlow<List<ChatEntity>>(emptyList())
    val chats: StateFlow<List<ChatEntity>> = _chats.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getAllChats()
                .catch { e -> _error.value = e.message }
                .collect { chats -> _chats.value = chats }
        }
    }

    /**
     * Asks the Chat tab to drop whatever it is showing, so the screen the user
     * lands on after tapping + is a genuinely empty conversation.
     */
    fun startNewChat() {
        sessionController.requestNewChat()
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            try {
                // Attachments shared in the conversation live outside the
                // database, so they are removed alongside the rows that
                // referenced them.
                val images = chatRepository.getChatImagePaths(chatId)
                val documents = chatRepository.getChatDocumentPaths(chatId)
                chatRepository.deleteChat(chatId)
                withContext(Dispatchers.IO) {
                    ChatImageStore.deleteAll(context, images)
                    ChatDocumentStore.deleteAll(context, documents)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
