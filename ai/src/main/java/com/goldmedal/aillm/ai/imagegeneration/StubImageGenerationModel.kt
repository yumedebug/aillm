package com.goldmedal.aillm.ai.imagegeneration

import android.graphics.Bitmap
import com.goldmedal.aillm.ai.model.ModelKind
import com.goldmedal.aillm.ai.model.ModelSpec

/**
 * Placeholder image-generation engine.
 *
 * An image model can be downloaded and tracked like any other, but until a
 * diffusion runtime is wired up this engine refuses to run rather than
 * returning a fake picture. Failing loudly is honest; a mock image would not be.
 */
class StubImageGenerationModel : ImageGenerationModel {

    override val name: String = "Image generation (not available yet)"
    override val isLoaded: Boolean = false

    override suspend fun load(
        spec: ModelSpec,
        modelPath: String,
        projectorPath: String?
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException(
            "On-device image generation is not wired up yet. ${spec.name} is stored but cannot run."
        )
    )

    override suspend fun unload(): Result<Unit> = Result.success(Unit)

    override suspend fun generateImage(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidanceScale: Float
    ): Result<Bitmap> = Result.failure(
        UnsupportedOperationException("On-device image generation is not wired up yet.")
    )

    companion object {
        val supportedKind: ModelKind = ModelKind.IMAGE_GENERATION
    }
}
