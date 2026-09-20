package com.goldmedal.aillm.ai.di

import android.content.Context
import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.imagegeneration.ImageGenerationModel
import com.goldmedal.aillm.ai.llm.LlamaChatModel
import com.goldmedal.aillm.ai.llm.LlamaEmbeddingModel
import com.goldmedal.aillm.ai.llm.LlamaVisionModel
import com.goldmedal.aillm.ai.modelmanager.ModelManager
import com.goldmedal.aillm.ai.modelmanager.ModelManagerImpl
import com.goldmedal.aillm.ai.vision.StubImageGenerationModel
import com.goldmedal.aillm.ai.vision.StubVisionModel
import com.goldmedal.aillm.ai.vision.VisionModel
import com.goldmedal.aillm.core.database.ModelDao
import com.goldmedal.aillm.memory.embedding.EmbeddingModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideLlamaChatModel(
        @ApplicationContext context: Context
    ): LlamaChatModel {
        return LlamaChatModel(context)
    }

    @Provides
    @Singleton
    fun provideLlamaVisionModel(
        @ApplicationContext context: Context
    ): LlamaVisionModel {
        return LlamaVisionModel(context)
    }

    @Provides
    @Singleton
    fun provideLlamaEmbeddingModel(
        @ApplicationContext context: Context
    ): LlamaEmbeddingModel {
        return LlamaEmbeddingModel(context)
    }

    @Provides
    @Singleton
    fun provideChatModel(
        llamaChatModel: LlamaChatModel
    ): ChatModel {
        return llamaChatModel
    }

    @Provides
    @Singleton
    fun provideVisionModel(
        llamaVisionModel: LlamaVisionModel
    ): VisionModel {
        return llamaVisionModel
    }

    @Provides
    @Singleton
    fun provideImageGenerationModel(): ImageGenerationModel {
        return StubImageGenerationModel()
    }

    @Provides
    @Singleton
    fun provideEmbeddingModel(
        llamaEmbeddingModel: LlamaEmbeddingModel
    ): EmbeddingModel {
        return llamaEmbeddingModel
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
