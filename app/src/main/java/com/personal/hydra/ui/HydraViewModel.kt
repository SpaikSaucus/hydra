package com.personal.hydra.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.hydra.HydraApp
import com.personal.hydra.di.AppContainer

/** Builds a screen ViewModel from the app's manual DI container. */
@Composable
inline fun <reified VM : ViewModel> hydraViewModel(noinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as HydraApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
