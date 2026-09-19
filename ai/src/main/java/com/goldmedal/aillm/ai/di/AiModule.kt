package com.goldmedal.aillm.ai.di

import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.chat.StubChatModel
import com.goldmedal.aillm.ai.imagegeneration.ImageGenerationModel
import com.goldmedal.aillm.ai.modelmanager.ModelManager
import com.goldmedal.aillm.ai.modelmanager.ModelManagerImpl
import com.goldmedal.aillm.ai.vision.StubVisionModel
import com.goldmedal.aillm.ai.vision.VisionModel
import com.goldmedal.aillm.ai.imagegeneration.StubImageGenerationModel
import com.goldmedal.aillm.core.database.ModelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideChatModel(): ChatModel {
        return StubChatModel()
    }

    @Provides
    @Singleton
    fun provideVisionModel(): VisionModel {
        return StubVisionModel()
    }

    @Provides
    @Singleton
    fun provideImageGenerationModel(): ImageGenerationModel {
        return StubImageGenerationModel()
    }

    @Provides
    @Singleton
    fun provideModelManager(
        modelDao: ModelDao,
        chatModel: ChatModel,
        visionModel: VisionModel,
        imageGenerationModel: ImageGenerationModel
    ): ModelManager {
        val chatModelMap = mapOf(chatModel.name to chatModel)
        val visionModelMap = mapOf(visionModel.name to visionModel)
        val imageGenerationModelMap = mapOf(imageGenerationModel.name to imageGenerationModel)
        return ModelManagerImpl(modelDao, chatModelMap, visionModelMap, imageGenerationModelMap)
    }
}
