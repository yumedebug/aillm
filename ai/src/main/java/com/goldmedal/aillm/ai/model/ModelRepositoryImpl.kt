package com.goldmedal.aillm.ai.model

import android.content.Context
import com.goldmedal.aillm.ai.chat.ChatModel
import com.goldmedal.aillm.ai.decision.DecisionModel
import com.goldmedal.aillm.ai.decision.VON_MODEL_ID
import com.goldmedal.aillm.ai.engine.OnDeviceEngine
import com.goldmedal.aillm.ai.imagegeneration.ImageGenerationModel
import com.goldmedal.aillm.ai.vision.VisionModel
import com.goldmedal.aillm.core.database.InstalledModelDao
import com.goldmedal.aillm.core.database.InstalledModelEntity
import com.goldmedal.aillm.core.preferences.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installedModelDao: InstalledModelDao,
    private val appSettings: AppSettings,
    private val downloader: ModelDownloader,
    private val chatModel: ChatModel,
    private val visionModel: VisionModel,
    private val imageGenerationModel: ImageGenerationModel,
    private val decisionModel: DecisionModel
) : ModelRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _states = MutableStateFlow<Map<String, ModelStatus>>(emptyMap())
    override val states: StateFlow<Map<String, ModelStatus>> = _states.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val loadedIds = mutableMapOf<ModelKind, String>()

    init {
        scope.launch {
            restore()
            // Von is the app's front door: once the previous session's model is
            // accounted for, Von's ONNX runtime is brought up for the launch.
            // It runs after restore so the two loads cannot race; if a chat
            // model was restored, Von replaces it as the resident engine.
            autoLoadVon()
        }
    }

    /**
     * Loads Von on startup when it is installed.
     *
     * Von never shares the device with a resident chat model — [load] unloads
     * whichever side is already up — so this leaves Von as the only resident
     * engine. When Von is not installed the app keeps its normal state: the
     * Decision screen offers the download and nothing else is disturbed.
     */
    private suspend fun autoLoadVon() {
        if (ModelCatalog.byId(VON_MODEL_ID) == null) return
        if (status(VON_MODEL_ID).isInstalled) {
            runCatching { load(VON_MODEL_ID) }
        }
    }

    // ---- catalog ----

    override fun catalog(): List<ModelSpec> = ModelCatalog.all

    override fun spec(id: String): ModelSpec? = ModelCatalog.byId(id)

    override fun byKind(kind: ModelKind): List<ModelSpec> = ModelCatalog.byKind(kind)

    override fun status(id: String): ModelStatus = _states.value[id] ?: ModelStatus.NotInstalled

    override fun loadedId(kind: ModelKind): String? = loadedIds[kind]

    override fun activeChatModelId(): String? =
        loadedIds[ModelKind.CHAT]
            ?: loadedIds[ModelKind.CODING]
            // A resident vision model is a text model too, so it answers chat.
            ?: loadedIds[ModelKind.VISION]

    // ---- restore ----

    private suspend fun restore() {
        val installed = runCatching { installedModelDao.getAll() }.getOrDefault(emptyList())
        val map = mutableMapOf<String, ModelStatus>()
        installed.forEach { entity ->
            val spec = ModelCatalog.byId(entity.specId)
            val files = spec?.let { downloader.filesFor(it) }
            val complete = files != null && files.all { it.exists() && it.length() > 0L }
            if (spec == null || !complete) {
                // A half-present model is not installed. Never pretend otherwise.
                runCatching { installedModelDao.deleteById(entity.specId) }
            } else {
                map[entity.specId] = ModelStatus.Installed
            }
        }
        _states.update { it + map }
        // Von is the front door: when it is installed it always takes the
        // device on startup (autoLoadVon), so the last session's chat model is
        // not brought back just to be evicted again a moment later.
        if (!status(VON_MODEL_ID).isInstalled) {
            restoreLastModel()
        }
    }

    /**
     * Brings back the model that was loaded when the app was last closed.
     *
     * Without this the user would have to pick and load a model on every
     * launch, which is exactly the friction this app is supposed to remove. A
     * model that is no longer installed simply forgets itself.
     */
    private suspend fun restoreLastModel() {
        val lastId = runCatching { appSettings.lastUsedModelId.first() }.getOrNull() ?: return
        val spec = ModelCatalog.byId(lastId) ?: return
        if (spec.kind == ModelKind.IMAGE_GENERATION) return
        // Von is brought up by [autoLoadVon] instead; it is never "the last
        // chat model" and must not be restored as one.
        if (spec.kind == ModelKind.DECISION) return
        if (!status(lastId).isInstalled) {
            runCatching { appSettings.clearLastUsedModelId() }
            return
        }
        // Loading is best effort: a device that no longer has the memory for it
        // keeps working, just without a resident model.
        runCatching { load(lastId) }
    }

    // ---- download ----

    override suspend fun startDownload(id: String): Result<Unit> {
        val spec = ModelCatalog.byId(id)
            ?: return Result.failure(IllegalArgumentException("Unknown model: $id"))
        if (status(id) is ModelStatus.Downloading) return Result.success(Unit)

        val job = scope.launch {
            set(id, ModelStatus.Downloading(0f, 0L, spec.downloadBytes))
            val result = downloader.download(spec) { progress, downloaded, total ->
                set(id, ModelStatus.Downloading(progress, downloaded, total))
            }
            result
                .onSuccess { files ->
                    set(id, ModelStatus.Verifying)
                    if (verify(spec, files)) {
                        installedModelDao.upsert(
                            InstalledModelEntity(
                                specId = spec.id,
                                filePath = files.first().absolutePath,
                                fileSizeBytes = files.sumOf { it.length() }
                            )
                        )
                        set(id, ModelStatus.Installed)
                    } else {
                        files.forEach { runCatching { it.delete() } }
                        set(id, ModelStatus.Error("The downloaded file failed verification. Try again."))
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        set(id, ModelStatus.NotInstalled)
                    } else {
                        set(id, ModelStatus.Error(error.message ?: "Download failed"))
                    }
                }
            downloadJobs.remove(id)
        }
        downloadJobs[id] = job
        return Result.success(Unit)
    }

    override fun cancelDownload(id: String) {
        downloadJobs.remove(id)?.cancel()
        set(id, ModelStatus.NotInstalled)
    }

    override suspend fun delete(id: String): Result<Unit> {
        val spec = ModelCatalog.byId(id) ?: return Result.failure(IllegalArgumentException("Unknown model: $id"))
        if (loadedIds.values.contains(id)) {
            unload(spec.kind)
        }
        runCatching {
            downloader.filesFor(spec).forEach { it.delete() }
            runCatching { downloader.tempFileFor(spec.fileName).delete() }
            spec.auxiliaryFiles.forEach { runCatching { downloader.tempFileFor(it.fileName).delete() } }
            installedModelDao.deleteById(id)
        }
        // A deleted model must not be remembered, or the next launch would try
        // to restore something that is gone.
        runCatching {
            if (appSettings.lastUsedModelId.first() == id) appSettings.clearLastUsedModelId()
        }
        set(id, ModelStatus.NotInstalled)
        return Result.success(Unit)
    }

    // ---- load / unload ----

    override suspend fun load(id: String): Result<Unit> {
        val spec = ModelCatalog.byId(id) ?: return Result.failure(IllegalArgumentException("Unknown model: $id"))
        if (!status(id).isInstalled) {
            return Result.failure(IllegalStateException("${spec.name} is not installed"))
        }
        // A load already under way must not be started again — startup (autoLoadVon)
        // and a screen reacting to the same install could otherwise race.
        if (status(id) is ModelStatus.Loading) return Result.success(Unit)

        // Von never shares memory with a chat model: loading one side unloads
        // the other first. The two runtimes may not both be resident.
        if (spec.kind == ModelKind.DECISION) {
            if (loadedIds[ModelKind.CHAT] != null ||
                loadedIds[ModelKind.CODING] != null ||
                loadedIds[ModelKind.VISION] != null
            ) {
                runCatching { unload(ModelKind.CHAT) }
            }
        } else if (loadedIds[ModelKind.DECISION] != null) {
            runCatching { unload(ModelKind.DECISION) }
        }

        // Chat and coding models share one runtime, so only one can be resident.
        for (sibling in siblingsOf(spec.kind)) {
            val siblingId = loadedIds[sibling] ?: continue
            if (siblingId == id) continue
            runCatching { engineFor(sibling).unload() }
            loadedIds.remove(sibling)
            set(siblingId, if (status(siblingId).isInstalled) ModelStatus.Installed else ModelStatus.NotInstalled)
        }

        val previous = loadedIds[spec.kind]
        if (previous != null && previous != id) {
            runCatching { engineFor(spec.kind).unload() }
            set(previous, ModelStatus.Installed)
            loadedIds.remove(spec.kind)
        }

        set(id, ModelStatus.Loading)
        val weights = downloader.fileFor(spec)
        val projector = spec.auxiliaryFiles
            .firstOrNull { it.fileName.contains("mmproj", ignoreCase = true) }
            ?.let { downloader.auxFileFor(spec, it) }
        val result = engineFor(spec.kind).load(
            spec = spec,
            modelPath = weights.absolutePath,
            projectorPath = projector?.absolutePath
        )
        return result
            .onSuccess {
                loadedIds[spec.kind] = id
                runCatching { installedModelDao.touch(id) }
                // Remembered so the next launch can bring the same model back.
                // Von is loaded automatically on every launch, so it is never
                // remembered as the "last used" chat model.
                if (spec.kind != ModelKind.DECISION) {
                    runCatching { appSettings.setLastUsedModelId(id) }
                }
                set(id, ModelStatus.Ready)
            }
            .onFailure { error ->
                set(id, ModelStatus.Error(error.message ?: "Failed to load model"))
            }
    }

    override suspend fun unload(kind: ModelKind) {
        for (target in listOf(kind) + siblingsOf(kind)) {
            runCatching { engineFor(target).unload() }
            val id = loadedIds.remove(target) ?: continue
            set(id, if (status(id).isInstalled) ModelStatus.Installed else ModelStatus.NotInstalled)
        }
    }

    // ---- helpers ----

    private fun engineFor(kind: ModelKind): OnDeviceEngine = when (kind) {
        ModelKind.CHAT, ModelKind.CODING -> chatModel
        ModelKind.VISION -> visionModel
        ModelKind.IMAGE_GENERATION -> imageGenerationModel
        ModelKind.DECISION -> decisionModel
    }

    /**
     * Kinds that share the same runtime as [kind] (excluding itself). Chat,
     * coding and vision all run on the single text model, so only one of them
     * can be resident at a time.
     */
    private fun siblingsOf(kind: ModelKind): List<ModelKind> = when (kind) {
        ModelKind.CHAT -> listOf(ModelKind.CODING, ModelKind.VISION)
        ModelKind.CODING -> listOf(ModelKind.CHAT, ModelKind.VISION)
        ModelKind.VISION -> listOf(ModelKind.CHAT, ModelKind.CODING)
        ModelKind.IMAGE_GENERATION -> emptyList()
        // The Decision model runs on the ONNX runtime, independently of the
        // text model, so loading it never evicts the resident chat model.
        ModelKind.DECISION -> emptyList()
    }

    private fun set(id: String, status: ModelStatus) {
        _states.update { it + (id to status) }
    }

    /**
     * GGUF files carry a magic header, and a vision model is unusable without
     * its projector. A broken download must never be counted as installed.
     */
    private fun verify(spec: ModelSpec, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        if (files.any { !it.exists() || it.length() == 0L }) return false
        return files.all { file ->
            if (file.name.endsWith(".gguf", ignoreCase = true)) hasGgufMagic(file) else true
        }
    }

    private fun hasGgufMagic(file: File): Boolean = runCatching {
        file.inputStream().use { stream ->
            val magic = ByteArray(4)
            if (stream.read(magic) != 4) return false
            String(magic, Charsets.US_ASCII) == "GGUF"
        }
    }.getOrDefault(false)
}
