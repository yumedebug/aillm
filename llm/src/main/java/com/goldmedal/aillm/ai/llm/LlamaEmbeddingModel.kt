package com.goldmedal.aillm.ai.llm

import android.content.Context
import com.goldmedal.aillm.memory.embedding.EmbeddingModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder for on-device embeddings.
 *
 * No embedding backend ships with this build, so this refuses to load rather
 * than handing out vectors that mean nothing: a similarity search over random
 * numbers looks like it works and would quietly rank memories at random. Memory
 * retrieval stays lexical in the meantime, which is honest and still useful.
 *
 * The context is kept because a real implementation would load a small
 * embedding GGUF from the same model store the chat models use.
 */
@Singleton
class LlamaEmbeddingModel @Inject constructor(
    @ApplicationContext private val context: Context
) : EmbeddingModel {

    override val name: String = "On-device embeddings (unavailable)"

    override val isLoaded: Boolean = false

    override suspend fun load(): Result<Unit> = Result.failure(
        UnsupportedOperationException(NOT_AVAILABLE)
    )

    override suspend fun unload(): Result<Unit> = Result.success(Unit)

    override suspend fun embed(text: String): Result<FloatArray> = Result.failure(
        UnsupportedOperationException(NOT_AVAILABLE)
    )

    override suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>> =
        Result.failure(UnsupportedOperationException(NOT_AVAILABLE))

    override fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
        return if (denominator > 0.0) (dot / denominator).toFloat() else 0f
    }

    private companion object {
        private const val NOT_AVAILABLE =
            "On-device embeddings are not implemented in this build."
    }
}
