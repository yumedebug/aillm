package com.goldmedal.aillm.search.di

import android.content.Context
import com.goldmedal.aillm.search.BraveSearchProvider
import com.goldmedal.aillm.search.TavilySearchProvider
import com.goldmedal.aillm.search.WebSearchManager
import com.goldmedal.aillm.search.WebSearchManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {
    @Provides
    @Singleton
    fun provideBraveSearchProvider(
        @ApplicationContext context: Context
    ): BraveSearchProvider {
        return BraveSearchProvider(context)
    }

    @Provides
    @Singleton
    fun provideTavilySearchProvider(
        @ApplicationContext context: Context
    ): TavilySearchProvider {
        return TavilySearchProvider(context)
    }

    @Provides
    @Singleton
    fun provideWebSearchManager(
        @ApplicationContext context: Context,
        braveSearchProvider: BraveSearchProvider,
        tavilySearchProvider: TavilySearchProvider
    ): WebSearchManager {
        return WebSearchManagerImpl(context, braveSearchProvider, tavilySearchProvider)
    }
}
