package com.goldmedal.aillm.ai.llm

import android.content.Context
import com.goldmedal.aillm.memory.embedding.EmbeddingModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEmbeddingModel @Inject constructor(
    @ApplicationContext private val context: Context
) : EmbeddingModel {

    private var model: Long = 0
    private var _isLoaded = false

    override val name: String = "llama.cpp Embedding"
    override val isLoaded: Boolean get() = _isLoaded

    override suspend fun load(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            val embeddingFile = modelsDir.listFiles()?.firstOrNull {
                it.extension == "gguf" && it.name.contains("embed", ignoreCase = true)
            }

            if (embeddingFile == null) {
                return@withContext Result.failure(Exception("No embedding model found"))
            }

            val config = LlamaConfig(contextSize = 512, threads = 4)
            model = Llama.loadModel(embeddingFile.absolutePath, config)
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

    override suspend fun embed(text: String): Result<FloatArray> = withContext(Dispatchers.Default) {
        try {
            if (!_isLoaded || model == 0L) {
                return@withContext Result.failure(Exception("Embedding model not loaded"))
            }

            val embedding = Llama.embed(model, text)
            Result.success(embedding)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>> {
        return try {
            val results = texts.map { embed(it).getOrThrow() }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA > 0f && normB > 0f) {
            dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
        } else {
            0f
        }
    }
}
