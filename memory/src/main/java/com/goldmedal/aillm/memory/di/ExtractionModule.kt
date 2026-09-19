package com.goldmedal.aillm.memory.di

import com.goldmedal.aillm.memory.extraction.MemoryExtractor
import com.goldmedal.aillm.memory.extraction.MemoryExtractorImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExtractionModule {
    @Provides
    @Singleton
    fun provideMemoryExtractor(): MemoryExtractor {
        return MemoryExtractorImpl()
    }
}
