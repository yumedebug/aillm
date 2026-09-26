package com.goldmedal.aillm.ai.diffusion

import android.graphics.Bitmap
import com.goldmedal.aillm.ai.imagegeneration.ImageGenerationModel
import com.goldmedal.aillm.ai.model.ModelSpec
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Real on-device image generation via stable-diffusion.cpp.
 *
 * This owns the single diffusion model that is resident. All native work runs
 * on a dedicated single thread, which keeps a multi-minute sampling loop off
 * the shared dispatcher pool and guarantees only one generation touches the
 * context at a time.
 *
 * Loading quantizes the weights to Q8_0 on the fly, which roughly halves the
 * resident memory compared with the fp16 file on disk and is what makes a
 * ~2 GB checkpoint usable on a phone.
 */
class SdImageGenerationModel : ImageGenerationModel {

    private val inferenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aillm-diffusion").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var loaded: Boolean = false

    private var loadedSpec: ModelSpec? = null

    override val name: String
        get() = loadedSpec?.let { "stable-diffusion.cpp · ${it.name}" }
            ?: "stable-diffusion.cpp (no model loaded)"

    override val isLoaded: Boolean get() = loaded

    override suspend fun load(
        spec: ModelSpec,
        modelPath: String,
        projectorPath: String?
    ): Result<Unit> = withContext(inferenceDispatcher) {
        if (!DiffusionNative.isAvailable) {
            return@withContext Result.failure(
                IllegalStateException(
                    "The on-device image engine could not be loaded on this device " +
                        "(${DiffusionNative.loadError})."
                )
            )
        }

        releaseHandle()

        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val newHandle = DiffusionNative.nativeLoad(
            path = modelPath,
            threads = threads,
            weightType = DiffusionNative.WEIGHT_TYPE_Q8_0,
            mmap = false
        )
        if (newHandle == 0L) {
            return@withContext Result.failure(
                IllegalStateException(
                    "${spec.name} could not be loaded. The file may be damaged or there may " +
                        "not be enough free memory."
                )
            )
        }

        handle = newHandle
        loadedSpec = spec
        loaded = true
        Result.success(Unit)
    }

    override suspend fun unload(): Result<Unit> = withContext(inferenceDispatcher) {
        releaseHandle()
        Result.success(Unit)
    }

    override suspend fun generateImage(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidanceScale: Float
    ): Result<Bitmap> = withContext(inferenceDispatcher) {
        val currentHandle = handle
        if (!loaded || currentHandle == 0L) {
            return@withContext Result.failure(
                IllegalStateException("No image model is loaded. Download and load one in Models.")
            )
        }

        // SD 1.5 was trained on multiples of 64; snapping avoids torn output.
        val safeWidth = snap(width)
        val safeHeight = snap(height)
        val safeSteps = steps.coerceIn(1, 100)

        val pixels = DiffusionNative.nativeGenerate(
            handle = currentHandle,
            prompt = prompt,
            negativePrompt = negativePrompt,
            width = safeWidth,
            height = safeHeight,
            steps = safeSteps,
            guidance = guidanceScale,
            seed = Random.nextLong()
        )

        val outWidth = DiffusionNative.nativeLastWidth(currentHandle)
        val outHeight = DiffusionNative.nativeLastHeight(currentHandle)
        if (pixels == null || outWidth <= 0 || outHeight <= 0 ||
            pixels.size != outWidth * outHeight
        ) {
            return@withContext Result.failure(
                IllegalStateException("The image could not be generated. Try a shorter prompt.")
            )
        }

        Result.success(Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888))
    }

    override fun setProgressListener(listener: ((step: Int, steps: Int) -> Unit)?) {
        if (!DiffusionNative.isAvailable) return
        DiffusionNative.nativeSetProgressListener(
            listener?.let { deliver ->
                DiffusionProgressListener { step, steps, _ -> deliver(step, steps) }
            }
        )
    }

    private fun releaseHandle() {
        val current = handle
        if (current != 0L) {
            runCatching { DiffusionNative.nativeFree(current) }
        }
        handle = 0L
        loaded = false
        loadedSpec = null
    }

    private fun snap(value: Int): Int {
        val clamped = value.coerceIn(MIN_SIZE, MAX_SIZE)
        return (clamped / STEP) * STEP
    }

    companion object {
        private const val STEP = 64
        private const val MIN_SIZE = 256
        private const val MAX_SIZE = 768
    }
}
