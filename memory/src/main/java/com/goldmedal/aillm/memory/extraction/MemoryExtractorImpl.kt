package com.goldmedal.aillm.memory.extraction

import com.goldmedal.aillm.memory.ExtractedMemory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls durable facts out of what the user said.
 *
 * Deliberately simple and local: keyword groups catch the things people state
 * about themselves (what they run, what they code in, what they like), and a
 * few explicit patterns catch sentences that spell it out. Everything else is
 * left alone — inventing memories the user never expressed is worse than
 * remembering nothing.
 */
@Singleton
class MemoryExtractorImpl @Inject constructor() : MemoryExtractor {

    override suspend fun extractMemoriesFromMessage(
        userMessage: String,
        aiResponse: String
    ): List<ExtractedMemory> {
        val memories = mutableListOf<ExtractedMemory>()

        for (pattern in KEYWORD_PATTERNS) {
            for (keyword in pattern.keywords) {
                if (!mentions(userMessage, keyword)) continue
                memories.add(
                    ExtractedMemory(
                        category = pattern.category,
                        key = keyword,
                        value = snippetAround(userMessage, keyword),
                        importance = pattern.importance,
                        confidence = 0.8f
                    )
                )
            }
        }

        for (pattern in EXPLICIT_PATTERNS) {
            val match = pattern.regex.find(userMessage) ?: continue
            val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (value.length > 1) {
                memories.add(
                    ExtractedMemory(
                        category = "profile",
                        key = pattern.key,
                        value = value,
                        importance = 2,
                        confidence = 0.9f
                    )
                )
            }
        }

        return memories.distinctBy { "${it.category}:${it.key}" }
    }

    /**
     * Latin keywords are matched on word boundaries — a plain `contains` makes
     * "arch" match "search" and "go" match "good", which quietly fills memory
     * with nonsense. CJK keywords have no word boundaries, so they are matched
     * as substrings.
     */
    private fun mentions(message: String, keyword: String): Boolean {
        if (keyword.any { it.code > 127 }) return message.contains(keyword, ignoreCase = true)
        val prefix = if (keyword.first().isLetterOrDigit()) "\\b" else ""
        val suffix = if (keyword.last().isLetterOrDigit()) "\\b" else ""
        return Regex(prefix + Regex.escape(keyword) + suffix, RegexOption.IGNORE_CASE)
            .containsMatchIn(message)
    }

    /**
     * The clause the keyword sits in, rather than the bare keyword, so a stored
     * memory reads like something the user actually said. Works for messages
     * with and without spaces.
     */
    private fun snippetAround(message: String, keyword: String): String {
        val index = message.indexOf(keyword, ignoreCase = true)
        if (index < 0) return keyword

        val windowStart = maxOf(0, index - VALUE_BEFORE)
        val start = (windowStart until index).lastOrNull { message[it] in CLAUSE_BREAKS }
            ?.plus(1)
            ?: windowStart

        val keywordEnd = (index + keyword.length).coerceAtMost(message.length)
        val windowEnd = minOf(message.length, keywordEnd + VALUE_AFTER)
        val end = (keywordEnd until windowEnd).firstOrNull { message[it] in CLAUSE_BREAKS }
            ?: windowEnd

        return message.substring(start, end).trim().trim(*TRIM_CHARS).ifBlank { keyword }
    }

    private data class Pattern(
        val category: String,
        val keywords: List<String>,
        val importance: Int = 1
    )

    private data class ExplicitPattern(val regex: Regex, val key: String)

    private companion object {
        private const val VALUE_BEFORE = 30
        private const val VALUE_AFTER = 40

        private val CLAUSE_BREAKS = charArrayOf(
            '\n', '。', '、', '.', ',', '!', '?', ';', ':', '！', '？', '，', '；'
        )

        private val TRIM_CHARS = charArrayOf(
            '"', '\'', '「', '」', '『', '』', '（', '）', '(', ')', '-', '—'
        )

        private val KEYWORD_PATTERNS = listOf(
            Pattern(
                "operating system",
                listOf(
                    "linux", "windows", "macos", "ubuntu", "fedora", "debian",
                    "arch", "manjaro", "mint", "android", "ios"
                )
            ),
            Pattern(
                "programming language",
                listOf(
                    "python", "java", "kotlin", "javascript", "typescript", "rust",
                    "golang", "swift", "php", "ruby", "c++", "c#", "sql"
                )
            ),
            Pattern(
                "device",
                listOf(
                    "phone", "laptop", "desktop", "tablet", "pixel", "iphone",
                    "samsung", "macbook", "surface"
                )
            ),
            Pattern(
                "preference",
                listOf(
                    "like", "love", "prefer", "favorite", "favourite", "enjoy",
                    "dislike", "hate", "好き", "好み"
                )
            )
        )

        private val EXPLICIT_PATTERNS = listOf(
            ExplicitPattern(
                Regex(
                    "\\b(?:i use|i'm using|i am using|i run|i'm running)\\s+([^.!?,;\\n]{2,40})",
                    RegexOption.IGNORE_CASE
                ),
                "usage"
            ),
            ExplicitPattern(
                Regex(
                    "\\b(?:i like|i love|i prefer|i enjoy|i hate|i dislike|my favou?rite(?:\\s+is)?)\\s+([^.!?,;\\n]{2,40})",
                    RegexOption.IGNORE_CASE
                ),
                "preference"
            ),
            ExplicitPattern(
                Regex(
                    "\\b(?:my name is|call me)\\s+([^.!?,;\\n]{2,30})",
                    RegexOption.IGNORE_CASE
                ),
                "name"
            ),
            ExplicitPattern(
                Regex("(?:私|僕|わたし)の名前は\\s*([^。、！？!?\\n]{1,20})"),
                "name"
            )
        )
    }
}
