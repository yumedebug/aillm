package com.goldmedal.aillm.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goldmedal.aillm.core.database.AppDatabase
import com.goldmedal.aillm.core.database.ChatDao
import com.goldmedal.aillm.core.database.FileMemoryDao
import com.goldmedal.aillm.core.database.ImageMemoryDao
import com.goldmedal.aillm.core.database.InstalledModelDao
import com.goldmedal.aillm.core.database.MessageDao
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

    /**
     * Messages gained attached-document columns. Migrating in place rather than
     * letting the destructive fallback run keeps the installed-model registry
     * (and every saved conversation) intact across the upgrade.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN documentPath TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN documentName TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "aillm_database"
        ).addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
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
    fun provideInstalledModelDao(database: AppDatabase): InstalledModelDao =
        database.installedModelDao()
}
