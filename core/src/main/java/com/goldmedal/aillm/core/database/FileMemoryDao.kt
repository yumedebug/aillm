package com.goldmedal.aillm.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileMemoryDao {
    @Query("SELECT * FROM file_memories ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<FileMemoryEntity>>

    @Query("SELECT * FROM file_memories WHERE id = :fileId")
    suspend fun getFileById(fileId: Long): FileMemoryEntity?

    @Query("SELECT * FROM file_memories WHERE fileName LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%'")
    suspend fun searchFiles(query: String): List<FileMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileMemoryEntity): Long

    @Update
    suspend fun updateFile(file: FileMemoryEntity)

    @Delete
    suspend fun deleteFile(file: FileMemoryEntity)

    @Query("DELETE FROM file_memories WHERE id = :fileId")
    suspend fun deleteFileById(fileId: Long)
}
