package com.goldmedal.aillm.memory.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.core.database.UserMemoryEntity
import com.goldmedal.aillm.memory.MemoryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val showArchived = MutableStateFlow(false)

    /**
     * Archived memories are ones consolidation moved aside: near-duplicates of
     * something still known, or statements old enough that the newer ones
     * describe the user better. They stay visible here so nothing the assistant
     * believed is quietly lost.
     */
    private val source = combine(
        memoryEngine.observeMemories(),
        memoryEngine.observeArchivedMemories(),
        showArchived
    ) { active, archived, archivedOnly -> if (archivedOnly) archived else active }

    val memories: StateFlow<List<UserMemoryEntity>> =
        combine(source, query, category) { all, q, cat ->
            all.asSequence()
                .filter { cat == null || it.category.equals(cat, ignoreCase = true) }
                .filter { item ->
                    q.isBlank() ||
                        item.value.contains(q, ignoreCase = true) ||
                        item.key.contains(q, ignoreCase = true) ||
                        item.category.contains(q, ignoreCase = true)
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> =
        source.map { list -> list.map { it.category }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedCategory: StateFlow<String?> = category
    val isShowingArchived: StateFlow<Boolean> = showArchived

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(value: String?) {
        category.value = value
    }

    fun setShowArchived(value: Boolean) {
        showArchived.value = value
        category.value = null
    }

    fun addMemory(category: String, key: String, value: String) {
        viewModelScope.launch {
            memoryEngine.storeMemory(category = category, key = key, value = value, importance = 2)
        }
    }

    fun updateMemory(memory: UserMemoryEntity) {
        viewModelScope.launch {
            memoryEngine.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun restoreMemory(memoryId: Long) {
        viewModelScope.launch {
            memoryEngine.restoreMemory(memoryId)
            _notice.value = "Memory restored."
        }
    }

    /** Merges near-duplicates together and trims over-full categories. */
    fun tidyUp() {
        viewModelScope.launch {
            val archived = memoryEngine.cleanUpMemories().getOrDefault(0)
            _notice.value = if (archived == 0) {
                "Nothing to tidy up."
            } else {
                "$archived older or duplicate memories were moved to Archived."
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch { memoryEngine.deleteMemory(memoryId) }
    }

    fun deleteAll() {
        viewModelScope.launch { memoryEngine.deleteAllMemories() }
    }
}
