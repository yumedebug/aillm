package com.goldmedal.aillm.memory.extraction

import com.goldmedal.aillm.memory.ExtractedMemory

interface MemoryExtractor {
    suspend fun extractMemoriesFromMessage(
        userMessage: String,
        aiResponse: String
    ): List<ExtractedMemory>
}
