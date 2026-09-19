package com.goldmedal.aillm.files

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun extractText(uri: Uri): Result<String> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))

            val text = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractTextFromPath(filePath: String): Result<String> {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found"))
            }

            val text = file.readText()
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFileType(fileName: String): FileType {
        return when {
            fileName.endsWith(".txt", true) -> FileType.TEXT
            fileName.endsWith(".md", true) -> FileType.MARKDOWN
            fileName.endsWith(".json", true) -> FileType.JSON
            fileName.endsWith(".xml", true) -> FileType.XML
            fileName.endsWith(".csv", true) -> FileType.CSV
            fileName.endsWith(".pdf", true) -> FileType.PDF
            fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) -> FileType.DOCUMENT
            else -> FileType.UNKNOWN
        }
    }

    fun summarize(text: String, maxLength: Int = 500): String {
        if (text.length <= maxLength) return text

        val truncated = text.take(maxLength)
        val lastSentence = truncated.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (lastSentence > maxLength / 2) {
            truncated.take(lastSentence + 1)
        } else {
            truncated + "..."
        }
    }
}

enum class FileType {
    TEXT, MARKDOWN, JSON, XML, CSV, PDF, DOCUMENT, UNKNOWN
}
