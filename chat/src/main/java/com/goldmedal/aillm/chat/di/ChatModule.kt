package com.goldmedal.aillm.chat.di

import com.goldmedal.aillm.chat.repository.ChatRepository
import com.goldmedal.aillm.core.database.ChatDao
import com.goldmedal.aillm.core.database.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {
    @Provides
    @Singleton
    fun provideChatRepository(
        chatDao: ChatDao,
        messageDao: MessageDao
    ): ChatRepository {
        return ChatRepository(chatDao, messageDao)
    }
}
