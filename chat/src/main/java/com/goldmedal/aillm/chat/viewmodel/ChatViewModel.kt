package com.goldmedal.aillm.chat.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.chat.ChatMessage
import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.model.ModelRepository
import com.goldmedal.aillm.ai.model.ModelStatus
import com.goldmedal.aillm.ai.prompt.AttachedDocument
import com.goldmedal.aillm.ai.prompt.PromptBuilder
import com.goldmedal.aillm.ai.vision.VisionModel
import com.goldmedal.aillm.chat.document.ChatDocumentStore
import com.goldmedal.aillm.chat.document.DocumentContext
import com.goldmedal.aillm.chat.image.ChatImageStore
import com.goldmedal.aillm.chat.repository.ChatRepository
import com.goldmedal.aillm.chat.session.ChatSessionController
import com.goldmedal.aillm.core.database.MessageEntity
import com.goldmedal.aillm.core.preferences.AppSettings
import com.goldmedal.aillm.memory.MemoryEngine
import com.goldmedal.aillm.memory.extraction.MemoryExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Chat state. The screen stays usable when no model is installed or loaded —
 * that is treated as a normal state, not an error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val memoryEngine: MemoryEngine,
    private val memoryExtractor: MemoryExtractor,
    private val modelRepository: ModelRepository,
    private val promptBuilder: PromptBuilder,
    private val appSettings: AppSettings,
    private val chatModel: ChatModel,
    private val visionModel: VisionModel,
    private val sessionController: ChatSessionController
) : ViewModel() {

    private val _currentChatId = MutableStateFlow<Long?>(null)
    val currentChatId: StateFlow<Long?> = _currentChatId.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _attachedImage = MutableStateFlow<Uri?>(null)
    val attachedImage: StateFlow<Uri?> = _attachedImage.asStateFlow()

    private val _attachedFileUri = MutableStateFlow<Uri?>(null)

    private val _attachedFileName = MutableStateFlow<String?>(null)
    val attachedFileName: StateFlow<String?> = _attachedFileName.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _chatModelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotInstalled)
    val chatModelStatus: StateFlow<ModelStatus> = _chatModelStatus.asStateFlow()

    private val _chatModelName = MutableStateFlow<String?>(null)
    val chatModelName: StateFlow<String?> = _chatModelName.asStateFlow()

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            _currentChatId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else chatRepository.getMessagesByChatId(id)
                }
                .collect { _messages.value = it }
        }
        // "+ from History" lands here: the request arrives before the user is
        // back on the Chat tab, so the conversation is cleared ready and waiting.
        viewModelScope.launch {
            sessionController.newChatRequests.drop(1).collect { newChat() }
        }
        viewModelScope.launch {
            modelRepository.states.collect { states ->
                // Either a Chat or a Coding model can be the resident assistant.
                val loadedId = modelRepository.activeChatModelId()
                _chatModelStatus.value = loadedId?.let { states[it] } ?: ModelStatus.NotInstalled
                _chatModelName.value = loadedId?.let { modelRepository.spec(it)?.name }
            }
        }
    }

    // ---- conversation management ----

    fun newChat() {
        if (_isGenerating.value) return
        _currentChatId.value = null
        _messages.value = emptyList()
        _attachedImage.value = null
        _attachedFileUri.value = null
        _attachedFileName.value = null
        _error.value = null
    }

    fun loadChat(chatId: Long) {
        if (_isGenerating.value) return
        _currentChatId.value = chatId
    }

    fun isChatModelReady(): Boolean =
        _chatModelStatus.value is ModelStatus.Ready && chatModel.isLoaded

    // ---- input ----

    fun attachImage(uri: Uri) {
        _attachedImage.value = uri
    }

    fun removeAttachedImage() {
        _attachedImage.value = null
    }

    fun attachFile(uri: Uri, displayName: String? = null) {
        _attachedFileUri.value = uri
        _attachedFileName.value = displayName?.takeIf { it.isNotBlank() }
        if (_attachedFileName.value == null) {
            // Resolving the real file name touches the content resolver, so it
            // happens off the main thread and is discarded if the user swaps the
            // attachment before it comes back.
            viewModelScope.launch {
                val resolved = withContext(Dispatchers.IO) {
                    ChatDocumentStore.resolveDisplayName(context, uri)
                }
                if (_attachedFileUri.value == uri) _attachedFileName.value = resolved
            }
        }
    }

    fun removeAttachedFile() {
        _attachedFileUri.value = null
        _attachedFileName.value = null
    }

    fun clearError() {
        _error.value = null
    }

    // ---- sending ----

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank() || _isGenerating.value) return
        if (!isChatModelReady()) {
            _error.value = "Choose and load a chat model to start talking."
            return
        }

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            try {
                val chatId = _currentChatId.value
                    ?: chatRepository.createChat().also { _currentChatId.value = it }
                val isFirst = chatRepository.getMessageCount(chatId) == 0

                val imageUri = _attachedImage.value
                val documentUri = _attachedFileUri.value

                // Picker URIs are transient, so attachments are copied into app
                // storage here. That is what keeps them available to later turns.
                val storedImage = imageUri?.let { uri ->
                    withContext(Dispatchers.IO) { ChatImageStore.save(context, uri) }
                }
                val storedDocument = documentUri?.let { uri ->
                    withContext(Dispatchers.IO) {
                        ChatDocumentStore.save(context, uri, _attachedFileName.value)
                    }
                }
                val attachmentWarning = when {
                    imageUri != null && storedImage == null -> "That image could not be attached."
                    documentUri != null && storedDocument == null ->
                        "That file could not be attached as text."
                    imageUri != null && !visionModel.isLoaded ->
                        "Image saved. Install a vision model in Models → Images to have it understood."
                    else -> null
                }

                chatRepository.insertMessage(
                    MessageEntity(
                        chatId = chatId,
                        role = "user",
                        content = text,
                        hasImage = storedImage != null,
                        imagePath = storedImage,
                        documentPath = storedDocument?.storedName,
                        documentName = storedDocument?.displayName
                    )
                )
                if (isFirst) chatRepository.renameChat(chatId, autoTitle(text))

                _attachedImage.value = null
                _attachedFileUri.value = null
                _attachedFileName.value = null

                attachmentWarning?.let { _error.value = it }
                generate(chatId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong"
            } finally {
                _isGenerating.value = false
                _streamingText.value = null
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
    }

    fun regenerate() {
        if (_isGenerating.value) return
        val chatId = _currentChatId.value ?: return
        if (!isChatModelReady()) {
            _error.value = "Choose and load a chat model to regenerate."
            return
        }

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            try {
                chatRepository.deleteLastAssistantMessage(chatId)
                val lastUser = chatRepository.getRecentMessages(chatId)
                    .lastOrNull { it.role == "user" }?.content ?: return@launch
                generate(chatId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong"
            } finally {
                _isGenerating.value = false
                _streamingText.value = null
            }
        }
    }

    /**
     * Streams a response for the current conversation. The user message is
     * already persisted; this only produces and stores the assistant reply.
     */
    private suspend fun generate(chatId: Long) {
        val recentDescending = chatRepository.getRecentMessages(chatId)
        val recent = recentDescending.reversed().map { ChatMessage(role = it.role, content = it.content) }
        val lastUserText = recent.lastOrNull { it.role == "user" }?.content.orEmpty()

        val imageDescription = describeConversationImage(recentDescending, lastUserText)
        val attachedDocument = documentContext(recentDescending, lastUserText)

        val memories = runCatching { memoryEngine.searchMemories(lastUserText).take(10) }
            .getOrDefault(emptyList())

        val prompt = promptBuilder.buildPrompt(
            recentMessages = recent,
            relevantMemories = memories,
            imageDescription = imageDescription,
            attachedDocument = attachedDocument,
            onlineToolsEnabled = false
        )

        val temperature = appSettings.temperature.first()
        val maxTokens = appSettings.maxTokens.first()

        val builder = StringBuilder()
        _streamingText.value = ""
        try {
            chatModel.generateStream(prompt, temperature = temperature, maxTokens = maxTokens)
                .collect { chunk ->
                    builder.append(chunk)
                    _streamingText.value = builder.toString()
                }
        } catch (e: CancellationException) {
            val partial = builder.toString().trim()
            _streamingText.value = null
            if (partial.isNotBlank()) {
                chatRepository.insertMessage(
                    MessageEntity(chatId = chatId, role = "assistant", content = partial)
                )
            }
            throw e
        }

        val answer = builder.toString().trim()
        _streamingText.value = null
        if (answer.isBlank()) return

        chatRepository.insertMessage(
            MessageEntity(chatId = chatId, role = "assistant", content = answer)
        )
        chatRepository.touchChat(chatId)

        runCatching {
            memoryExtractor.extractMemoriesFromMessage(lastUserText, answer).forEach { memory ->
                memoryEngine.storeMemory(
                    category = memory.category,
                    key = memory.key,
                    value = memory.value,
                    importance = memory.importance,
                    confidence = memory.confidence
                )
            }
        }
    }

    // ---- helpers ----

    /**
     * Keeps a picture shared earlier in the conversation in play.
     *
     * The model itself is stateless, so on every turn the most recent image in
     * the conversation is looked at again *with the current question*. That is
     * what makes follow-ups such as "what colour is it?" work without the user
     * re-attaching anything. Once the image falls outside the recent window it
     * is no longer re-read, so an old conversation does not pay for a vision
     * pass on every message.
     */
    private suspend fun describeConversationImage(
        newestFirst: List<MessageEntity>,
        question: String
    ): String? {
        if (!visionModel.isLoaded) return null

        val index = newestFirst.indexOfFirst { it.hasImage && !it.imagePath.isNullOrBlank() }
        if (index < 0 || index >= IMAGE_CONTEXT_MESSAGES) return null

        val stored = newestFirst[index].imagePath?.trim().orEmpty()
        if (stored.isEmpty()) return null

        val prompt = promptBuilder.buildImageAnalysisPrompt(question)
        // Looking at an image takes a moment; say so instead of appearing stuck.
        _streamingText.value = "Looking at your image…"
        return try {
            runCatching {
                val analysis = if (stored.contains("://")) {
                    visionModel.analyzeImage(Uri.parse(stored), prompt)
                } else {
                    val file = ChatImageStore.file(context, stored) ?: return null
                    visionModel.analyzeImageFromPath(file.absolutePath, prompt)
                }
                analysis.getOrNull()?.description?.takeIf { it.isNotBlank() }
            }.getOrNull()
        } finally {
            _streamingText.value = null
        }
    }

    /**
     * Keeps an attached document in play, exactly like an image: the file is
     * read again for every turn and handed to the model together with the
     * current question, so a document attached once can be asked about
     * repeatedly. Reading a document is cheap, so it survives a wider window
     * than an image (which needs a full vision pass).
     */
    private suspend fun documentContext(
        newestFirst: List<MessageEntity>,
        question: String
    ): AttachedDocument? {
        val index = newestFirst.indexOfFirst { !it.documentPath.isNullOrBlank() }
        if (index < 0 || index >= DOCUMENT_CONTEXT_MESSAGES) return null

        val stored = newestFirst[index].documentPath?.trim().orEmpty()
        if (stored.isEmpty()) return null

        val text = withContext(Dispatchers.IO) { ChatDocumentStore.readText(context, stored) }
            ?: return null
        val name = newestFirst[index].documentName?.trim().takeIf { !it.isNullOrEmpty() }
            ?: stored
        return DocumentContext.build(name, text, question)
    }

    private fun autoTitle(text: String): String {
        val clean = text.replace("\n", " ").trim()
        return if (clean.length <= 42) clean else clean.take(42).trimEnd() + "…"
    }

    companion object {
        /** How far back in the conversation an image is still re-read. */
        private const val IMAGE_CONTEXT_MESSAGES = 8

        /** Same, for documents: reading one back is cheap, so the window is wider. */
        private const val DOCUMENT_CONTEXT_MESSAGES = 12
    }
}
