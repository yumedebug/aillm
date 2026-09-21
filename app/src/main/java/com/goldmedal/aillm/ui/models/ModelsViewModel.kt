package com.goldmedal.aillm.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.modelmanager.ModelDownloader
import com.goldmedal.aillm.ai.modelmanager.ModelInfo
import com.goldmedal.aillm.ai.modelmanager.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Models tab state.
 * Surfaces the full catalog (aa available) + install/load state of each model,
 * and drives the user-initiated download flow through ModelDownloader.
 */
@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val modelDownloader: ModelDownloader
) : ViewModel() {

    private val _chatModels = MutableStateFlow<Map<String, ModelInfo>>(emptyMap())
    val chatModels: StateFlow<Map<String, ModelInfo>> = _chatModels.asStateFlow()

    private val _visionModels = MutableStateFlow<Map<String, ModelInfo>>(emptyMap())
    val visionModels: StateFlow<Map<String, ModelInfo>> = _visionModels.asStateFlow()

    private val _imageGenModels = MutableStateFlow<Map<String, ModelInfo>>(emptyMap())
    val imageGenModels: StateFlow<Map<String, ModelInfo>> = _imageGenModels.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _chatModels.value = modelManager.getAvailableChatModels().associateBy { it.name }
            _visionModels.value = modelManager.getAvailableVisionModels().associateBy { it.name }
            _imageGenModels.value = modelManager.getAvailableImageGenerationModels().associateBy { it.name }
        }
    }

    fun loadModel(name: String) {
        viewModelScope.launch {
            modelManager.loadChatModel(name)
                .onFailure { _error.value = "Failed to load $name: ${it.message}" }
            refresh()
        }
    }

    fun unloadModel(name: String) {
        viewModelScope.launch {
            modelManager.unloadChatModel()
            refresh()
        }
    }

    fun downloadModel(name: String, url: String, fileName: String) {
        viewModelScope.launch {
            val model = com.goldmedal.aillm.ai.modelmanager.ModelDownload(
                name = name,
                url = url,
                fileName = fileName,
                sizeBytes = 0,
                type = com.goldmedal.aillm.ai.modelmanager.ModelType.CHAT
            )
            modelDownloader.downloadModel(
                model = model,
                onProgress = { progress ->
                    _downloadProgress.update { it + (name to progress) }
                }
            )
                .onSuccess { _downloadProgress.update { it - name } }
                .onFailure { _error.value = "Download failed for $name: ${it.message}" }
            refresh()
        }
    }
}
