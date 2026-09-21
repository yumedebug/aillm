package com.goldmedal.aillm.ai.chat

import com.goldmedal.aillm.ai.engine.OnDeviceEngine
import kotlinx.coroutines.flow.Flow

/**
 * Lifecycle (load/unload/isLoaded) comes from [OnDeviceEngine]; only the
 * chat-specific surface is declared here.
 */
interface ChatModel : OnDeviceEngine {
    val contextLength: Int

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
