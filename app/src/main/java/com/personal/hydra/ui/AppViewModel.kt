package com.personal.hydra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.hydra.data.settings.SettingsRepository
import com.personal.hydra.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface BootState {
    data object Loading : BootState
    data class Ready(val onboardingDone: Boolean) : BootState
}

class AppViewModel(settings: SettingsRepository) : ViewModel() {

    val boot: StateFlow<BootState> = settings.config
        .map { BootState.Ready(it.onboarding.onboardingDone) as BootState }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BootState.Loading)

    val theme: StateFlow<ThemeMode> = settings.config
        .map { it.settings.theme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    companion object {
        fun factory(settings: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(settings) }
        }
    }
}
