package com.goldmedal.aillm.ui.image

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.imagegeneration.ImageGenerationModel
import com.goldmedal.aillm.ai.model.IMAGE_MODEL_ID
import com.goldmedal.aillm.ai.model.ModelRepository
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.ai.model.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The image screen's state: one prompt in, one picture out.
 *
 * The model is loaded lazily on the first generation rather than at screen
 * entry — it is ~2 GB resident, and a user who opens the screen just to look
 * should not pay for that.
 *
 * Install status comes from [ModelRepository] rather than the engine directly,
 * so this screen and the Models library never disagree.
 */
@HiltViewModel
class ImageViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val imageModel: ImageGenerationModel
) : ViewModel() {

    val spec: ModelSpec? = modelRepository.spec(IMAGE_MODEL_ID)

    val status: StateFlow<ModelStatus> = modelRepository.states
        .map { it[IMAGE_MODEL_ID] ?: ModelStatus.NotInstalled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = modelRepository.status(IMAGE_MODEL_ID)
        )

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _negative = MutableStateFlow("")
    val negative: StateFlow<String> = _negative.asStateFlow()

    private val _size = MutableStateFlow(512)
    val size: StateFlow<Int> = _size.asStateFlow()

    private val _steps = MutableStateFlow(25)
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** Sampling progress, 0..1. */
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // The native progress callback fires on the engine's worker thread;
        // writing a StateFlow from there is safe.
        imageModel.setProgressListener { step, total ->
            _progress.value = if (total > 0) (step.toFloat() / total.toFloat()) else 0f
        }
    }

    fun onPromptChange(value: String) {
        if (!_generating.value) _prompt.value = value
    }

    fun onNegativeChange(value: String) {
        if (!_generating.value) _negative.value = value
    }

    fun selectSize(value: Int) {
        if (!_generating.value) _size.value = value
    }

    fun selectSteps(value: Int) {
        if (!_generating.value) _steps.value = value
    }

    fun clearError() {
        _error.value = null
    }

    fun download() {
        val spec = spec ?: return
        viewModelScope.launch { modelRepository.startDownload(spec.id) }
    }

    fun cancelDownload() {
        modelRepository.cancelDownload(IMAGE_MODEL_ID)
    }

    fun generate() {
        if (_generating.value) return
        val spec = spec ?: return
        if (_prompt.value.isBlank()) return

        _generating.value = true
        _progress.value = 0f
        _error.value = null

        viewModelScope.launch {
            try {
                if (!imageModel.isLoaded) {
                    modelRepository.load(spec.id).onFailure { cause ->
                        _error.value = cause.message ?: "The image model could not be loaded."
                        return@launch
                    }
                }
                imageModel.generateImage(
                    prompt = _prompt.value.trim(),
                    negativePrompt = _negative.value.trim(),
                    width = _size.value,
                    height = _size.value,
                    steps = _steps.value,
                    guidanceScale = GUIDANCE
                ).fold(
                    onSuccess = { _bitmap.value = it },
                    onFailure = { cause ->
                        _error.value = cause.message ?: "The image could not be generated."
                    }
                )
            } finally {
                _generating.value = false
            }
        }
    }

    companion object {
        const val GUIDANCE = 7.0f
    }
}
