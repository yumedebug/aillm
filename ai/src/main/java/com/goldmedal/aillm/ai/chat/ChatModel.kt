package com.goldmedal.aillm.ai.chat

import kotlinx.coroutines.flow.Flow

interface ChatModel {
    val name: String
    val isLoaded: Boolean
    val contextLength: Int

    suspend fun load(): Result<Unit>
    suspend fun unload(): Result<Unit>
    suspend fun generate(
        messages: List<ChatMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 2048
    ): Result<String>

    fun generateStream(
        messages: List<ChatMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 2048
    ): Flow<String>
}

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
