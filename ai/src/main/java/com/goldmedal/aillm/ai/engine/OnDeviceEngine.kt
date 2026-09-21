package com.goldmedal.aillm.ai.engine

import com.goldmedal.aillm.ai.model.ModelSpec

/**
 * Common lifecycle shared by every on-device engine (chat, vision, image
 * generation). Exists so the model repository can talk about loading/unloading
 * an engine without knowing which role it plays.
 *
 * [load] is told exactly which file to use. Engines must never guess: the app
 * only ever runs a model the user explicitly installed.
 */
interface OnDeviceEngine {
    val name: String
    val isLoaded: Boolean

    suspend fun load(
        spec: ModelSpec,
        modelPath: String,
        projectorPath: String? = null
    ): Result<Unit>

    suspend fun unload(): Result<Unit>
}
