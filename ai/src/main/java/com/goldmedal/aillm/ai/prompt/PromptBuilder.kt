package com.goldmedal.aillm.ai.prompt

import com.goldmedal.aillm.ai.chat.ChatMessage
import com.goldmedal.aillm.core.database.UserMemoryEntity

class PromptBuilder {
    companion object {
        private const val SYSTEM_PROMPT_TEMPLATE = """You are a helpful AI assistant that lives on the user's device. You have access to the user's memories and can help them with various tasks.

Important guidelines:
- Be helpful, concise, and accurate
- Use the provided memories to personalize your responses
- If you don't have enough information, ask the user
- Never make up information about the user's preferences or memories
- Be conversational and natural

Current date: %s
"""

        private const val MAX_RECENT_MESSAGES = 20
        private const val MAX_RELEVANT_MEMORIES = 10
    }

    fun buildPrompt(
        recentMessages: List<ChatMessage>,
        relevantMemories: List<UserMemoryEntity>,
        webSearchResults: String? = null,
        imageDescription: String? = null
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        val currentDate = java.time.LocalDate.now().toString()
        val systemPrompt = SYSTEM_PROMPT_TEMPLATE.format(currentDate)

        // System prompt
        val systemContent = buildString {
            append(systemPrompt)

            if (relevantMemories.isNotEmpty()) {
                append("\n\nRelevant memories about the user:\n")
                relevantMemories.take(MAX_RELEVANT_MEMORIES).forEach { memory ->
                    append("- ${memory.category}: ${memory.key} = ${memory.value}\n")
                }
            }

            if (webSearchResults != null) {
                append("\n\nWeb search results:\n$webSearchResults")
            }

            if (imageDescription != null) {
                append("\n\nImage description: $imageDescription")
            }
        }

        messages.add(ChatMessage(role = "system", content = systemContent))

        // Recent messages (limited to MAX_RECENT_MESSAGES)
        val recentMessagesLimited = recentMessages.takeLast(MAX_RECENT_MESSAGES)
        messages.addAll(recentMessagesLimited)

        return messages
    }

    fun buildImageAnalysisPrompt(userMessage: String): String {
        return """Analyze this image and provide a detailed description. Include:
1. What objects are visible
2. Any text visible in the image
3. The overall scene or context
4. Any notable details

User's request: $userMessage"""
    }
}
