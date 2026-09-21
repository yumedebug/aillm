package com.goldmedal.aillm.ai.llm

import com.goldmedal.aillm.ai.chat.ChatMessage
import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.ai.prompt.ChatPromptFormat
import com.goldmedal.aillm.ai.prompt.ChatPromptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Real on-device inference via llama.cpp.
 *
 * This owns the single text model that is resident. All native work runs on a
 * dedicated single thread, which keeps inference off the shared dispatcher pool
 * and guarantees only one generation can touch the handle at a time.
 *
 * A multimodal (vision) model *is* a text model plus a projector, so
 * [attachProjector] bolts sight onto this same handle instead of loading a
 * second copy of the weights.
 */
@Singleton
class LlamaChatModel @Inject constructor() : ChatModel {

    private val inferenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aillm-inference").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var handle: Long = 0L
    private var loadedSpec: ModelSpec? = null
    private var chatFormat: ChatPromptFormat = ChatPromptFormat.PLAIN
    private var loadedContextLength: Int = 4096
    private var projectorAttached: Boolean = false

    private var loaded = false

    override val name: String
        get() {
            val base = loadedSpec?.let { "llama.cpp · ${it.name}" } ?: "llama.cpp (no model loaded)"
            return if (projectorAttached) "$base + vision" else base
        }

    override val isLoaded: Boolean get() = loaded

    override val contextLength: Int get() = loadedContextLength

    /** The template the resident model expects. */
    val currentChatFormat: ChatPromptFormat get() = chatFormat

    /** Display name of the resident model, if any. */
    val currentSpecName: String? get() = loadedSpec?.name

    /** True when a multimodal projector is attached to the resident model. */
    val hasProjector: Boolean get() = projectorAttached

    // ---- lifecycle ----

    override suspend fun load(
        spec: ModelSpec,
        modelPath: String,
        projectorPath: String?
    ): Result<Unit> = withContext(inferenceDispatcher) {
        if (!LlamaNative.isAvailable) {
            return@withContext Result.failure(
                IllegalStateException(
                    "The on-device inference engine could not be loaded on this device " +
                        "(${LlamaNative.loadError})."
                )
            )
        }

        releaseHandle()
        LlamaNative.backendInit()

        // The advertised context is often far larger than a phone can afford, so
        // the KV cache is capped; the native side still grows it per request.
        val maxContext = spec.contextLength.takeIf { it > 0 }?.coerceAtMost(MAX_CONTEXT) ?: 4096
        val newHandle = LlamaNative.loadModel(
            path = modelPath,
            maxContext = maxContext,
            threads = defaultThreads(),
            gpuLayers = 0
        )
        if (newHandle == 0L) {
            return@withContext Result.failure(
                IllegalStateException("${spec.name} could not be loaded. The file may be damaged.")
            )
        }

        handle = newHandle
        loadedSpec = spec
        chatFormat = spec.chatFormat
        loadedContextLength = maxContext
        loaded = true
        Result.success(Unit)
    }

    override suspend fun unload(): Result<Unit> = withContext(inferenceDispatcher) {
        releaseHandle()
        Result.success(Unit)
    }

    /** Attaches a multimodal projector to the resident model. */
    suspend fun attachProjector(projectorPath: String): Boolean = withContext(inferenceDispatcher) {
        val currentHandle = handle
        if (!loaded || currentHandle == 0L || !LlamaNative.isAvailable) {
            return@withContext false
        }
        val attached = runCatching { LlamaNative.loadProjector(currentHandle, projectorPath) }
            .getOrDefault(false)
        projectorAttached = attached
        attached
    }

    // ---- generation ----

    override suspend fun generate(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = runCatching {
        generateStream(messages, temperature, maxTokens).toList().joinToString("")
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> {
        val prompt = ChatPromptFormatter.format(messages, chatFormat)
        return streamFrom { currentHandle ->
            val code = LlamaNative.beginGeneration(
                handle = currentHandle,
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = TOP_P,
                topK = TOP_K,
                seed = Random.nextInt()
            )
            // beginGeneration returns the prompt token count on success.
            if (code >= 0) 0 else code
        }
    }

    /**
     * Generates a reply conditioned on an image. [prompt] must already contain
     * the media marker (see [LlamaNative.defaultMarker]) where the image goes.
     */
    fun generateFromImage(
        prompt: String,
        imageBytes: ByteArray,
        maxTokens: Int
    ): Flow<String> = streamFrom { currentHandle ->
        LlamaNative.beginVisionGeneration(
            handle = currentHandle,
            prompt = prompt,
            imageBytes = imageBytes,
            maxTokens = maxTokens,
            temperature = VISION_TEMPERATURE,
            topP = TOP_P,
            topK = TOP_K,
            seed = Random.nextInt()
        )
    }

    /**
     * Shared streaming loop. [begin] returns false / a non-zero code when the
     * native side refused to start, in which case the flow fails with a message
     * the UI can show.
     */
    private fun streamFrom(begin: (Long) -> Int): Flow<String> = flow {
        val currentHandle = handle
        check(loaded && currentHandle != 0L) { "No model is loaded. Download and load one in Models." }
        val startCode = begin(currentHandle)
        check(startCode == 0) { LlamaNative.describeError(startCode) }
        try {
            while (true) {
                val piece = LlamaNative.nextToken(currentHandle) ?: break
                emit(piece)
            }
        } finally {
            // Runs on cancellation too, so stopping a reply always frees the
            // context instead of leaking it.
            LlamaNative.endGeneration(currentHandle)
        }
    }.flowOn(inferenceDispatcher)

    // ---- helpers ----

    private fun releaseHandle() {
        if (handle != 0L) {
            runCatching { LlamaNative.endGeneration(handle) }
            runCatching { LlamaNative.freeModel(handle) }
        }
        handle = 0L
        loadedSpec = null
        loaded = false
        projectorAttached = false
        loadedContextLength = 4096
    }

    /** Rough diagnostics only: token count of [text] under the resident model. */
    fun countTokens(text: String): Int =
        if (handle != 0L && LlamaNative.isAvailable) {
            runCatching { LlamaNative.countTokens(handle, text) }.getOrDefault(-1)
        } else {
            -1
        }

    private fun defaultThreads(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    companion object {
        private const val MAX_CONTEXT = 8192
        private const val TOP_P = 0.95f
        private const val TOP_K = 40
        private const val VISION_TEMPERATURE = 0.2f
    }
}
