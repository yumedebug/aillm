package com.goldmedal.aillm.memory

import com.goldmedal.aillm.memory.extraction.MemoryExtractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractorImpl @Inject constructor() : MemoryExtractor {

    override suspend fun extractMemoriesFromMessage(
        userMessage: String,
        aiResponse: String
    ): List<ExtractedMemory> {
        val memories = mutableListOf<ExtractedMemory>()

        // Extract user information patterns
        val patterns = listOf(
            Pattern("operating system", listOf("linux", "windows", "macos", "ubuntu", "fedora", "arch", "debian", "mint", "manjaro", "elementary")),
            Pattern("programming language", listOf("python", "java", "kotlin", "javascript", "typescript", "c++", "rust", "go", "swift")),
            Pattern("device", listOf("phone", "laptop", "desktop", "tablet", "pixel", "iphone", "samsung", "macbook")),
            Pattern("preference", listOf("like", "love", "prefer", "favorite", "enjoy", "hate", "dislike"))
        )

        val lowerMessage = userMessage.lowercase()

        for (pattern in patterns) {
            for (keyword in pattern.keywords) {
                if (lowerMessage.contains(keyword)) {
                    memories.add(
                        ExtractedMemory(
                            category = pattern.category,
                            key = keyword,
                            value = extractValueFromContext(userMessage, keyword),
                            importance = 1,
                            confidence = 0.8f
                        )
                    )
                }
            }
        }

        // Extract explicit statements
        val explicitPatterns = listOf(
            Regex("(?:i use|i'm using|my) (.+?)(?:\\s|$|\\.|,|!|\\?)"),
            Regex("(?:i like|i love|i prefer) (.+?)(?:\\s|$|\\.|,|!|\\?)"),
            Regex("(?:my name is|i'm|i am) (.+?)(?:\\s|$|\\.|,|!|\\?)")
        )

        for (regex in explicitPatterns) {
            val match = regex.find(lowerMessage)
            if (match != null) {
                val value = match.groupValues[1].trim()
                if (value.isNotBlank() && value.length > 1) {
                    memories.add(
                        ExtractedMemory(
                            category = "profile",
                            key = extractKeyFromPattern(regex.pattern),
                            value = value,
                            importance = 2,
                            confidence = 0.9f
                        )
                    )
                }
            }
        }

        return memories.distinctBy { "${it.category}:${it.key}" }
    }

    private fun extractValueFromContext(message: String, keyword: String): String {
        val words = message.split("\\s+".toRegex())
        val keywordIndex = words.indexOfFirst { it.lowercase().contains(keyword) }
        if (keywordIndex >= 0 && keywordIndex < words.size - 1) {
            return words.subList(keywordIndex, minOf(keywordIndex + 3, words.size)).joinToString(" ")
        }
        return keyword
    }

    private fun extractKeyFromPattern(pattern: String): String {
        return when {
            pattern.contains("use") -> "usage"
            pattern.contains("like") || pattern.contains("love") || pattern.contains("prefer") -> "preference"
            pattern.contains("name") -> "name"
            else -> "general"
        }
    }

    data class Pattern(
        val category: String,
        val keywords: List<String>
    )
}

data class ExtractedMemory(
    val category: String,
    val key: String,
    val value: String,
    val importance: Int = 0,
    val confidence: Float = 1.0f
)
