package com.goldmedal.aillm.memory

data class ExtractedMemory(
    val category: String,
    val key: String,
    val value: String,
    val importance: Int = 0,
    val confidence: Float = 1.0f
)