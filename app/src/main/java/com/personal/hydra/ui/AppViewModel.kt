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

    // ONE eager collector on the config for both process-lifetime flows. Two
    // separate stateIn(Eagerly) calls meant two DataStore collectors decoding the
    // same JSON on every write, for as long as the process lived.
    private val config = settings.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val boot: StateFlow<BootState> = config
        .map { c -> if (c == null) BootState.Loading else BootState.Ready(c.onboarding.onboardingDone) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BootState.Loading)

    val theme: StateFlow<ThemeMode> = config
        .map { it?.settings?.theme ?: ThemeMode.SYSTEM }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    companion object {
        fun factory(settings: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(settings) }
        }
    }
}
