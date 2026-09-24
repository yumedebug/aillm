package com.goldmedal.aillm.ui.decision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.ai.decision.DecisionModel
import com.goldmedal.aillm.ai.decision.DecisionResult
import com.goldmedal.aillm.ai.decision.VON_MODEL_ID
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
 * The VON screen's state: one batch of judgments.
 *
 * A is entered as multiple lines — one subject per line — and B as one line.
 * Pressing 判定 walks the A lines one at a time through Von: "A → B か？".
 * Each row moves through Checking → verdict + probability on its own, so the
 * list fills in progressively.
 *
 * Model status comes from [ModelRepository] rather than the engine directly,
 * so this screen and the Models library never disagree about what is installed
 * or resident. Von loads automatically at app start (see
 * `ModelRepositoryImpl.autoLoadVon`); this view model only ever needs to react.
 */
@HiltViewModel
class DecisionViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val decisionModel: DecisionModel
) : ViewModel() {

    /** The Von entry, as described in the catalogue. */
    val spec: ModelSpec? = modelRepository.spec(VON_MODEL_ID)

    val status: StateFlow<ModelStatus> = modelRepository.states
        .map { it[VON_MODEL_ID] ?: ModelStatus.NotInstalled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = modelRepository.status(VON_MODEL_ID)
        )

    private val _subjects = MutableStateFlow("")
    val subjects: StateFlow<String> = _subjects.asStateFlow()

    private val _context = MutableStateFlow("")
    val context: StateFlow<String> = _context.asStateFlow()

    private val _rows = MutableStateFlow<List<Row>>(emptyList())
    val rows: StateFlow<List<Row>> = _rows.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    data class Row(
        val subject: String,
        val state: RowState,
        /** The verdict, once [RowState.DONE]. */
        val result: DecisionResult? = null,
        /** Why the row failed, once [RowState.FAILED]. */
        val failure: Throwable? = null
    )

    /** The per-row lifecycle behind the Checking… / Waiting… display. */
    enum class RowState { WAITING, CHECKING, DONE, FAILED }

    init {
        // If the startup auto-load has not happened yet (or failed because the
        // download finished mid-session), press it along as soon as Von is
        // installed and not already up.
        viewModelScope.launch {
            status.collect { current ->
                if (current is ModelStatus.Installed && !_running.value) {
                    modelRepository.load(VON_MODEL_ID)
                }
            }
        }
    }

    fun onSubjectsChange(value: String) {
        if (!_running.value) _subjects.value = value
    }

    fun onContextChange(value: String) {
        if (!_running.value) _context.value = value
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

    /**
     * Judges every non-blank A line against B, sequentially.
     *
     * Rows are re-created from the current input at press time; editing is
     * locked while the batch runs. Any line that fails is shown as FAILED
     * rather than aborting the rest of the batch.
     */
    fun judge() {
        if (_running.value) return
        val b = _context.value.trim()
        val subjects = _subjects.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (subjects.isEmpty() || b.isEmpty()) return

        _rows.value = subjects.map { Row(it, RowState.WAITING) }
        _error.value = null
        _running.value = true

        viewModelScope.launch {
            try {
                subjects.forEachIndexed { index, subject ->
                    updateRow(index) { it.copy(state = RowState.CHECKING) }
                    decisionModel.decide(subject, b).fold(
                        onSuccess = { result ->
                            updateRow(index) { it.copy(state = RowState.DONE, result = result) }
                        },
                        onFailure = { cause ->
                            updateRow(index) { it.copy(state = RowState.FAILED, failure = cause) }
                        }
                    )
                }
            } finally {
                _running.value = false
            }
        }
    }

    private fun updateRow(index: Int, change: (Row) -> Row) {
        _rows.update { rows ->
            rows.mapIndexed { i, row -> if (i == index) change(row) else row }
        }
    }
}
