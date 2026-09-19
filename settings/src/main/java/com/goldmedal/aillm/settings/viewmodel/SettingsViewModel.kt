package com.goldmedal.aillm.settings.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.modelmanager.ModelInfo
import com.goldmedal.aillm.ai.modelmanager.ModelManager
import com.goldmedal.aillm.memory.MemoryEngine
import com.goldmedal.aillm.search.WebSearchManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val modelManager: ModelManager,
    private val memoryEngine: MemoryEngine,
    private val webSearchManager: WebSearchManager
) : AndroidViewModel(application) {

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _isWebSearchEnabled = MutableStateFlow(false)
    val isWebSearchEnabled: StateFlow<Boolean> = _isWebSearchEnabled.asStateFlow()

    private val _braveApiKey = MutableStateFlow("")
    val braveApiKey: StateFlow<String> = _braveApiKey.asStateFlow()

    private val _tavilyApiKey = MutableStateFlow("")
    val tavilyApiKey: StateFlow<String> = _tavilyApiKey.asStateFlow()

    init {
        loadModels()
        loadSearchSettings()
    }

    private fun loadModels() {
        viewModelScope.launch {
            val installedModels = modelManager.getInstalledModels()
            _models.value = installedModels
        }
    }

    private fun loadSearchSettings() {
        viewModelScope.launch {
            _isWebSearchEnabled.value = webSearchManager.isEnabled()
            _braveApiKey.value = webSearchManager.getBraveApiKey()
            _tavilyApiKey.value = webSearchManager.getTavilyApiKey()
        }
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            webSearchManager.setEnabled(enabled)
            _isWebSearchEnabled.value = enabled
        }
    }

    fun setBraveApiKey(key: String) {
        viewModelScope.launch {
            webSearchManager.setBraveApiKey(key)
            _braveApiKey.value = key
        }
    }

    fun setTavilyApiKey(key: String) {
        viewModelScope.launch {
            webSearchManager.setTavilyApiKey(key)
            _tavilyApiKey.value = key
        }
    }

    fun loadModel(modelName: String) {
        viewModelScope.launch {
            modelManager.loadChatModel(modelName)
            loadModels()
        }
    }

    fun unloadModel(modelName: String) {
        viewModelScope.launch {
            modelManager.unloadChatModel()
            loadModels()
        }
    }

    fun deleteAllMemories() {
        viewModelScope.launch {
            memoryEngine.deleteAllMemories()
        }
    }
}
