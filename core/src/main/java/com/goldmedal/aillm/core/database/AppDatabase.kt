package com.goldmedal.aillm.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        UserMemoryEntity::class,
        ImageMemoryEntity::class,
        FileMemoryEntity::class,
        InstalledModelEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun imageMemoryDao(): ImageMemoryDao
    abstract fun fileMemoryDao(): FileMemoryDao
    abstract fun installedModelDao(): InstalledModelDao
}
