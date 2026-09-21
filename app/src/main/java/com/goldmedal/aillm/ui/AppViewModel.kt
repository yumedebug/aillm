package com.goldmedal.aillm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldmedal.aillm.core.design.ThemeMode
import com.goldmedal.aillm.core.preferences.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appSettings: AppSettings
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        appSettings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** null = not yet read from storage, so we never flash the wrong screen. */
    val isOnboarded: StateFlow<Boolean?> =
        appSettings.isOnboarded
            .map { it as Boolean? }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
