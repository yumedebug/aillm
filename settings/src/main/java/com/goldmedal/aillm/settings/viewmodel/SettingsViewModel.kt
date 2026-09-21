package com.goldmedal.aillm.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.model.ModelRepository
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.ai.model.isInstalled
import com.goldmedal.aillm.core.design.ThemeMode
import com.goldmedal.aillm.core.preferences.AppSettings
import com.goldmedal.aillm.search.OnlineSources
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val modelRepository: ModelRepository,
    private val onlineSources: OnlineSources
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        appSettings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val temperature: StateFlow<Float> =
        appSettings.temperature.stateIn(viewModelScope, SharingStarted.Eagerly, 0.7f)

    val maxTokens: StateFlow<Int> =
        appSettings.maxTokens.stateIn(viewModelScope, SharingStarted.Eagerly, 2048)

    val contextLength: StateFlow<Int> =
        appSettings.contextLength.stateIn(viewModelScope, SharingStarted.Eagerly, 4096)

    val onlineSourcesEnabled: StateFlow<Boolean> =
        appSettings.onlineSourcesEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val reduceMotion: StateFlow<Boolean> =
        appSettings.reduceMotion.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val installedModels: StateFlow<List<ModelSpec>> =
        modelRepository.states.map { states ->
            modelRepository.catalog().filter { states[it.id]?.isInstalled == true }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loadedChatModel: StateFlow<ModelSpec?> =
        modelRepository.states.map { states ->
            modelRepository.activeChatModelId()?.let { id ->
                modelRepository.spec(id)?.takeIf { states[id]?.isInstalled == true }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) = launch { appSettings.setThemeMode(mode) }

    fun setTemperature(value: Float) = launch { appSettings.setTemperature(value) }

    fun setMaxTokens(value: Int) = launch { appSettings.setMaxTokens(value) }

    fun setContextLength(value: Int) = launch { appSettings.setContextLength(value) }

    fun setOnlineSourcesEnabled(value: Boolean) = launch { onlineSources.setEnabled(value) }

    fun setReduceMotion(value: Boolean) = launch { appSettings.setReduceMotion(value) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
