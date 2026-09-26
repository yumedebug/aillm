package com.goldmedal.aillm.ai.diffusion

/**
 * Progress reported by the diffusion engine while an image is being sampled.
 * Called from the engine's own worker thread.
 */
fun interface DiffusionProgressListener {
    fun onProgress(step: Int, steps: Int, time: Float)
}

/**
 * Thin JNI surface over stable-diffusion.cpp.
 *
 * Loading the native library can legitimately fail (an ABI we did not build, a
 * broken install). That must never crash the app, so failure is captured in
 * [loadError] and [isAvailable] lets the engine fail with a clear message
 * instead of an `UnsatisfiedLinkError`.
 *
 * Deliberately public (not `internal`): Kotlin mangles the JVM names of internal
 * declarations, which would break the `Java_..._DiffusionNative_*` symbols the
 * C++ side exports.
 */
object DiffusionNative {

    /** Same value as `SD_TYPE_Q8_0` in stable-diffusion.h. */
    const val WEIGHT_TYPE_Q8_0 = 8

    /** `SD_TYPE_COUNT`: keep the weights exactly as they are in the file. */
    const val WEIGHT_TYPE_AS_IS = 45

    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("aillm_diffusion")
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
        }
    }

    val isAvailable: Boolean get() = loadError == null

    /** Loads weights from disk. Returns an opaque handle, or 0 on failure. */
    external fun nativeLoad(path: String, threads: Int, weightType: Int, mmap: Boolean): Long

    external fun nativeFree(handle: Long)

    /** Asks a generation in progress to stop as soon as possible. */
    external fun nativeCancel(handle: Long)

    /**
     * Generates one image and returns its ARGB pixels, or null if sampling
     * failed. The dimensions are read back with [nativeLastWidth] /
     * [nativeLastHeight] because the engine snaps them to a valid multiple.
     */
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidance: Float,
        seed: Long
    ): IntArray?

    external fun nativeLastWidth(handle: Long): Int

    external fun nativeLastHeight(handle: Long): Int

    /** Registers (or clears) the single global progress listener. */
    external fun nativeSetProgressListener(listener: DiffusionProgressListener?)
}
