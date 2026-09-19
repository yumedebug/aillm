package com.goldmedal.aillm.memory.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.core.database.UserMemoryEntity
import com.goldmedal.aillm.memory.MemoryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    private val _memories = MutableStateFlow<List<UserMemoryEntity>>(emptyList())
    val memories: StateFlow<List<UserMemoryEntity>> = _memories.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadMemories()
    }

    private fun loadMemories() {
        viewModelScope.launch {
            try {
                val allMemories = mutableListOf<UserMemoryEntity>()
                val categories = listOf("PROFILE", "PREFERENCE", "PROJECT", "EVENT", "CONVERSATION", "IMAGE", "FILE", "TECHNICAL")
                for (category in categories) {
                    allMemories.addAll(memoryEngine.getMemoriesByCategory(category))
                }
                _memories.value = allMemories
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun searchMemories(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                loadMemories()
                return@launch
            }
            try {
                _memories.value = memoryEngine.searchMemories(query)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun addMemory(category: String, key: String, value: String) {
        viewModelScope.launch {
            try {
                memoryEngine.storeMemory(category, key, value)
                loadMemories()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateMemory(memory: UserMemoryEntity) {
        viewModelScope.launch {
            try {
                memoryEngine.updateMemory(memory)
                loadMemories()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            try {
                memoryEngine.deleteMemory(memoryId)
                loadMemories()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteAllMemories() {
        viewModelScope.launch {
            try {
                memoryEngine.deleteAllMemories()
                _memories.value = emptyList()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
