package com.goldmedal.aillm.ai.vision

import android.net.Uri
import com.goldmedal.aillm.ai.engine.OnDeviceEngine

/**
 * Lifecycle (load/unload/isLoaded) comes from [OnDeviceEngine]; only the
 * vision-specific surface is declared here.
 */
interface VisionModel : OnDeviceEngine {

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
