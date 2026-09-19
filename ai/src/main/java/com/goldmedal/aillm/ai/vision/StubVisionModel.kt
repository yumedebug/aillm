package com.goldmedal.aillm.ai.vision

import android.net.Uri
import kotlinx.coroutines.delay

class StubVisionModel : VisionModel {
    override val name: String = "Stub Vision Model"
    override val isLoaded: Boolean = true

    override suspend fun load(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun unload(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun analyzeImage(
        imageUri: Uri,
        prompt: String
    ): Result<ImageAnalysis> {
        delay(1000)
        return Result.success(
            ImageAnalysis(
                description = "This is a stub vision analysis. In production, this would analyze the actual image using a vision LLM model.",
                objects = listOf("image"),
                text = emptyList(),
                tags = listOf("stub", "vision"),
                confidence = 0.5f
            )
        )
    }

    override suspend fun analyzeImageFromPath(
        imagePath: String,
        prompt: String
    ): Result<ImageAnalysis> {
        delay(1000)
        return Result.success(
            ImageAnalysis(
                description = "This is a stub vision analysis for image at path: $imagePath",
                objects = listOf("image"),
                text = emptyList(),
                tags = listOf("stub", "vision"),
                confidence = 0.5f
            )
        )
    }
}
