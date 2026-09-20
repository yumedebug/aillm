package com.goldmedal.aillm.chat.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.chat.ChatMessage
import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.modelmanager.ModelManager
import com.goldmedal.aillm.ai.prompt.PromptBuilder
import com.goldmedal.aillm.ai.vision.VisionModel
import com.goldmedal.aillm.chat.repository.ChatRepository
import com.goldmedal.aillm.core.database.ChatEntity
import com.goldmedal.aillm.core.database.MessageEntity
import com.goldmedal.aillm.memory.MemoryEngine
import com.goldmedal.aillm.memory.extraction.MemoryExtractor
import com.goldmedal.aillm.search.WebSearchManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val memoryEngine: MemoryEngine,
    private val memoryExtractor: MemoryExtractor,
    private val modelManager: ModelManager,
    private val webSearchManager: WebSearchManager,
    private val promptBuilder: PromptBuilder,
    private val chatModel: ChatModel,
    private val visionModel: VisionModel
) : ViewModel() {

    private val _currentChatId = MutableStateFlow<Long?>(null)
    val currentChatId: StateFlow<Long?> = _currentChatId.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _attachedImage = MutableStateFlow<Uri?>(null)
    val attachedImage: StateFlow<Uri?> = _attachedImage.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _modelStatus = MutableStateFlow("Model not loaded")
    val modelStatus: StateFlow<String> = _modelStatus.asStateFlow()

    init {
        checkModelStatus()
    }

    private fun checkModelStatus() {
        viewModelScope.launch {
            if (chatModel.isLoaded) {
                _modelStatus.value = "Ready"
            } else {
                _modelStatus.value = "Model not loaded - download a model in Settings"
            }
        }
    }

    fun createNewChat() {
        viewModelScope.launch {
            val chatId = chatRepository.createChat()
            _currentChatId.value = chatId
            _messages.value = emptyList()
        }
    }

    fun loadChat(chatId: Long) {
        viewModelScope.launch {
            _currentChatId.value = chatId
            chatRepository.getMessagesByChatId(chatId).collect { messages ->
                _messages.value = messages
            }
        }
    }

    fun sendMessage(content: String) {
        val chatId = _currentChatId.value ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null

            try {
                // Check if image is attached
                val hasImage = _attachedImage.value != null
                var imageDescription: String? = null

                // Analyze image if attached
                if (hasImage && _attachedImage.value != null) {
                    try {
                        val analysis = visionModel.analyzeImage(_attachedImage.value!!, content)
                        imageDescription = analysis.getOrNull()?.description
                    } catch (e: Exception) {
                        // Continue without image analysis
                    }
                }

                // Save user message
                val userMessage = MessageEntity(
                    chatId = chatId,
                    role = "user",
                    content = content,
                    hasImage = hasImage,
                    imagePath = _attachedImage.value?.toString()
                )
                chatRepository.insertMessage(userMessage)

                // Get recent messages for context
                val recentMessages = chatRepository.getRecentMessages(chatId)
                    .map { ChatMessage(role = it.role, content = it.content) }

                // Get relevant memories
                val relevantMemories = memoryEngine.searchMemories(content)
                    .take(10)

                // Build prompt
                val promptMessages = promptBuilder.buildPrompt(
                    recentMessages = recentMessages,
                    relevantMemories = relevantMemories,
                    imageDescription = imageDescription
                )

                // Generate response using actual LLM
                if (chatModel.isLoaded) {
                    val response = chatModel.generate(promptMessages)
                    if (response.isSuccess) {
                        val aiResponse = response.getOrNull() ?: ""

                        // Save AI response
                        val aiMessage = MessageEntity(
                            chatId = chatId,
                            role = "assistant",
                            content = aiResponse
                        )
                        chatRepository.insertMessage(aiMessage)

                        // Extract and store memories
                        val extractedMemories = memoryExtractor.extractMemoriesFromMessage(content, aiResponse)
                        extractedMemories.forEach { memory ->
                            memoryEngine.storeMemory(
                                category = memory.category,
                                key = memory.key,
                                value = memory.value,
                                importance = memory.importance,
                                confidence = memory.confidence
                            )
                        }

                        // Update chat timestamp
                        chatRepository.updateChat(
                            ChatEntity(id = chatId, updatedAt = System.currentTimeMillis())
                        )
                    } else {
                        _error.value = response.exceptionOrNull()?.message ?: "Generation failed"
                    }
                } else {
                    _error.value = "Model not loaded. Please download and load a model in Settings."
                }

                _attachedImage.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun attachImage(uri: Uri) {
        _attachedImage.value = uri
    }

    fun removeAttachedImage() {
        _attachedImage.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId)
            if (_currentChatId.value == chatId) {
                _currentChatId.value = null
                _messages.value = emptyList()
            }
        }
    }

    fun loadModel() {
        viewModelScope.launch {
            _modelStatus.value = "Loading model..."
            val result = chatModel.load()
            if (result.isSuccess) {
                _modelStatus.value = "Ready"
            } else {
                _modelStatus.value = "Failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            chatModel.unload()
            _modelStatus.value = "Model not loaded"
        }
    }
}
