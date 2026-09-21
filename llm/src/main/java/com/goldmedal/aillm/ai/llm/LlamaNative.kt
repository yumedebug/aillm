package com.goldmedal.aillm.ai.llm

/**
 * Thin JNI surface over llama.cpp.
 *
 * Loading the native library can legitimately fail (an ABI we did not build, a
 * broken install). That must never crash the app, so failure is captured in
 * [loadError] and [isAvailable] lets callers fall back to a clear message
 * instead of an `UnsatisfiedLinkError`.
 *
 * Deliberately public (not `internal`): Kotlin mangles the JVM names of internal
 * declarations, which would break the `Java_..._LlamaNative_*` symbol names the
 * C++ side exports.
 */
object LlamaNative {

    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("aillm_llama")
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
        }
    }

    val isAvailable: Boolean get() = loadError == null

    /** Registers ggml backends. Safe to call repeatedly. */
    external fun backendInit()

    /** Loads weights from disk. Returns an opaque handle, or 0 on failure. */
    external fun loadModel(path: String, maxContext: Int, threads: Int, gpuLayers: Int): Long

    external fun freeModel(handle: Long)

    /**
     * Attaches a multimodal projector (mmproj) to an already loaded model.
     * False when the file is missing, unreadable, or has no vision support.
     */
    external fun loadProjector(handle: Long, projectorPath: String): Boolean

    /** The marker libmtmd replaces with the image: "<__media__>" by default. */
    external fun defaultMarker(): String

    /**
     * Prepares a generation. Returns the prompt token count, or a negative
     * error code: -1 no model, -2 empty prompt, -3 tokenize failed,
     * -4 prompt longer than the context, -5 context creation, -6 sampler.
     */
    external fun beginGeneration(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Int
    ): Int

    /**
     * Prepares an image-conditioned generation. Returns 0 on success.
     * Negative codes: -1 no model, -5 context, -6 sampler, -10 no projector,
     * -12 image could not be decoded, -13 tokenize failed, -14 encode/eval failed.
     */
    external fun beginVisionGeneration(
        handle: Long,
        prompt: String,
        imageBytes: ByteArray,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Int
    ): Int

    /** Next streamed piece, or null when the model is done. */
    external fun nextToken(handle: Long): String?

    external fun endGeneration(handle: Long)

    external fun countTokens(handle: Long, text: String): Int

    fun describeError(code: Int): String = when (code) {
        -1 -> "No chat model is loaded."
        -2 -> "The prompt was empty."
        -3 -> "The prompt could not be tokenized by this model."
        -4 -> "This conversation is longer than the model's context window. Start a new chat or pick a model with a larger context."
        -5 -> "There was not enough memory to create the inference context."
        -6 -> "The sampler could not be created."
        -10 -> "No vision projector is loaded for this model."
        -12 -> "That image could not be read or decoded."
        -13 -> "The prompt could not be prepared for the vision model."
        -14 -> "The vision model could not process that image."
        else -> "Inference could not start (code $code)."
    }
}
