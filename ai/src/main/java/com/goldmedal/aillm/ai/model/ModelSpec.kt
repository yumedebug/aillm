package com.goldmedal.aillm.ai.model

import com.goldmedal.aillm.ai.decision.LAYA_MODEL_ID
import com.goldmedal.aillm.ai.imagegeneration.ImageSampler
import com.goldmedal.aillm.ai.prompt.ChatPromptFormat

/** The default image-generation model in the library (quality). */
const val IMAGE_MODEL_ID = "absolute-reality-1.81"

/** The fast image-generation model (LCM, 4-8 steps). */
const val DREAMSHAPER_LCM_MODEL_ID = "dreamshaper-8-lcm"

/** The independent model roles. Each one is loaded separately, on demand. */
enum class ModelKind(val label: String, val blurb: String) {
    CHAT("Chat", "Everyday conversation and questions"),
    CODING("Coding", "Writing, reviewing and explaining code"),
    VISION("Vision", "Understanding images you send"),
    IMAGE_GENERATION("Images", "Generating pictures from text"),
    DECISION("Decision", "Judging a proposition Yes or No")
}

/**
 * A secondary file that must be downloaded together with the main weights.
 * Vision models cannot run without their `mmproj` projector, so it is part of
 * the download rather than a separate "optional extra" the user could skip.
 */
data class ModelFile(
    val fileName: String,
    val url: String,
    val sizeBytes: Long
)

/**
 * Everything known about a downloadable model *before* it is installed.
 *
 * Nothing here is bundled in the APK — a model only exists on the device after
 * the user chooses to download it, and every entry points at a public
 * Hugging Face repository.
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val family: String,
    val kind: ModelKind,
    val parameters: String,
    val quantization: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** Extra files (e.g. vision projectors) downloaded alongside the weights. */
    val auxiliaryFiles: List<ModelFile> = emptyList(),
    val minRamBytes: Long,
    val recommendedRamBytes: Long,
    val contextLength: Int,
    val speedRating: Int,
    val qualityRating: Int,
    val description: String,
    val tags: List<String> = emptyList(),

    // --- image generation only -------------------------------------------------
    /** The sampler the checkpoint was distilled for (see [ImageSampler]). */
    val sampler: ImageSampler = ImageSampler.DEFAULT,
    /** Steps the model works well at out of the box. */
    val defaultSteps: Int = 25,
    /** Classifier-free guidance the model works well at out of the box. */
    val defaultGuidance: Float = 7f,
    /** The step counts offered for this model in the image screen. */
    val stepPresets: List<Int> = listOf(15, 25, 35),

    /** The chat template this family was trained on. */
    val chatFormat: ChatPromptFormat = ChatPromptFormat.PLAIN
) {
    /** What the user actually spends in storage and bandwidth. */
    val downloadBytes: Long get() = sizeBytes + auxiliaryFiles.sumOf { it.sizeBytes }

    val hasAuxiliaryFiles: Boolean get() = auxiliaryFiles.isNotEmpty()

    fun fitsRam(totalRamBytes: Long): Boolean = totalRamBytes >= minRamBytes

    fun recommendsRam(totalRamBytes: Long): Boolean = totalRamBytes >= recommendedRamBytes
}

private const val GB = 1_000_000_000L
private const val MB = 1_000_000L

/**
 * The curated, developer-maintained model library.
 *
 * Every entry was checked against the Hugging Face API: the repository must be
 * public (not gated, no login required) and the file name must exist. Adding a
 * model here is the only thing required for it to show up in the app.
 */
object ModelCatalog {

    val all: List<ModelSpec> = listOf(

        // ---------------------------------------------------------------- Chat
        ModelSpec(
            id = "qwen2.5-0.5b-instruct-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "Qwen2.5 0.5B Instruct",
            family = "Qwen",
            kind = ModelKind.CHAT,
            parameters = "0.5B",
            quantization = "Q4_K_M",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeBytes = 491_400_032L,
            minRamBytes = 1 * GB,
            recommendedRamBytes = 2 * GB,
            contextLength = 32768,
            speedRating = 5,
            qualityRating = 1,
            description = "The smallest chat model here. Almost instant replies, even on budget phones.",
            tags = listOf("fastest", "light")
        ),
        ModelSpec(
            id = "gemma-3-1b-it-q4",
            chatFormat = ChatPromptFormat.GEMMA,
            name = "Gemma 3 1B IT",
            family = "Google",
            kind = ModelKind.CHAT,
            parameters = "1B",
            quantization = "Q4_K_M",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            sizeBytes = 806_058_240L,
            minRamBytes = 2 * GB,
            recommendedRamBytes = 3 * GB,
            contextLength = 32768,
            speedRating = 5,
            qualityRating = 2,
            description = "Google's newest tiny instruct model. Quick and light, with a long context.",
            tags = listOf("fastest", "light")
        ),
        ModelSpec(
            id = "qwen2.5-1.5b-instruct-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "Qwen2.5 1.5B Instruct",
            family = "Qwen",
            kind = ModelKind.CHAT,
            parameters = "1.5B",
            quantization = "Q4_K_M",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1_117_320_736L,
            minRamBytes = 2 * GB,
            recommendedRamBytes = 3 * GB,
            contextLength = 32768,
            speedRating = 5,
            qualityRating = 2,
            description = "A light, dependable chat model. Snappy replies on almost any phone.",
            tags = listOf("fastest", "light")
        ),
        ModelSpec(
            id = "lfm2-1.2b-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "LFM2 1.2B",
            family = "Liquid AI",
            kind = ModelKind.CHAT,
            parameters = "1.2B",
            quantization = "Q4_K_M",
            fileName = "LFM2-1.2B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf",
            sizeBytes = 730_893_248L,
            minRamBytes = 2 * GB,
            recommendedRamBytes = 3 * GB,
            contextLength = 32768,
            speedRating = 5,
            qualityRating = 3,
            description = "Great replies for its size, and the least memory of any model here. " +
                "Built for phones.",
            tags = listOf("recommended", "fastest", "light")
        ),
        ModelSpec(
            id = "qwen3-1.7b-q4",
            chatFormat = ChatPromptFormat.QWEN3,
            name = "Qwen3 1.7B",
            family = "Qwen",
            kind = ModelKind.CHAT,
            parameters = "1.7B",
            quantization = "Q4_K_M",
            fileName = "Qwen3-1.7B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            sizeBytes = 1_107_409_472L,
            minRamBytes = 2 * GB,
            recommendedRamBytes = 3 * GB,
            contextLength = 32768,
            speedRating = 5,
            qualityRating = 4,
            description = "A small model that reasons before it answers. The strongest " +
                "assistant in the light tier.",
            tags = listOf("recommended", "reasoning", "light")
        ),
        ModelSpec(
            id = "gemma-2-2b-it-q4",
            chatFormat = ChatPromptFormat.GEMMA,
            name = "Gemma 2 2B IT",
            family = "Google",
            kind = ModelKind.CHAT,
            parameters = "2B",
            quantization = "Q4_K_M",
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            sizeBytes = 1_710_000_000L,
            minRamBytes = 3 * GB,
            recommendedRamBytes = 4 * GB,
            contextLength = 8192,
            speedRating = 4,
            qualityRating = 3,
            description = "Google's small instruct model. Friendly tone, modest memory use.",
            tags = listOf("balanced")
        ),
        ModelSpec(
            id = "qwen2.5-3b-instruct-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "Qwen2.5 3B Instruct",
            family = "Qwen",
            kind = ModelKind.CHAT,
            parameters = "3B",
            quantization = "Q4_K_M",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            sizeBytes = 1_930_000_000L,
            minRamBytes = 4 * GB,
            recommendedRamBytes = 6 * GB,
            contextLength = 32768,
            speedRating = 4,
            qualityRating = 4,
            description = "The sweet spot for most phones. A strong general assistant.",
            tags = listOf("recommended", "balanced")
        ),
        ModelSpec(
            id = "llama-3.2-3b-instruct-q4",
            chatFormat = ChatPromptFormat.LLAMA3,
            name = "Llama 3.2 3B Instruct",
            family = "Meta",
            kind = ModelKind.CHAT,
            parameters = "3B",
            quantization = "Q4_K_M",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_020_000_000L,
            minRamBytes = 4 * GB,
            recommendedRamBytes = 6 * GB,
            contextLength = 8192,
            speedRating = 4,
            qualityRating = 4,
            description = "Meta's compact assistant. Reliable instruction following.",
            tags = listOf("balanced")
        ),
        ModelSpec(
            id = "phi-3.5-mini-instruct-q4",
            chatFormat = ChatPromptFormat.PHI3,
            name = "Phi-3.5 Mini",
            family = "Microsoft",
            kind = ModelKind.CHAT,
            parameters = "3.8B",
            quantization = "Q4_K_M",
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            sizeBytes = 2_390_000_000L,
            minRamBytes = 4 * GB,
            recommendedRamBytes = 6 * GB,
            contextLength = 4096,
            speedRating = 3,
            qualityRating = 4,
            description = "Punches above its size at reasoning and short answers.",
            tags = listOf("reasoning")
        ),
        ModelSpec(
            id = "qwen2.5-7b-instruct-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "Qwen2.5 7B Instruct",
            family = "Qwen",
            kind = ModelKind.CHAT,
            parameters = "7B",
            quantization = "Q4_K_M",
            fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
            sizeBytes = 4_680_000_000L,
            minRamBytes = 8 * GB,
            recommendedRamBytes = 10 * GB,
            contextLength = 32768,
            speedRating = 2,
            qualityRating = 5,
            description = "Highest quality chat model. Needs a flagship phone.",
            tags = listOf("high quality", "heavy")
        ),

        // -------------------------------------------------------------- Coding
        ModelSpec(
            id = "qwen2.5-coder-1.5b-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "Qwen2.5-Coder 1.5B",
            family = "Qwen",
            kind = ModelKind.CODING,
            parameters = "1.5B",
            quantization = "Q4_K_M",
            fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1_117_320_768L,
            minRamBytes = 2 * GB,
            recommendedRamBytes = 3 * GB,
            contextLength = 32768,
            speedRating = 5,
            qualityRating = 3,
            description = "Fast code helper for snippets, regexes and small scripts.",
            tags = listOf("fastest", "light")
        ),
        ModelSpec(
            id = "qwen2.5-coder-3b-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "Qwen2.5-Coder 3B",
            family = "Qwen",
            kind = ModelKind.CODING,
            parameters = "3B",
            quantization = "Q4_K_M",
            fileName = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            sizeBytes = 1_930_000_000L,
            minRamBytes = 4 * GB,
            recommendedRamBytes = 6 * GB,
            contextLength = 32768,
            speedRating = 4,
            qualityRating = 5,
            description = "The best coding model for a normal phone. Reads whole files.",
            tags = listOf("recommended")
        ),
        ModelSpec(
            id = "qwen2.5-coder-7b-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "Qwen2.5-Coder 7B",
            family = "Qwen",
            kind = ModelKind.CODING,
            parameters = "7B",
            quantization = "Q4_K_M",
            fileName = "qwen2.5-coder-7b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf",
            sizeBytes = 4_680_000_000L,
            minRamBytes = 8 * GB,
            recommendedRamBytes = 10 * GB,
            contextLength = 32768,
            speedRating = 2,
            qualityRating = 5,
            description = "Serious coding quality for flagship devices.",
            tags = listOf("high quality", "heavy")
        ),

        // ------------------------------------------------------------ Decision
        ModelSpec(
            id = LAYA_MODEL_ID,
            name = "Laya Multilingual",
            family = "convaiinnovations",
            kind = ModelKind.DECISION,
            parameters = "322M",
            quantization = "F16",
            fileName = "model.safetensors",
            downloadUrl = "https://huggingface.co/convaiinnovations/laya-multilingual/resolve/main/model.safetensors",
            sizeBytes = 643_835_514L,
            // The repository keeps the encoder config under `encoder/` and the
            // tokenizer under `tokenizer/`; the downloader stores everything
            // flat, so each file is named after its leaf.
            auxiliaryFiles = listOf(
                ModelFile(
                    fileName = "config.json",
                    url = "https://huggingface.co/convaiinnovations/laya-multilingual/resolve/main/encoder/config.json",
                    sizeBytes = 1_938L
                ),
                ModelFile(
                    fileName = "tokenizer.json",
                    url = "https://huggingface.co/convaiinnovations/laya-multilingual/resolve/main/tokenizer/tokenizer.json",
                    sizeBytes = 34_363_188L
                ),
                ModelFile(
                    fileName = "tokenizer_config.json",
                    url = "https://huggingface.co/convaiinnovations/laya-multilingual/resolve/main/tokenizer/tokenizer_config.json",
                    sizeBytes = 502L
                ),
                ModelFile(
                    fileName = "rl_agent_config.json",
                    url = "https://huggingface.co/convaiinnovations/laya-multilingual/resolve/main/rl_agent_config.json",
                    sizeBytes = 472L
                )
            ),
            minRamBytes = 6 * GB,
            recommendedRamBytes = 8 * GB,
            contextLength = 1024,
            speedRating = 4,
            qualityRating = 4,
            description = "Judges a proposition Yes or No in 100+ languages rather " +
                "than chatting. One pass, no prose.",
            tags = listOf("decision", "yes/no", "multilingual")
        ),

        // -------------------------------------------------------------- Vision
        ModelSpec(
            id = "smolvlm-256m-q8",
            chatFormat = ChatPromptFormat.SMOLVLM,
            name = "SmolVLM 256M",
            family = "Hugging Face",
            kind = ModelKind.VISION,
            parameters = "256M",
            quantization = "Q8_0",
            fileName = "SmolVLM-256M-Instruct-Q8_0.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf",
            sizeBytes = 160 * MB,
            auxiliaryFiles = listOf(
                ModelFile(
                    fileName = "mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",
                    url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",
                    sizeBytes = 90 * MB
                )
            ),
            minRamBytes = 3 * GB,
            recommendedRamBytes = 4 * GB,
            contextLength = 8192,
            speedRating = 5,
            qualityRating = 2,
            description = "Tiny vision model. Reads simple photos and documents offline.",
            tags = listOf("light", "fastest")
        ),
        ModelSpec(
            id = "qwen2.5-vl-3b-q4",
            chatFormat = ChatPromptFormat.CHATML,
            name = "Qwen2.5-VL 3B",
            family = "Qwen",
            kind = ModelKind.VISION,
            parameters = "3B",
            quantization = "Q4_K_M",
            fileName = "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_929_901_056L,
            auxiliaryFiles = listOf(
                ModelFile(
                    fileName = "mmproj-Qwen2.5-VL-3B-Instruct-Q8_0.gguf",
                    url = "https://huggingface.co/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/mmproj-Qwen2.5-VL-3B-Instruct-Q8_0.gguf",
                    sizeBytes = 844_757_728L
                )
            ),
            minRamBytes = 6 * GB,
            recommendedRamBytes = 8 * GB,
            contextLength = 32768,
            speedRating = 3,
            qualityRating = 4,
            description = "Describes scenes, reads screenshots, tables and documents in detail.",
            tags = listOf("recommended", "balanced")
        ),
        ModelSpec(
            id = "gemma-3-4b-vision-q4",
            chatFormat = ChatPromptFormat.GEMMA,
            name = "Gemma 3 4B Vision",
            family = "Google",
            kind = ModelKind.VISION,
            parameters = "4B",
            quantization = "Q4_K_M",
            fileName = "gemma-3-4b-it-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
            sizeBytes = 2_489_757_856L,
            auxiliaryFiles = listOf(
                ModelFile(
                    fileName = "mmproj-model-f16.gguf",
                    url = "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/mmproj-model-f16.gguf",
                    sizeBytes = 851_251_104L
                )
            ),
            minRamBytes = 6 * GB,
            recommendedRamBytes = 8 * GB,
            contextLength = 8192,
            speedRating = 3,
            qualityRating = 4,
            description = "Stronger visual understanding at a higher memory cost.",
            tags = listOf("high quality", "heavy")
        ),

        // ---------------------------------------------------- Image generation
        // Both entries are single-file .safetensors that stable-diffusion.cpp
        // loads directly, unlike the diffusers-format mirrors of the same
        // releases (separate unet/vae/text_encoder files). They differ in what
        // they trade: steps for quality, or speed by distilling the sampler.
        ModelSpec(
            id = IMAGE_MODEL_ID,
            name = "Absolute Reality 1.81",
            family = "Lykon",
            kind = ModelKind.IMAGE_GENERATION,
            parameters = "0.9B",
            quantization = "F16",
            fileName = "AbsoluteReality_1.8.1_pruned.safetensors",
            downloadUrl = "https://huggingface.co/Lykon/AbsoluteReality/resolve/main/AbsoluteReality_1.8.1_pruned.safetensors",
            sizeBytes = 2_132_625_432L,
            sampler = ImageSampler.DEFAULT,
            defaultSteps = 25,
            defaultGuidance = 7f,
            stepPresets = listOf(15, 25, 35),
            minRamBytes = 6 * GB,
            recommendedRamBytes = 8 * GB,
            contextLength = 0,
            speedRating = 3,
            qualityRating = 5,
            description = "Lykon's photorealistic SD 1.5 checkpoint. Generates pictures fully on-device.",
            tags = listOf("recommended", "photoreal")
        ),
        ModelSpec(
            id = DREAMSHAPER_LCM_MODEL_ID,
            name = "DreamShaper 8 LCM",
            family = "Lykon",
            kind = ModelKind.IMAGE_GENERATION,
            parameters = "0.9B",
            quantization = "F16",
            fileName = "DreamShaper8_LCM.safetensors",
            downloadUrl = "https://huggingface.co/Lykon/dreamshaper-8-lcm/resolve/main/DreamShaper8_LCM.safetensors",
            sizeBytes = 2_133_804_992L,
            // LCM distillation is what makes the few steps work; sampling it
            // the full 25-step DPM++ way would be both slower and softer.
            sampler = ImageSampler.LCM,
            defaultSteps = 6,
            defaultGuidance = 1.5f,
            stepPresets = listOf(4, 6, 8),
            minRamBytes = 6 * GB,
            recommendedRamBytes = 8 * GB,
            contextLength = 0,
            speedRating = 5,
            qualityRating = 4,
            description = "The fast one: an LCM-distilled SD 1.5 that lands a picture in " +
                "4-8 steps instead of 25.",
            tags = listOf("fastest", "quick")
        )
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }

    fun byKind(kind: ModelKind): List<ModelSpec> = all.filter { it.kind == kind }
}
