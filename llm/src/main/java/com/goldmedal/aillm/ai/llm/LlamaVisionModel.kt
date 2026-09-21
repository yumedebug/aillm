package com.goldmedal.aillm.ai.llm

import android.content.Context
import android.net.Uri
import com.goldmedal.aillm.ai.chat.ChatMessage
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.ai.prompt.ChatPromptFormatter
import com.goldmedal.aillm.ai.vision.ImageAnalysis
import com.goldmedal.aillm.ai.vision.VisionModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image understanding via libmtmd.
 *
 * A vision model in llama.cpp is a text model plus a projector, so this shares
 * the resident text model with [LlamaChatModel] rather than loading a second
 * copy of the weights. Loading a vision model therefore also makes it the
 * active chat model — the same weights answer both.
 */
@Singleton
class LlamaVisionModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatModel: LlamaChatModel
) : VisionModel {

    private var projectorAttached = false

    override val name: String
        get() {
            val base = chatModel.currentSpecName ?: "no model"
            return if (projectorAttached) "llama.cpp Vision · $base" else "llama.cpp Vision (idle)"
        }

    override val isLoaded: Boolean get() = projectorAttached && chatModel.isLoaded

    override suspend fun load(
        spec: ModelSpec,
        modelPath: String,
        projectorPath: String?
    ): Result<Unit> {
        if (!LlamaNative.isAvailable) {
            return Result.failure(
                IllegalStateException(
                    "The on-device inference engine could not be loaded on this device " +
                        "(${LlamaNative.loadError})."
                )
            )
        }
        if (projectorPath == null) {
            return Result.failure(
                IllegalStateException(
                    "${spec.name} cannot see images: its projector file is missing. Delete it and download again."
                )
            )
        }

        // The vision-capable GGUF is the text model itself.
        val textResult = chatModel.load(spec, modelPath)
        if (textResult.isFailure) {
            return textResult
        }

        val attached = chatModel.attachProjector(projectorPath)
        if (!attached) {
            chatModel.unload()
            return Result.failure(
                IllegalStateException(
                    "${spec.name} could not use its projector. The projector and the model may not match."
                )
            )
        }

        projectorAttached = true
        return Result.success(Unit)
    }

    override suspend fun unload(): Result<Unit> {
        // The weights live in the shared chat runtime; the repository unloads
        // them together with the chat/coding siblings.
        projectorAttached = false
        return Result.success(Unit)
    }

    override suspend fun analyzeImage(
        imageUri: Uri,
        prompt: String
    ): Result<ImageAnalysis> = analyze(prompt) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            }.getOrNull()
        }
    }

    override suspend fun analyzeImageFromPath(
        imagePath: String,
        prompt: String
    ): Result<ImageAnalysis> = analyze(prompt) {
        withContext(Dispatchers.IO) {
            runCatching { File(imagePath).takeIf { it.exists() }?.readBytes() }.getOrNull()
        }
    }

    private suspend fun analyze(
        prompt: String,
        loadBytes: suspend () -> ByteArray?
    ): Result<ImageAnalysis> {
        if (!isLoaded) {
            return Result.failure(
                IllegalStateException(
                    "No vision model is loaded. Load one from Models to describe images."
                )
            )
        }

        val bytes = loadBytes()
        if (bytes == null || bytes.isEmpty()) {
            return Result.failure(IllegalStateException("That image could not be read."))
        }

        val marker = runCatching { LlamaNative.defaultMarker() }.getOrDefault("<__media__>")
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = "You look at images the user shares. Describe only what is actually " +
                    "visible, mention any readable text, and answer the user's question directly."
            ),
            ChatMessage(role = "user", content = prompt.ifBlank { DEFAULT_PROMPT })
        )
        val formatted = ChatPromptFormatter.format(
            messages = messages,
            format = chatModel.currentChatFormat,
            imageMarker = marker
        )

        return runCatching {
            val text = chatModel
                .generateFromImage(formatted, bytes, VISION_MAX_TOKENS)
                .toList()
                .joinToString("")
                .trim()
            if (text.isEmpty()) {
                throw IllegalStateException("The vision model returned nothing for that image.")
            }
            ImageAnalysis(description = text)
        }
    }

    private companion object {
        const val VISION_MAX_TOKENS = 320
        const val DEFAULT_PROMPT = "Describe this image in detail."
    }
}
