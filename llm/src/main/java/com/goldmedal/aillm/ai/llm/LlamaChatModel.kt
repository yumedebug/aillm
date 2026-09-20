package com.goldmedal.aillm.ai.llm

import android.content.Context
import com.goldmedal.aillm.ai.chat.ChatMessage
import com.goldmedal.aillm.ai.chat.ChatModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaChatModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ChatModel {

    private var _isLoaded = false

    override val name: String = "llama.cpp Chat (Stub)"
    override val isLoaded: Boolean get() = _isLoaded
    override val contextLength: Int = 4096

    override suspend fun load(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            // Check if any GGUF model exists
            val modelFile = modelsDir.listFiles()?.firstOrNull {
                it.extension == "gguf"
            }

            if (modelFile == null) {
                return@withContext Result.failure(
                    Exception("No GGUF model found in ${modelsDir.absolutePath}. Download a model first.")
                )
            }

            // TODO: Load actual model with llama.cpp when library is available
            _isLoaded = true
            Result.success(Unit)
        } catch (e: Exception) {
            _isLoaded = false
            Result.failure(e)
        }
    }

    override suspend fun unload(): Result<Unit> = withContext(Dispatchers.Default) {
        _isLoaded = false
        Result.success(Unit)
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            if (!_isLoaded) {
                return@withContext Result.failure(Exception("Model not loaded"))
            }

            // Stub response - replace with actual llama.cpp inference
            delay(1000) // Simulate processing time
            val lastMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
            val response = "This is a stub response. In production, this would be handled by a real LLM model running on your device via llama.cpp.\n\nYou said: $lastMessage"
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        if (!_isLoaded) {
            emit("Error: Model not loaded")
            return@flow
        }

        val lastMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val words = "This is a stub response. In production, this would be handled by a real LLM model running on your device via llama.cpp. You said: $lastMessage".split(" ")
        for (word in words) {
            delay(100)
            emit("$word ")
        }
    }

    fun getModelDir(): File {
        return File(context.getExternalFilesDir(null), "models").also {
            if (!it.exists()) it.mkdirs()
        }
    }
}
