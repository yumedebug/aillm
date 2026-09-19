package com.goldmedal.aillm.ai.modelmanager

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ModelDownload(
    val name: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val type: ModelType,
    val description: String = ""
)

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun getAvailableModels(): List<ModelDownload> {
        return listOf(
            ModelDownload(
                name = "Qwen2.5-0.5B-Instruct-GGUF",
                url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                sizeBytes = 400_000_000L,
                type = ModelType.CHAT,
                description = "Qwen 2.5 0.5B - Fast, basic chat"
            ),
            ModelDownload(
                name = "Qwen2.5-1.5B-Instruct-GGUF",
                url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                sizeBytes = 1_000_000_000L,
                type = ModelType.CHAT,
                description = "Qwen 2.5 1.5B - Good balance"
            ),
            ModelDownload(
                name = "Qwen2.5-3B-Instruct-GGUF",
                url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
                sizeBytes = 2_000_000_000L,
                type = ModelType.CHAT,
                description = "Qwen 2.5 3B - High quality"
            ),
            ModelDownload(
                name = "Phi-3.5-mini-instruct-GGUF",
                url = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-GGUF/resolve/main/phi-3.5-mini-instruct-q4_k_m.gguf",
                fileName = "phi-3.5-mini-instruct-q4_k_m.gguf",
                sizeBytes = 2_200_000_000L,
                type = ModelType.CHAT,
                description = "Phi-3.5 Mini 3.8B - Strong reasoning"
            ),
            ModelDownload(
                name = "Llama-3.2-3B-Instruct-GGUF",
                url = "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct-GGUF/resolve/main/llama-3.2-3b-instruct-q4_k_m.gguf",
                fileName = "llama-3.2-3b-instruct-q4_k_m.gguf",
                sizeBytes = 2_000_000_000L,
                type = ModelType.CHAT,
                description = "Llama 3.2 3B - Best for chat"
            )
        )
    }

    suspend fun downloadModel(
        model: ModelDownload,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val outputFile = File(modelsDir, model.fileName)
            if (outputFile.exists()) {
                return@withContext Result.success(outputFile)
            }

            val request = DownloadManager.Request(Uri.parse(model.url))
                .setTitle("Downloading ${model.name}")
                .setDescription(model.description)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, "models", model.fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)

            val query = DownloadManager.Query().setFilterById(downloadId)
            var isDownloading = true

            while (isDownloading) {
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    if (total > 0) {
                        onProgress(downloaded.toFloat() / total)
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> isDownloading = false
                        DownloadManager.STATUS_FAILED -> {
                            return@withContext Result.failure(Exception("Download failed"))
                        }
                    }
                }
                cursor.close()
                kotlinx.coroutines.delay(500)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getInstalledModels(): List<File> {
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        if (!modelsDir.exists()) return emptyList()
        return modelsDir.listFiles()?.filter { it.extension == "gguf" } ?: emptyList()
    }

    fun deleteModel(fileName: String): Boolean {
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        val file = File(modelsDir, fileName)
        return file.delete()
    }

    fun getModelFile(fileName: String): File? {
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        val file = File(modelsDir, fileName)
        return if (file.exists()) file else null
    }
}
