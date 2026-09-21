package com.goldmedal.aillm.ui.setup

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
import com.goldmedal.aillm.core.preferences.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupModelRow(
    val spec: ModelSpec,
    val status: ModelStatus,
    val fit: ModelFit,
    val bestFit: Boolean
) {
    val isUsable: Boolean get() = fit != ModelFit.TOO_HEAVY && fit != ModelFit.NO_STORAGE
}

data class SetupSection(
    val title: String,
    val subtitle: String,
    val showsKind: Boolean,
    val rows: List<SetupModelRow>
)

private data class SectionRequest(
    val title: String,
    val subtitle: String,
    val kinds: List<ModelKind>,
    val showsKind: Boolean
)

/**
 * First-run wizard.
 *
 * It reads the real device, then proposes up to three models for each of the
 * three jobs the app does — chat, coding and images. Nothing is downloaded
 * until the user taps Install on a specific card, and nothing is assumed to
 * exist beforehand.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    deviceProfileProvider: DeviceProfileProvider,
    private val modelRepository: ModelRepository,
    private val appSettings: AppSettings
) : ViewModel() {

    val profile: DeviceProfile = deviceProfileProvider.profile()

    private val requests = listOf(
        SectionRequest(
            title = "Chat",
            subtitle = "Talk things through, ask questions, draft text",
            kinds = listOf(ModelKind.CHAT),
            showsKind = false
        ),
        SectionRequest(
            title = "Coding",
            subtitle = "Write, read and explain code",
            kinds = listOf(ModelKind.CODING),
            showsKind = false
        ),
        SectionRequest(
            title = "Images",
            subtitle = "Understand photos you send, or generate new ones",
            kinds = listOf(ModelKind.VISION, ModelKind.IMAGE_GENERATION),
            showsKind = true
        )
    )

    private val recommended: List<Pair<SectionRequest, List<ModelSpec>>> = requests.map { request ->
        request to ModelRecommender.recommend(request.kinds, profile)
    }

    /** Best fit per section, used to label exactly one card as the default pick. */
    private val bestFitIds: Set<String> = recommended.mapNotNull { (_, specs) ->
        specs.firstOrNull { ModelRecommender.fit(it, profile) == ModelFit.GOOD }?.id
            ?: specs.firstOrNull()?.id
    }.toSet()

    val sections: StateFlow<List<SetupSection>> =
        modelRepository.states
            .map { states ->
                recommended.map { (request, specs) ->
                    SetupSection(
                        title = request.title,
                        subtitle = request.subtitle,
                        showsKind = request.showsKind,
                        rows = specs.map { spec ->
                            SetupModelRow(
                                spec = spec,
                                status = states[spec.id] ?: ModelStatus.NotInstalled,
                                fit = ModelRecommender.fit(spec, profile),
                                bestFit = spec.id in bestFitIds
                            )
                        }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Anything that can answer chat. Vision models qualify: a multimodal model
     * is a text model with sight added, so it is a perfectly good assistant.
     */
    private val chatCapable: List<ModelSpec> =
        modelRepository.byKind(ModelKind.CHAT) +
            modelRepository.byKind(ModelKind.CODING) +
            modelRepository.byKind(ModelKind.VISION)

    init {
        // Once the user installs a chat-capable model, bring it up so the app is
        // immediately usable. Never loads anything the user did not ask for.
        viewModelScope.launch {
            modelRepository.states.collect { states ->
                if (modelRepository.activeChatModelId() != null) return@collect
                val ready = chatCapable
                    .firstOrNull { states[it.id] is ModelStatus.Installed }
                    ?: return@collect
                modelRepository.load(ready.id)
            }
        }
    }

    fun install(id: String) {
        viewModelScope.launch { modelRepository.startDownload(id) }
    }

    fun cancelDownload(id: String) {
        modelRepository.cancelDownload(id)
    }

    fun delete(id: String) {
        viewModelScope.launch { modelRepository.delete(id) }
    }

    fun finish() {
        viewModelScope.launch { appSettings.setOnboarded(true) }
    }
}
