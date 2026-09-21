package com.goldmedal.aillm.ai.imagegeneration

import android.graphics.Bitmap
import com.goldmedal.aillm.ai.engine.OnDeviceEngine

/**
 * Lifecycle (load/unload/isLoaded) comes from [OnDeviceEngine]; only the
 * generation-specific surface is declared here.
 */
interface ImageGenerationModel : OnDeviceEngine {

    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 30,
        guidanceScale: Float = 7.5f
    ): Result<Bitmap>
}
