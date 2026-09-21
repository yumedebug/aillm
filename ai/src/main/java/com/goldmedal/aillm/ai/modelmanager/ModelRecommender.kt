package com.goldmedal.aillm.ai.modelmanager

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Device-aware model recommendations.
 *
 * Reads the ACTUAL hardware (RAM, free storage, CPU cores) and answers the
 * one question that matters for local-first AI: "which 3 models will run
 * well on THIS phone?" — then ranks them per category (chat / coding ...)
 * so the user just picks a favorite. Downloads are served from Hugging Face
 * and installed inside the app (no browser, no external store).
 */
enum class ModelCategory(val label: String) {
    CHAT("Chat"),
    CODING("Coding"),
    VISION("Vision")
}

data class RecommendedModel(
    val name: String,
    val category: ModelCategory,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val description: String
)

data class ModelRecommendationBundle(
    val deviceLabel: String,
    val ramBytes: Long,
    val freeStorageBytes: Long,
    val cpuCores: Int,
    val models: List<RecommendedModel>
)

@Singleton
class ModelRecommender @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun recommendForThisDevice(): ModelRecommendationBundle {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val ramBytes = mem.totalMem

        val storageRoot = context.getExternalFilesDir(null)?.absolutePath
            ?: Environment.getDataDirectory().absolutePath
        val freeStorageBytes = try {
            val sf = StatFs(storageRoot)
            sf.availableBytes
        } catch (e: Exception) {
            0L
        }
        val cpuCores = Runtime.getRuntime().availableProcessors()

        val label = when {
            ramBytes >= 5_500_000_000L && cpuCores >= 8 -> "Performance"
            ramBytes >= 2_800_000_000L -> "Balanced"
            else -> "Light"
        }

        val models = buildList {
            add(chatModel(ramBytes, freeStorageBytes))
            add(codingModel(ramBytes, freeStorageBytes))
            add(visionModel(freeStorageBytes))
        }

        return ModelRecommendationBundle(
            deviceLabel = label,
            ramBytes = ramBytes,
            freeStorageBytes = freeStorageBytes,
            cpuCores = cpuCores,
            models = models
        )
    }

    private fun tier(ramBytes: Long): Int = when {
        ramBytes >= 5_500_000_000L -> 3
        ramBytes >= 2_800_000_000L -> 2
        else -> 1
    }

    private fun chatModel(ramBytes: Long, freeBytes: Long): RecommendedModel {
        val t = tier(ramBytes)
        return when {
            freeBytes < 1_200_000_000L || t == 1 -> RecommendedModel(
                name = "Gemma-2-2B-it-GGUF",
                category = ModelCategory.CHAT,
                url = "https://huggingface.co/google/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-q4_k_m.gguf",
                fileName = "gemma-2-2b-it-q4_k_m.gguf",
                sizeBytes = 1_600_000_000L,
                description = "Google Gemma 2 2B — small & friendly for light phones"
            )
            t == 2 -> RecommendedModel(
                name = "Qwen2.5-3B-Instruct-GGUF",
                category = ModelCategory.CHAT,
                url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
                sizeBytes = 2_000_000_000L,
                description = "Qwen 2.5 3B — best small general chat assistant"
            )
            else -> RecommendedModel(
                name = "Qwen2.5-7B-Instruct-GGUF",
                category = ModelCategory.CHAT,
                url = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
                sizeBytes = 4_700_000_000L,
                description = "Qwen 2.5 7B — high-quality general chat on powerful phones"
            )
        }
    }

    private fun codingModel(ramBytes: Long, freeBytes: Long): RecommendedModel {
        val t = tier(ramBytes)
        return when {
            freeBytes < 1_200_000_000L || t == 1 -> RecommendedModel(
                name = "Qwen2.5-Coder-1.5B",
                category = ModelCategory.CODING,
                url = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                sizeBytes = 1_000_000_000L,
                description = "Qwen Coder 1.5B — handy code assist for any phone"
            )
            t == 2 -> RecommendedModel(
                name = "Qwen2.5-Coder-3B",
                category = ModelCategory.CODING,
                url = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
                sizeBytes = 2_000_000_000L,
                description = "Qwen Coder 3B — real code editing on mid-range phones"
            )
            else -> RecommendedModel(
                name = "CodeLlama-7B",
                category = ModelCategory.CODING,
                url = "https://huggingface.co/TheBloke/CodeLlama-7B-GGUF/resolve/main/codellama-7b-q4_k_m.gguf",
                fileName = "codellama-7b-q4_k_m.gguf",
                sizeBytes = 3_800_000_000L,
                description = "Code Llama 7B — serious coding on flagship hardware"
            )
        }
    }

    private fun visionModel(freeBytes: Long): RecommendedModel = RecommendedModel(
        name = "SmolVLM-256M",
        category = ModelCategory.VISION,
        url = "https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct-GGUF/resolve/main/smolvlm-256m-instruct-q4_k_m.gguf",
        fileName = "smolvlm-256m-instruct-q4_k_m.gguf",
        sizeBytes = min(700_000_000L, freeBytes / 4),
        description = "SmolVLM — reads photos & documents, runs offline"
    )
}
