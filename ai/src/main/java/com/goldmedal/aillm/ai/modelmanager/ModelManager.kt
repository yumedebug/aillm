package com.goldmedal.aillm.ai.modelmanager

import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.imagegeneration.ImageGenerationModel
import com.goldmedal.aillm.ai.vision.VisionModel

interface ModelManager {
    val chatModel: ChatModel?
    val visionModel: VisionModel?
    val imageGenerationModel: ImageGenerationModel?

    suspend fun loadChatModel(modelName: String): Result<Unit>
    suspend fun loadVisionModel(modelName: String): Result<Unit>
    suspend fun loadImageGenerationModel(modelName: String): Result<Unit>

    suspend fun unloadChatModel(): Result<Unit>
    suspend fun unloadVisionModel(): Result<Unit>
    suspend fun unloadImageGenerationModel(): Result<Unit>

    suspend fun getInstalledModels(): List<ModelInfo>
    suspend fun getModelInfo(modelName: String): ModelInfo?

    fun getAvailableChatModels(): List<ModelInfo>
    fun getAvailableVisionModels(): List<ModelInfo>
    fun getAvailableImageGenerationModels(): List<ModelInfo>
}

data class ModelInfo(
    val name: String,
    val type: ModelType,
    val description: String,
    val sizeBytes: Long,
    val estimatedRamUsageBytes: Long,
    val contextLength: Int,
    val isInstalled: Boolean,
    val isLoaded: Boolean
)

enum class ModelType {
    CHAT,
    VISION,
    IMAGE_GENERATION
}
