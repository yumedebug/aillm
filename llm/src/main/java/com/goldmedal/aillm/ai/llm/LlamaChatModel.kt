package com.goldmedal.aillm.ai.llm

import android.content.Context
import com.goldmedal.aillm.ai.chat.ChatMessage
import com.goldmedal.aillm.ai.chat.ChatModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.Dispatchers
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

    private var model: Long = 0
    private var config: LlamaConfig? = null
    private var _isLoaded = false

    override val name: String = "llama.cpp Chat"
    override val isLoaded: Boolean get() = _isLoaded
    override val contextLength: Int get() = config?.contextSize ?: 4096

    override suspend fun load(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            val modelFile = modelsDir.listFiles()?.firstOrNull {
                it.extension == "gguf" && it.name.contains("chat", ignoreCase = true)
            }

            if (modelFile == null) {
                return@withContext Result.failure(Exception("No GGUF chat model found in ${modelsDir.absolutePath}. Download a model first."))
            }

            val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            config = LlamaConfig(
                contextSize = 4096,
                threads = threadCount,
                temperature = 0.7f,
                topP = 0.9f,
                topK = 40,
                repeatPenalty = 1.1f,
                maxTokens = 2048,
                gpuLayers = 0
            )

            model = Llama.loadModel(modelFile.absolutePath, config!!)
            _isLoaded = true
            Result.success(Unit)
        } catch (e: Exception) {
            _isLoaded = false
            Result.failure(e)
        }
    }

    override suspend fun unload(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (model != 0L) {
                Llama.releaseModel(model)
                model = 0
            }
            _isLoaded = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            if (!_isLoaded || model == 0L) {
                return@withContext Result.failure(Exception("Model not loaded"))
            }

            val prompt = buildPromptString(messages)
            val result = Llama.complete(
                model,
                prompt = prompt,
                maxTokens = maxTokens
            )

            Result.success(result.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        if (!_isLoaded || model == 0L) {
            emit("Error: Model not loaded")
            return@flow
        }

        val prompt = buildPromptString(messages)
        val result = Llama.complete(
            model,
            prompt = prompt,
            maxTokens = maxTokens
        )

        emit(result.text)
    }

    private fun buildPromptString(messages: List<ChatMessage>): String {
        return buildString {
            for (msg in messages) {
                when (msg.role) {
                    "system" -> append("[System] ${msg.content}\n")
                    "user" -> append("[User] ${msg.content}\n")
                    "assistant" -> append("[Assistant] ${msg.content}\n")
                }
            }
            append("[Assistant] ")
        }
    }

    fun getModelDir(): File {
        return File(context.getExternalFilesDir(null), "models").also {
            if (!it.exists()) it.mkdirs()
        }
    }
}
