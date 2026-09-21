package com.goldmedal.aillm.ai.model

/**
 * The full model lifecycle. "Installed" and "Ready" are deliberately distinct:
 *
 *  - Installed: the (verified) file exists on disk
 *  - Loading:   the engine is currently loading it
 *  - Ready:     inference is actually possible right now
 *
 * Nothing may treat a model as usable unless it is [Ready].
 */
sealed interface ModelStatus {

    /** No file on device. This is the normal state on a fresh install. */
    data object NotInstalled : ModelStatus

    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : ModelStatus

    data object Verifying : ModelStatus

    /** Verified file on disk, not loaded into the engine. */
    data object Installed : ModelStatus

    data object Loading : ModelStatus

    /** Loaded and ready to run inference. */
    data object Ready : ModelStatus

    data class Error(val message: String) : ModelStatus
}

val ModelStatus.isInstalled: Boolean
    get() = this is ModelStatus.Installed || this is ModelStatus.Ready

val ModelStatus.isBusy: Boolean
    get() = this is ModelStatus.Downloading ||
        this is ModelStatus.Verifying ||
        this is ModelStatus.Loading
