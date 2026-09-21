package com.goldmedal.aillm.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.model.ModelFit
import com.goldmedal.aillm.ai.model.ModelKind
import com.goldmedal.aillm.ai.model.ModelRecommender
import com.goldmedal.aillm.ai.model.ModelRepository
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.ai.model.ModelStatus
import com.goldmedal.aillm.core.device.DeviceProfile
import com.goldmedal.aillm.core.device.DeviceProfileProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelRow(
    val spec: ModelSpec,
    val status: ModelStatus,
    val fit: ModelFit,
    val recommended: Boolean
) {
    val isUsable: Boolean get() = fit != ModelFit.TOO_HEAVY && fit != ModelFit.NO_STORAGE
}

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    deviceProfileProvider: DeviceProfileProvider
) : ViewModel() {

    private val profile: DeviceProfile = deviceProfileProvider.profile()

    private val _kind = MutableStateFlow(ModelKind.CHAT)
    val kind: StateFlow<ModelKind> = _kind.asStateFlow()

    val rows: StateFlow<List<ModelRow>> =
        combine(modelRepository.states, _kind) { states, kind ->
            val recommendedIds = ModelRecommender
                .recommend(listOf(kind), profile)
                .map { it.id }
                .toSet()
            modelRepository.byKind(kind).map { spec ->
                ModelRow(
                    spec = spec,
                    status = states[spec.id] ?: ModelStatus.NotInstalled,
                    fit = ModelRecommender.fit(spec, profile),
                    recommended = spec.id in recommendedIds
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectKind(kind: ModelKind) {
        _kind.value = kind
    }

    fun download(id: String) {
        viewModelScope.launch { modelRepository.startDownload(id) }
    }

    fun cancelDownload(id: String) = modelRepository.cancelDownload(id)

    fun delete(id: String) {
        viewModelScope.launch { modelRepository.delete(id) }
    }

    fun load(id: String) {
        viewModelScope.launch { modelRepository.load(id) }
    }

    fun unload() {
        val current = _kind.value
        viewModelScope.launch { modelRepository.unload(current) }
    }
}
