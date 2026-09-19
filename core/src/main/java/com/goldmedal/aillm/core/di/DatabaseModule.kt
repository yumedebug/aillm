package com.goldmedal.aillm.core.di

import android.content.Context
import androidx.room.Room
import com.goldmedal.aillm.core.database.AppDatabase
import com.goldmedal.aillm.core.database.ChatDao
import com.goldmedal.aillm.core.database.FileMemoryDao
import com.goldmedal.aillm.core.database.ImageMemoryDao
import com.goldmedal.aillm.core.database.MessageDao
import com.goldmedal.aillm.core.database.ModelDao
import com.goldmedal.aillm.core.database.UserMemoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "aillm_database"
        ).build()
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideUserMemoryDao(database: AppDatabase): UserMemoryDao = database.userMemoryDao()

    @Provides
    fun provideImageMemoryDao(database: AppDatabase): ImageMemoryDao = database.imageMemoryDao()

    @Provides
    fun provideFileMemoryDao(database: AppDatabase): FileMemoryDao = database.fileMemoryDao()

    @Provides
    fun provideModelDao(database: AppDatabase): ModelDao = database.modelDao()
}
