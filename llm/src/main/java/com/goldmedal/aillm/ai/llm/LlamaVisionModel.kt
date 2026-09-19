package com.goldmedal.aillm.ai.llm

import android.content.Context
import android.net.Uri
import com.goldmedal.aillm.ai.vision.ImageAnalysis
import com.goldmedal.aillm.ai.vision.VisionModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaVisionModel @Inject constructor(
    @ApplicationContext private val context: Context
) : VisionModel {

    private var model: Long = 0
    private var config: LlamaConfig? = null
    private var _isLoaded = false

    override val name: String = "llama.cpp Vision"
    override val isLoaded: Boolean get() = _isLoaded

    override suspend fun load(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val modelFile = modelsDir.listFiles()?.firstOrNull {
                it.extension == "gguf" && it.name.contains("vision", ignoreCase = true)
            }

            if (modelFile == null) {
                return@withContext Result.failure(Exception("No GGUF vision model found in ${modelsDir.absolutePath}"))
            }

            val mmprojFile = modelsDir.listFiles()?.firstOrNull {
                it.extension == "gguf" && it.name.contains("mmproj", ignoreCase = true)
            }

            val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            config = LlamaConfig(
                contextSize = 4096,
                threads = threadCount,
                temperature = 0.7f,
                maxTokens = 1024
            )

            model = Llama.loadModel(modelFile.absolutePath, config!!)
            _isLoaded = true
            Result.success(Unit)
        } catch (e: Exception) {
            _isLoaded = false
            Result.failure(e)
        }
    }

    override suspend fun unload(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (model != 0L) {
                Llama.releaseModel(model)
                model = 0
            }
            _isLoaded = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun analyzeImage(
        imageUri: Uri,
        prompt: String
    ): Result<ImageAnalysis> = withContext(Dispatchers.Default) {
        try {
            if (!_isLoaded || model == 0L) {
                return@withContext Result.failure(Exception("Vision model not loaded"))
            }

            val result = Llama.complete(
                model,
                prompt = "Describe this image in detail. Include: objects, text, scene, context.\n\nUser request: $prompt",
                maxTokens = 1024
            )

            Result.success(
                ImageAnalysis(
                    description = result.text,
                    objects = extractObjects(result.text),
                    text = extractText(result.text),
                    tags = extractTags(result.text),
                    confidence = 0.8f
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun analyzeImageFromPath(
        imagePath: String,
        prompt: String
    ): Result<ImageAnalysis> = withContext(Dispatchers.Default) {
        try {
            if (!_isLoaded || model == 0L) {
                return@withContext Result.failure(Exception("Vision model not loaded"))
            }

            val result = Llama.complete(
                model,
                prompt = "Describe this image from path: $imagePath in detail.\n\nUser request: $prompt",
                maxTokens = 1024
            )

            Result.success(
                ImageAnalysis(
                    description = result.text,
                    objects = extractObjects(result.text),
                    text = extractText(result.text),
                    tags = extractTags(result.text),
                    confidence = 0.7f
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractObjects(text: String): List<String> {
        val objects = mutableListOf<String>()
        val patterns = listOf(
            Regex("(?:I see|There is|visible|contains?)\\s+(.+?)(?:\\.|,|\\n)"),
            Regex("(?:object|item|thing)[:\\s]+(.+?)(?:\\.|,|\\n)")
        )
        for (pattern in patterns) {
            pattern.findAll(text).forEach {
                objects.add(it.groupValues[1].trim())
            }
        }
        return objects.distinct().take(10)
    }

    private fun extractText(text: String): List<String> {
        val texts = mutableListOf<String>()
        val pattern = Regex("(?:text says?|reading|written|displays?|shows?)[:\\s]+(.+?)(?:\\.|,|\\n)")
        pattern.findAll(text).forEach {
            texts.add(it.groupValues[1].trim())
        }
        return texts.distinct()
    }

    private fun extractTags(text: String): List<String> {
        val tags = mutableListOf<String>()
        val keywords = listOf(
            "photo", "image", "picture", "screenshot", "document",
            "person", "people", "face", "animal", "cat", "dog",
            "building", "house", "car", "tree", "sky", "water",
            "food", "drink", "book", "screen", "computer", "phone"
        )
        val lowerText = text.lowercase()
        for (keyword in keywords) {
            if (lowerText.contains(keyword)) {
                tags.add(keyword)
            }
        }
        return tags
    }

    fun getModelDir(): File {
        return File(context.getExternalFilesDir(null), "models").also {
            if (!it.exists()) it.mkdirs()
        }
    }
}
