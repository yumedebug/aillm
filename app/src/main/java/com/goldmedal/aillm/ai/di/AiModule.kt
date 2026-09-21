package com.goldmedal.aillm.ai.di

import android.content.Context
import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.imagegeneration.ImageGenerationModel
import com.goldmedal.aillm.ai.imagegeneration.StubImageGenerationModel
import com.goldmedal.aillm.ai.llm.LlamaChatModel
import com.goldmedal.aillm.ai.llm.LlamaEmbeddingModel
import com.goldmedal.aillm.ai.llm.LlamaVisionModel
import com.goldmedal.aillm.ai.model.ModelDownloader
import com.goldmedal.aillm.ai.model.ModelRepository
import com.goldmedal.aillm.ai.model.ModelRepositoryImpl
import com.goldmedal.aillm.ai.vision.VisionModel
import com.goldmedal.aillm.core.database.InstalledModelDao
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

    /**
     * The chat runtime is also what vision runs on (a multimodal model is a text
     * model plus a projector), so both interfaces must resolve to the same
     * instance. LlamaChatModel is a @Singleton with an @Inject constructor, so
     * every injection site below receives that one instance.
     */
    @Provides
    @Singleton
    fun provideChatModel(impl: LlamaChatModel): ChatModel = impl

    @Provides
    @Singleton
    fun provideVisionModel(
        @ApplicationContext context: Context,
        chatModel: LlamaChatModel
    ): VisionModel = LlamaVisionModel(context, chatModel)

    @Provides
    @Singleton
    fun provideImageGenerationModel(): ImageGenerationModel = StubImageGenerationModel()

    @Provides
    @Singleton
    fun provideEmbeddingModel(
        @ApplicationContext context: Context
    ): EmbeddingModel = LlamaEmbeddingModel(context)

    @Provides
    @Singleton
    fun provideModelRepository(
        @ApplicationContext context: Context,
        installedModelDao: InstalledModelDao,
        downloader: ModelDownloader,
        chatModel: ChatModel,
        visionModel: VisionModel,
        imageGenerationModel: ImageGenerationModel
    ): ModelRepository = ModelRepositoryImpl(
        context = context,
        installedModelDao = installedModelDao,
        downloader = downloader,
        chatModel = chatModel,
        visionModel = visionModel,
        imageGenerationModel = imageGenerationModel
    )
}
