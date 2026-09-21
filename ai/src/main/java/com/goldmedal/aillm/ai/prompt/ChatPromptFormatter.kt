package com.goldmedal.aillm.ai.prompt

import com.goldmedal.aillm.ai.chat.ChatMessage

/**
 * The chat template a model family expects. Applying the right one matters a
 * lot on small models: feeding raw prose to an instruct model measurably
 * degrades the reply.
 */
enum class ChatPromptFormat {
    /** Qwen2.5 / Qwen2.5-Coder — ChatML. */
    CHATML,

    /** Llama 3.x header format. */
    LLAMA3,

    /** Gemma 2 / Gemma 3 turn format (no system role). */
    GEMMA,

    /** Phi-3.x pipe format. */
    PHI3,

    /** SmolVLM / SmolVLM2. */
    SMOLVLM,

    /** No template — plain concatenation, for models without a chat format. */
    PLAIN
}

/**
 * Turns a message list into the single prompt string an instruct model expects.
 *
 * BOS is deliberately NOT emitted here: the tokenizer is called with
 * `add_special = true`, which inserts the correct BOS token for the model.
 */
object ChatPromptFormatter {

    /**
     * @param imageMarker when present, it is placed at the start of the last
     *   user turn. libmtmd replaces that marker with the actual image tokens.
     */
    fun format(
        messages: List<ChatMessage>,
        format: ChatPromptFormat,
        imageMarker: String? = null
    ): String {
        val prepared = if (imageMarker.isNullOrBlank()) messages else injectMarker(messages, imageMarker)
        return when (format) {
            ChatPromptFormat.CHATML -> chatml(prepared)
            ChatPromptFormat.LLAMA3 -> llama3(prepared)
            ChatPromptFormat.GEMMA -> gemma(prepared)
            ChatPromptFormat.PHI3 -> phi3(prepared)
            ChatPromptFormat.SMOLVLM -> smolvlm(prepared)
            ChatPromptFormat.PLAIN -> prepared.joinToString("\n\n") { "${it.role}: ${it.content}" }
        }
    }

    /** Vision models expect the media marker before the question, not after it. */
    private fun injectMarker(messages: List<ChatMessage>, marker: String): List<ChatMessage> {
        val lastUserIndex = messages.indexOfLast { it.role == "user" }
        if (lastUserIndex < 0) return messages
        return messages.mapIndexed { index, message ->
            if (index == lastUserIndex) {
                message.copy(content = "$marker\n${message.content}")
            } else {
                message
            }
        }
    }

    // ---------------------------------------------------------------- ChatML

    private fun chatml(messages: List<ChatMessage>): String = buildString {
        messages.forEach { message ->
            append("<|im_start|>").append(roleOf(message.role)).append('\n')
            append(message.content.trim()).append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }

    // ----------------------------------------------------------------- Llama 3

    private fun llama3(messages: List<ChatMessage>): String = buildString {
        messages.forEach { message ->
            append("<|start_header_id|>").append(roleOf(message.role)).append("<|end_header_id|>\n\n")
            append(message.content.trim()).append("<|eot_id|>")
        }
        append("<|start_header_id|>assistant<|end_header_id|>\n\n")
    }

    // ------------------------------------------------------------------ Gemma

    /** Gemma has no system role, so any system text is folded into turn one. */
    private fun gemma(messages: List<ChatMessage>): String {
        val system = messages.filter { it.role == "system" }
            .joinToString("\n\n") { it.content.trim() }
        val turns = messages.filter { it.role != "system" }
        return buildString {
            turns.forEachIndexed { index, message ->
                val role = if (message.role == "assistant") "model" else "user"
                append("<start_of_turn>").append(role).append('\n')
                if (index == 0 && system.isNotEmpty()) {
                    append(system).append("\n\n")
                }
                append(message.content.trim()).append("<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
    }

    // ------------------------------------------------------------------- Phi-3

    private fun phi3(messages: List<ChatMessage>): String = buildString {
        messages.forEach { message ->
            append("<|").append(roleOf(message.role)).append("|>\n")
            append(message.content.trim()).append("<|end|>\n")
        }
        append("<|assistant|>\n")
    }

    // ----------------------------------------------------------------- SmolVLM

    private fun smolvlm(messages: List<ChatMessage>): String = buildString {
        messages.forEach { message ->
            val role = when (message.role) {
                "assistant" -> "Assistant"
                "system" -> "System"
                else -> "User"
            }
            append("<|im_start|>").append(role).append(": ")
            append(message.content.trim()).append("<end_of_utterance>\n")
        }
        append("<|im_start|>Assistant: ")
    }

    private fun roleOf(role: String): String = when (role) {
        "assistant" -> "assistant"
        "system" -> "system"
        else -> "user"
    }
}
