package com.goldmedal.aillm.ai.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StubChatModel : ChatModel {
    override val name: String = "Stub Chat Model"
    override val isLoaded: Boolean = true
    override val contextLength: Int = 4096

    override suspend fun load(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun unload(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        // Simulate processing time
        delay(1000)

        val lastMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val response = "This is a stub response. In production, this would be handled by a real LLM model. You said: $lastMessage"
        return Result.success(response)
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        val lastMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val words = "This is a stub response. In production, this would be handled by a real LLM model. You said: $lastMessage".split(" ")
        for (word in words) {
            delay(100)
            emit("$word ")
        }
    }
}
