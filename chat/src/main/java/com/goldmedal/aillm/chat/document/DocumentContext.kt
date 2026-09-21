package com.goldmedal.aillm.chat.document

import com.goldmedal.aillm.ai.prompt.AttachedDocument

/**
 * Turns a document into the text handed to the model for a single turn.
 *
 * A short document is passed whole. A long one is passed as its opening plus
 * the passages that actually mention what the user is asking about, so a
 * follow-up about something far into the file still has a chance of being
 * answered without shovelling the whole thing into a small model's context.
 */
object DocumentContext {

    private const val HEAD_CHARS = 6000
    private const val EXCERPT_BUDGET_CHARS = 2000
    private const val MIN_PARAGRAPH_CHARS = 40
    private const val MIN_TERM_CHARS = 3

    fun build(displayName: String, text: String, question: String): AttachedDocument {
        val clean = text.trim()
        if (clean.length <= HEAD_CHARS + EXCERPT_BUDGET_CHARS) {
            return AttachedDocument(name = displayName, text = clean, truncated = false)
        }

        val head = clean.take(HEAD_CHARS)
        val excerpt = relevantPassages(clean.substring(HEAD_CHARS), question)
        val body = if (excerpt.isEmpty()) {
            head
        } else {
            "$head\n\n… later passages that mention the question …\n\n$excerpt"
        }
        return AttachedDocument(name = displayName, text = body, truncated = true)
    }

    /** Picks paragraphs mentioning terms from the question, best match first. */
    private fun relevantPassages(text: String, question: String): String {
        val terms = question.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= MIN_TERM_CHARS }
            .toSet()
        if (terms.isEmpty()) return ""

        val ranked = text.split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.length >= MIN_PARAGRAPH_CHARS }
            .map { paragraph ->
                val lower = paragraph.lowercase()
                paragraph to terms.count { term -> lower.contains(term) }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        val picked = StringBuilder()
        for (paragraph in ranked) {
            if (picked.length + paragraph.length > EXCERPT_BUDGET_CHARS) continue
            if (picked.isNotEmpty()) picked.append("\n\n")
            picked.append(paragraph)
        }
        return picked.toString()
    }
}
