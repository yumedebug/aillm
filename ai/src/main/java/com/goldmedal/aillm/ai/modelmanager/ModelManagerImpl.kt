package com.goldmedal.aillm.ai.modelmanager

import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.imagegeneration.ImageGenerationModel
import com.goldmedal.aillm.ai.vision.VisionModel
import com.goldmedal.aillm.core.database.ModelDao
import com.goldmedal.aillm.core.database.ModelEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManagerImpl @Inject constructor(
    private val modelDao: ModelDao,
    private val chatModels: Map<String, @JvmSuppressWildcards ChatModel>,
    private val visionModels: Map<String, @JvmSuppressWildcards VisionModel>,
    private val imageGenerationModels: Map<String, @JvmSuppressWildcards ImageGenerationModel>
) : ModelManager {

    private var _chatModel: ChatModel? = null
    private var _visionModel: VisionModel? = null
    private var _imageGenerationModel: ImageGenerationModel? = null

    override val chatModel: ChatModel? get() = _chatModel
    override val visionModel: VisionModel? get() = _visionModel
    override val imageGenerationModel: ImageGenerationModel? get() = _imageGenerationModel

    override suspend fun loadChatModel(modelName: String): Result<Unit> {
        return try {
            val model = chatModels[modelName] ?: return Result.failure(Exception("Chat model not found: $modelName"))
            if (_chatModel?.isLoaded == true) {
                _chatModel?.unload()
            }
            val result = model.load()
            if (result.isSuccess) {
                _chatModel = model
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadVisionModel(modelName: String): Result<Unit> {
        return try {
            val model = visionModels[modelName] ?: return Result.failure(Exception("Vision model not found: $modelName"))
            if (_visionModel?.isLoaded == true) {
                _visionModel?.unload()
            }
            val result = model.load()
            if (result.isSuccess) {
                _visionModel = model
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadImageGenerationModel(modelName: String): Result<Unit> {
        return try {
            val model = imageGenerationModels[modelName] ?: return Result.failure(Exception("Image generation model not found: $modelName"))
            if (_imageGenerationModel?.isLoaded == true) {
                _imageGenerationModel?.unload()
            }
            val result = model.load()
            if (result.isSuccess) {
                _imageGenerationModel = model
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unloadChatModel(): Result<Unit> {
        return try {
            _chatModel?.unload()
            _chatModel = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unloadVisionModel(): Result<Unit> {
        return try {
            _visionModel?.unload()
            _visionModel = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unloadImageGenerationModel(): Result<Unit> {
        return try {
            _imageGenerationModel?.unload()
            _imageGenerationModel = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getInstalledModels(): List<ModelInfo> {
        return getAvailableChatModels() + getAvailableVisionModels() + getAvailableImageGenerationModels()
    }

    override suspend fun getModelInfo(modelName: String): ModelInfo? {
        return getInstalledModels().find { it.name == modelName }
    }

    override fun getAvailableChatModels(): List<ModelInfo> {
        return chatModels.map { (name, model) ->
            ModelInfo(
                name = name,
                type = ModelType.CHAT,
                description = "Chat model: $name",
                sizeBytes = 0,
                estimatedRamUsageBytes = 0,
                contextLength = model.contextLength,
                isInstalled = true,
                isLoaded = model.isLoaded
            )
        }
    }

    override fun getAvailableVisionModels(): List<ModelInfo> {
        return visionModels.map { (name, model) ->
            ModelInfo(
                name = name,
                type = ModelType.VISION,
                description = "Vision model: $name",
                sizeBytes = 0,
                estimatedRamUsageBytes = 0,
                contextLength = 0,
                isInstalled = true,
                isLoaded = model.isLoaded
            )
        }
    }

    override fun getAvailableImageGenerationModels(): List<ModelInfo> {
        return imageGenerationModels.map { (name, model) ->
            ModelInfo(
                name = name,
                type = ModelType.IMAGE_GENERATION,
                description = "Image generation model: $name",
                sizeBytes = 0,
                estimatedRamUsageBytes = 0,
                contextLength = 0,
                isInstalled = true,
                isLoaded = model.isLoaded
            )
        }
    }
}
