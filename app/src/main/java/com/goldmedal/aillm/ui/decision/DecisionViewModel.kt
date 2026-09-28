package com.goldmedal.aillm.ui.decision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.decision.DecisionFailure
import com.goldmedal.aillm.ai.decision.DecisionFailureKind
import com.goldmedal.aillm.ai.decision.DecisionModel
import com.goldmedal.aillm.ai.decision.DecisionOutcome
import com.goldmedal.aillm.ai.decision.LAYA_MODEL_ID
import com.goldmedal.aillm.ai.decision.LayaQuestionType
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The LAYA screen's state: a batch of decisions.
 *
 * State is entered one line per item, so every line becomes its *own* Laya
 * decision — the lines are never joined into one state. Question is the single
 * question asked of all of them, and Question Type selects the typed primitive
 * (noul for Yes/No).
 *
 * Model status comes from [ModelRepository] rather than the engine directly, so
 * this screen and the Models library never disagree about what is installed.
 */
@HiltViewModel
class DecisionViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val decisionModel: DecisionModel
) : ViewModel() {

    val spec: ModelSpec? = modelRepository.spec(LAYA_MODEL_ID)

    val status: StateFlow<ModelStatus> = modelRepository.states
        .map { it[LAYA_MODEL_ID] ?: ModelStatus.NotInstalled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = modelRepository.status(LAYA_MODEL_ID)
        )

    private val _states = MutableStateFlow("")
    val states: StateFlow<String> = _states.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _questionType = MutableStateFlow(LayaQuestionType.NOUL)
    val questionType: StateFlow<LayaQuestionType> = _questionType.asStateFlow()

    private val _rows = MutableStateFlow<List<Row>>(emptyList())
    val rows: StateFlow<List<Row>> = _rows.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _debugEnabled = MutableStateFlow(false)
    val debugEnabled: StateFlow<Boolean> = _debugEnabled.asStateFlow()

    /** One state line and what Laya made of it. */
    data class Row(
        val subject: String,
        val state: RowState,
        val outcome: DecisionOutcome? = null
    )

    enum class RowState { WAITING, CHECKING, DONE, FAILED }

    init {
        // If the startup auto-load has not happened yet (or failed because the
        // download finished mid-session), press it along as soon as Laya is
        // installed and not already up.
        viewModelScope.launch {
            status.collect { current ->
                if (current is ModelStatus.Installed && !_running.value) {
                    modelRepository.load(LAYA_MODEL_ID)
                }
            }
        }
    }

    fun onStatesChange(value: String) {
        if (!_running.value) _states.value = value
    }

    fun onQuestionChange(value: String) {
        if (!_running.value) _question.value = value
    }

    fun onQuestionTypeChange(value: LayaQuestionType) {
        if (!_running.value) _questionType.value = value
    }

    fun toggleDebug() {
        _debugEnabled.update { !it }
    }

    fun clearError() {
        _error.value = null
    }

    fun download() {
        val spec = spec ?: return
        viewModelScope.launch { modelRepository.startDownload(spec.id) }
    }

    fun cancelDownload() {
        val spec = spec ?: return
        modelRepository.cancelDownload(spec.id)
    }

    /** Judges every non-blank State line against the Question in one batch. */
    fun judge() {
        if (_running.value) return
        val type = _questionType.value
        val question = _question.value.trim()
        val subjects = _states.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (subjects.isEmpty() || question.isEmpty()) return

        _rows.value = subjects.map { Row(it, RowState.WAITING) }
        _error.value = null
        _running.value = true
        runBatch(subjects, question, type)
    }

    /** Re-runs a single failed row, leaving the rest of the batch untouched. */
    fun retry(index: Int) {
        if (_running.value) return
        val row = _rows.value.getOrNull(index) ?: return
        val question = _question.value.trim()
        if (question.isEmpty()) return
        _running.value = true
        runBatch(listOf(row.subject), question, _questionType.value, indices = listOf(index))
    }

    private fun runBatch(
        subjects: List<String>,
        question: String,
        type: LayaQuestionType,
        indices: List<Int> = subjects.indices.toList()
    ) {
        viewModelScope.launch {
            try {
                markChecking(indices)
                decisionModel.decide(subjects, question, type).fold(
                    onSuccess = { outcomes ->
                        outcomes.forEachIndexed { offset, outcome ->
                            val index = indices.getOrElse(offset) { offset }
                            updateRow(index) {
                                it.copy(
                                    state = if (outcome is DecisionOutcome.Answered) RowState.DONE else RowState.FAILED,
                                    outcome = outcome
                                )
                            }
                        }
                    },
                    onFailure = { cause ->
                        // An engine-level failure: every row shows the explicit
                        // error state rather than a substituted verdict.
                        _error.value = cause.message
                        indices.forEach { index ->
                            updateRow(index) { row ->
                                row.copy(
                                    state = RowState.FAILED,
                                    outcome = DecisionOutcome.Failed(
                                        row.subject,
                                        DecisionFailure(DecisionFailureKind.MODEL_NOT_LOADED, cause.message)
                                    )
                                )
                            }
                        }
                    }
                )
            } finally {
                _running.value = false
            }
        }
    }

    private fun markChecking(indices: List<Int>) {
        indices.forEach { index -> updateRow(index) { it.copy(state = RowState.CHECKING) } }
    }

    private fun updateRow(index: Int, change: (Row) -> Row) {
        _rows.update { rows -> rows.mapIndexed { i, row -> if (i == index) change(row) else row } }
    }
}
