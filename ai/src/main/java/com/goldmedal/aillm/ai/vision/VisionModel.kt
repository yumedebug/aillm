package com.goldmedal.aillm.ai.vision

import android.net.Uri

interface VisionModel {
    val name: String
    val isLoaded: Boolean

    suspend fun load(): Result<Unit>
    suspend fun unload(): Result<Unit>
    suspend fun analyzeImage(
        imageUri: Uri,
        prompt: String = "Describe this image in detail."
    ): Result<ImageAnalysis>

    suspend fun analyzeImageFromPath(
        imagePath: String,
        prompt: String = "Describe this image in detail."
    ): Result<ImageAnalysis>
}

data class ImageAnalysis(
    val description: String,
    val objects: List<String> = emptyList(),
    val text: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val confidence: Float = 0.0f
)
