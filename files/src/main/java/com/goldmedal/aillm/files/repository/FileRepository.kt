package com.goldmedal.aillm.files.repository

import android.content.Context
import com.goldmedal.aillm.core.database.FileMemoryDao
import com.goldmedal.aillm.core.database.FileMemoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileMemoryDao: FileMemoryDao
) {
    fun getAllFiles(): Flow<List<FileMemoryEntity>> = fileMemoryDao.getAllFiles()

    suspend fun getFileById(fileId: Long): FileMemoryEntity? = fileMemoryDao.getFileById(fileId)

    suspend fun searchFiles(query: String): List<FileMemoryEntity> = fileMemoryDao.searchFiles(query)

    suspend fun saveFile(fileName: String, content: ByteArray): Result<Long> {
        return try {
            val filesDir = File(context.filesDir, "user_files")
            if (!filesDir.exists()) {
                filesDir.mkdirs()
            }

            val file = File(filesDir, fileName)
            file.writeBytes(content)

            val fileEntity = FileMemoryEntity(
                filePath = file.absolutePath,
                fileName = fileName,
                fileType = getFileExtension(fileName),
                fileSize = content.size.toLong()
            )

            val id = fileMemoryDao.insertFile(fileEntity)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveTextFile(fileName: String, content: String): Result<Long> {
        return saveFile(fileName, content.toByteArray())
    }

    suspend fun readFile(fileId: Long): Result<String> {
        return try {
            val fileEntity = fileMemoryDao.getFileById(fileId)
                ?: return Result.failure(Exception("File not found"))

            val file = File(fileEntity.filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File does not exist"))
            }

            val content = file.readText()
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(fileId: Long): Result<Unit> {
        return try {
            val fileEntity = fileMemoryDao.getFileById(fileId)
            if (fileEntity != null) {
                val file = File(fileEntity.filePath)
                if (file.exists()) {
                    file.delete()
                }
                fileMemoryDao.deleteFileById(fileId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "unknown")
    }
}
