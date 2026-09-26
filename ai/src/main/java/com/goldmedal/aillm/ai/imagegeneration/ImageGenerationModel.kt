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

    /**
     * Optional progress reporting: [listener] is called with `(step, totalSteps)`
     * while an image is sampled, and `null` clears it. Engines that cannot
     * report progress simply ignore the call.
     */
    fun setProgressListener(listener: ((step: Int, steps: Int) -> Unit)?) = Unit
}
