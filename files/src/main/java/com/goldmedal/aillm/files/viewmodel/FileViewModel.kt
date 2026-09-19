package com.goldmedal.aillm.files.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.core.database.FileMemoryDao
import com.goldmedal.aillm.core.database.FileMemoryEntity
import com.goldmedal.aillm.files.FileTextExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileViewModel @Inject constructor(
    application: Application,
    private val fileMemoryDao: FileMemoryDao,
    private val fileTextExtractor: FileTextExtractor
) : AndroidViewModel(application) {

    private val _files = MutableStateFlow<List<FileMemoryEntity>>(emptyList())
    val files: StateFlow<List<FileMemoryEntity>> = _files.asStateFlow()

    init {
        loadFiles()
    }

    private fun loadFiles() {
        viewModelScope.launch {
            fileMemoryDao.getAllFiles().collect { fileList ->
                _files.value = fileList
            }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val fileName = getFileName(uri) ?: "unknown_file"
                val fileType = fileTextExtractor.getFileType(fileName)

                // Copy file to app storage
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val appDir = File(context.getExternalFilesDir(null), "user_files")
                if (!appDir.exists()) appDir.mkdirs()

                val outputFile = File(appDir, fileName)
                inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Extract text
                val text = fileTextExtractor.extractText(uri).getOrDefault("")
                val summary = fileTextExtractor.summarize(text)

                // Save to database
                val fileEntity = FileMemoryEntity(
                    filePath = outputFile.absolutePath,
                    fileName = fileName,
                    fileType = fileType.name.lowercase(),
                    fileSize = outputFile.length(),
                    summary = summary,
                    extractedText = text.take(10000) // Limit stored text
                )

                fileMemoryDao.insertFile(fileEntity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteFile(fileId: Long) {
        viewModelScope.launch {
            try {
                val file = fileMemoryDao.getFileById(fileId)
                if (file != null) {
                    val physicalFile = File(file.filePath)
                    if (physicalFile.exists()) {
                        physicalFile.delete()
                    }
                    fileMemoryDao.deleteFileById(fileId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        val context = getApplication<Application>()
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (nameIndex >= 0) it.getString(nameIndex) else null
        }
    }
}
