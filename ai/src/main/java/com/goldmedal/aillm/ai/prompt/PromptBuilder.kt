package com.goldmedal.aillm.ai.prompt

import com.goldmedal.aillm.ai.chat.ChatMessage
import com.goldmedal.aillm.core.database.UserMemoryEntity

/**
 * A document the user attached to this conversation, already reduced to what
 * should be handed to the model for this turn.
 */
data class AttachedDocument(
    val name: String,
    val text: String,
    val truncated: Boolean = false
)

/**
 * Builds the message list handed to the chat engine.
 *
 * Note: retrieved online content is intentionally NOT part of this builder. If
 * it is ever added it must arrive as a clearly-delimited tool result, never as
 * trusted system text (external pages can contain prompt injection).
 */
class PromptBuilder {

    fun buildPrompt(
        recentMessages: List<ChatMessage>,
        relevantMemories: List<UserMemoryEntity> = emptyList(),
        imageDescription: String? = null,
        attachedDocument: AttachedDocument? = null,
        onlineToolsEnabled: Boolean = false
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        messages.add(
            ChatMessage(
                role = "system",
                content = buildSystemPrompt(
                    relevantMemories,
                    imageDescription,
                    attachedDocument,
                    onlineToolsEnabled
                )
            )
        )
        messages.addAll(recentMessages.takeLast(MAX_RECENT_MESSAGES))
        return messages
    }

    private fun buildSystemPrompt(
        relevantMemories: List<UserMemoryEntity>,
        imageDescription: String?,
        attachedDocument: AttachedDocument?,
        onlineToolsEnabled: Boolean
    ): String = buildString {
        append("You are a helpful assistant that lives entirely on the user's device.")
        append(" Be concise, accurate and conversational.")
        append(" Never invent facts about the user.")
        append("\nToday's date is ${java.time.LocalDate.now()}.")

        if (relevantMemories.isNotEmpty()) {
            append("\n\nWhat you remember about the user:\n")
            relevantMemories.take(MAX_RELEVANT_MEMORIES).forEach { memory ->
                append("- [${memory.category}] ${memory.key}: ${memory.value}\n")
            }
        }
        if (imageDescription != null) {
            append("\n\nThe user shared an image earlier in this conversation. A vision pass over ")
            append("that image reports:\n$imageDescription\n")
            append("Treat that image as part of the conversation and answer questions about it.")
        }
        if (attachedDocument != null) {
            append("\n\nThe user attached a document in this conversation: \"")
            append(attachedDocument.name)
            append("\".")
            if (attachedDocument.truncated) {
                append(" Only part of it is reproduced (its opening plus passages ")
                append("matching the question).")
            }
            append("\n--- BEGIN DOCUMENT ---\n")
            append(attachedDocument.text)
            append("\n--- END DOCUMENT ---\n")
            append("Answer questions about that document from its contents, and say when the ")
            append("answer is not in the part you were given.")
        }
        if (!onlineToolsEnabled) {
            append("\n\nYou have no internet access. Answer from your own knowledge and say so when unsure.")
        }
    }

    /**
     * Prompt for looking at an image. When a question is present the report is
     * written to answer it, which is what lets the same picture be asked about
     * repeatedly without the user re-attaching it.
     */
    fun buildImageAnalysisPrompt(userMessage: String): String = buildString {
        append("Describe this image in detail. Cover what is shown, any visible text, ")
        append("colours, layout and anything notable. ")
        if (userMessage.isNotBlank()) {
            append("Also answer this question about it, in enough detail that ")
            append("follow-up questions can be answered from the same description: ")
            append(userMessage)
        }
    }

    companion object {
        private const val MAX_RECENT_MESSAGES = 20
        private const val MAX_RELEVANT_MEMORIES = 10
    }
}
