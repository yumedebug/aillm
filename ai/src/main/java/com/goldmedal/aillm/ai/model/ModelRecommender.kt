package com.goldmedal.aillm.ai.model

import com.goldmedal.aillm.core.device.DeviceProfile

/** How well a model suits the device the user is actually holding. */
enum class ModelFit {
    /** Fits comfortably — the recommended default for that tier. */
    GOOD,

    /** It runs, but it is close to the device's memory limit. */
    TIGHT,

    /** More RAM than this device has. */
    TOO_HEAVY,

    /** The device does not have enough free storage to hold it. */
    NO_STORAGE
}

/**
 * Turns raw device facts into a short, honest shortlist.
 *
 * The app never assumes a model exists and never downloads on its own — it only
 * proposes up to [DEFAULT_COUNT] models per category and lets the user decide.
 */
object ModelRecommender {

    const val DEFAULT_COUNT = 3

    fun fit(spec: ModelSpec, profile: DeviceProfile): ModelFit = when {
        profile.freeStorageBytes > 0L &&
            profile.freeStorageBytes < spec.downloadBytes -> ModelFit.NO_STORAGE
        !spec.fitsRam(profile.totalRamBytes) -> ModelFit.TOO_HEAVY
        !spec.recommendsRam(profile.totalRamBytes) -> ModelFit.TIGHT
        else -> ModelFit.GOOD
    }

    /**
     * The top [count] models drawn from [kinds], best fit first.
     *
     * When nothing fits the device at all we still return the lightest options
     * so the user sees a choice instead of an empty screen.
     */
    fun recommend(
        kinds: List<ModelKind>,
        profile: DeviceProfile,
        count: Int = DEFAULT_COUNT
    ): List<ModelSpec> {
        val pool = kinds.distinct().flatMap { ModelCatalog.byKind(it) }
        if (pool.isEmpty()) return emptyList()

        val usable = pool.filter { fit(it, profile) !in listOf(ModelFit.TOO_HEAVY, ModelFit.NO_STORAGE) }
        if (usable.isEmpty()) return pool.sortedBy { it.downloadBytes }.take(count)

        return usable
            .sortedWith(
                compareByDescending<ModelSpec> { fit(it, profile) == ModelFit.GOOD }
                    .thenByDescending { it.qualityRating }
                    .thenBy { it.downloadBytes }
            )
            .take(count)
    }
}
