package com.goldmedal.aillm.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goldmedal.aillm.core.database.AppDatabase
import com.goldmedal.aillm.core.database.ChatDao
import com.goldmedal.aillm.core.database.FileMemoryDao
import com.goldmedal.aillm.core.database.GeneratedImageDao
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

    /**
     * The generated-image gallery's table. Purely additive: an upgrade must not
     * cost the user the conversations and models already on the device.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `generated_images` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`fileName` TEXT NOT NULL, " +
                    "`prompt` TEXT NOT NULL, " +
                    "`negativePrompt` TEXT NOT NULL, " +
                    "`modelId` TEXT NOT NULL, " +
                    "`modelName` TEXT NOT NULL, " +
                    "`width` INTEGER NOT NULL, " +
                    "`height` INTEGER NOT NULL, " +
                    "`steps` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "aillm_database"
        ).addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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

    @Provides
    fun provideGeneratedImageDao(database: AppDatabase): GeneratedImageDao =
        database.generatedImageDao()
}
