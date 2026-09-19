package com.goldmedal.aillm.memory.di

import com.goldmedal.aillm.core.database.UserMemoryDao
import com.goldmedal.aillm.memory.MemoryEngine
import com.goldmedal.aillm.memory.MemoryEngineImpl
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
    fun provideMemoryEngine(userMemoryDao: UserMemoryDao): MemoryEngine {
        return MemoryEngineImpl(userMemoryDao)
    }
}
