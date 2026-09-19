package com.goldmedal.aillm.ai.imagegeneration

import android.graphics.Bitmap

interface ImageGenerationModel {
    val name: String
    val isLoaded: Boolean

    suspend fun load(): Result<Unit>
    suspend fun unload(): Result<Unit>
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 30,
        guidanceScale: Float = 7.5f
    ): Result<Bitmap>
}
