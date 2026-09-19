package com.goldmedal.aillm.memory.embedding

interface EmbeddingModel {
    val name: String
    val isLoaded: Boolean

    suspend fun load(): Result<Unit>
    suspend fun unload(): Result<Unit>
    suspend fun embed(text: String): Result<FloatArray>
    suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>>
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float
}
