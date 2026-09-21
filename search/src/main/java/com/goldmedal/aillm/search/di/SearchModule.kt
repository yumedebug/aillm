package com.goldmedal.aillm.search.di

import com.goldmedal.aillm.search.NoopWebSearchClient
import com.goldmedal.aillm.search.OnlineSources
import com.goldmedal.aillm.search.WebSearchClient
import com.goldmedal.aillm.core.preferences.AppSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {

    @Provides
    @Singleton
    fun provideWebSearchClient(): WebSearchClient = NoopWebSearchClient()

    @Provides
    @Singleton
    fun provideOnlineSources(appSettings: AppSettings): OnlineSources = OnlineSources(appSettings)
}
