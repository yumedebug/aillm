package com.goldmedal.aillm.ai.di

import com.goldmedal.aillm.ai.prompt.PromptBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PromptModule {
    @Provides
    @Singleton
    fun providePromptBuilder(): PromptBuilder {
        return PromptBuilder()
    }
}
