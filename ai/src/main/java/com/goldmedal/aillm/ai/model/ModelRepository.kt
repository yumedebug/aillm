package com.goldmedal.aillm.ai.model

import kotlinx.coroutines.flow.StateFlow

/**
 * The single source of truth for model availability, install state and load
 * state. The UI never assumes a model exists; it only reacts to [states].
 */
interface ModelRepository {
    /** Live status for every model the app knows about, keyed by [ModelSpec.id]. */
    val states: StateFlow<Map<String, ModelStatus>>

    fun catalog(): List<ModelSpec>
    fun spec(id: String): ModelSpec?
    fun byKind(kind: ModelKind): List<ModelSpec>

    suspend fun startDownload(id: String): Result<Unit>
    fun cancelDownload(id: String)
    suspend fun delete(id: String): Result<Unit>

    suspend fun load(id: String): Result<Unit>
    suspend fun unload(kind: ModelKind)

    /** The id of the model currently loaded for [kind], if any. */
    fun loadedId(kind: ModelKind): String?

    /**
     * The chat-capable model that is resident right now, whether it came from
     * the Chat or the Coding library. Null when nothing is loaded.
     */
    fun activeChatModelId(): String?

    fun status(id: String): ModelStatus
}
