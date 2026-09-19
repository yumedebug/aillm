package com.goldmedal.aillm.memory.embedding

import com.goldmedal.aillm.core.database.UserMemoryDao
import com.goldmedal.aillm.core.database.UserMemoryEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemanticSearch @Inject constructor(
    private val userMemoryDao: UserMemoryDao,
    private val embeddingModel: EmbeddingModel
) {

    suspend fun search(query: String, limit: Int = 10): List<UserMemoryEntity> {
        if (!embeddingModel.isLoaded) {
            return userMemoryDao.searchMemoriesByValue(query)
        }

        return try {
            val queryEmbedding = embeddingModel.embed(query).getOrThrow()
            val allMemories = userMemoryDao.getAllActiveMemories().first()

            val scoredMemories = allMemories.mapNotNull { memory ->
                val memoryText = "${memory.category} ${memory.key} ${memory.value}"
                val memoryEmbedding = embeddingModel.embed(memoryText).getOrNull()
                if (memoryEmbedding != null) {
                    val score = embeddingModel.cosineSimilarity(queryEmbedding, memoryEmbedding)
                    Triple(memory, score, memoryText)
                } else {
                    null
                }
            }

            scoredMemories
                .filter { it.second > 0.3f }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        } catch (e: Exception) {
            userMemoryDao.searchMemoriesByValue(query)
        }
    }

    suspend fun findSimilar(memory: UserMemoryEntity, limit: Int = 5): List<UserMemoryEntity> {
        if (!embeddingModel.isLoaded) {
            return userMemoryDao.searchMemoriesByValue(memory.value)
        }

        return try {
            val memoryText = "${memory.category} ${memory.key} ${memory.value}"
            val memoryEmbedding = embeddingModel.embed(memoryText).getOrThrow()
            val allMemories = userMemoryDao.getAllActiveMemories().first()

            allMemories
                .filter { it.id != memory.id }
                .mapNotNull { other ->
                    val otherText = "${other.category} ${other.key} ${other.value}"
                    val otherEmbedding = embeddingModel.embed(otherText).getOrNull()
                    if (otherEmbedding != null) {
                        val score = embeddingModel.cosineSimilarity(memoryEmbedding, otherEmbedding)
                        Pair(other, score)
                    } else {
                        null
                    }
                }
                .filter { it.second > 0.5f }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
