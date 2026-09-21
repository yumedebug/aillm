package com.goldmedal.aillm.memory.di

import com.goldmedal.aillm.core.database.UserMemoryDao
import com.goldmedal.aillm.memory.MemoryEngine
import com.goldmedal.aillm.memory.MemoryEngineImpl
import com.goldmedal.aillm.memory.consolidation.MemoryConsolidator
import com.goldmedal.aillm.memory.embedding.SemanticSearch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {
    @Provides
    @Singleton
    fun provideMemoryEngine(
        userMemoryDao: UserMemoryDao,
        semanticSearch: SemanticSearch,
        consolidator: MemoryConsolidator
    ): MemoryEngine {
        return MemoryEngineImpl(userMemoryDao, semanticSearch, consolidator)
    }
}
