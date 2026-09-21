package com.goldmedal.aillm.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Rough capability bucket used to surface the right model recommendations. */
enum class DeviceTier(val label: String, val description: String) {
    LIGHT("Light", "Small, fast models for casual use"),
    BALANCED("Balanced", "Everyday assistant at good quality"),
    PERFORMANCE("Performance", "Larger, higher-quality models")
}

/** A snapshot of what this exact phone can realistically run. */
data class DeviceProfile(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val cpuCores: Int,
    val abi: String,
    val androidSdk: Int,
    val freeStorageBytes: Long,
    val acceleration: String?,
    val tier: DeviceTier
)

/**
 * Reads the real hardware. Used by the setup wizard and the model screen to
 * recommend models that actually fit *this* device rather than a generic list.
 */
@Singleton
class DeviceProfileProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun profile(): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val freeStorage = runCatching {
            val root = context.filesDir ?: File(context.applicationInfo.dataDir)
            StatFs(root.absolutePath).availableBytes
        }.getOrDefault(0L)

        val cores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI
        val gl = runCatching { am.deviceConfigurationInfo.glEsVersion }.getOrNull()

        return DeviceProfile(
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            cpuCores = cores,
            abi = abi,
            androidSdk = Build.VERSION.SDK_INT,
            freeStorageBytes = freeStorage,
            acceleration = gl?.takeIf { it.isNotBlank() }?.let { "OpenGL ES $it" },
            tier = tierFor(memInfo.totalMem, cores)
        )
    }

    private fun tierFor(totalRam: Long, cores: Int): DeviceTier = when {
        totalRam >= 5_500_000_000L && cores >= 8 -> DeviceTier.PERFORMANCE
        totalRam >= 2_800_000_000L -> DeviceTier.BALANCED
        else -> DeviceTier.LIGHT
    }
}
