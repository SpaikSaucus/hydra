package com.personal.hydra

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.personal.hydra.ui.AppViewModel
import com.personal.hydra.ui.BootState
import com.personal.hydra.ui.about.AboutRoute
import com.personal.hydra.ui.history.HistoryRoute
import com.personal.hydra.ui.home.HomeRoute
import com.personal.hydra.ui.onboarding.OnboardingRoute
import com.personal.hydra.ui.settings.SettingsRoute
import com.personal.hydra.ui.theme.HydraTheme
import com.personal.hydra.util.LocaleHelper

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as HydraApp).container.settingsRepository)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { appViewModel.boot.value is BootState.Loading }

        setContent {
            val boot by appViewModel.boot.collectAsStateWithLifecycle()
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            HydraTheme(themeMode = theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (val b = boot) {
                        BootState.Loading -> Unit
                        is BootState.Ready -> HydraRoot(startInOnboarding = !b.onboardingDone)
                    }
                }
            }
        }
    }
}

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_MAIN = "main"
private const val ROUTE_HOME = "home"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"

@Composable
fun HydraRoot(startInOnboarding: Boolean) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == ROUTE_HOME || route == ROUTE_HISTORY || route == ROUTE_SETTINGS

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == ROUTE_HOME,
                        onClick = { nav.navigateTab(ROUTE_HOME) },
                        icon = { Icon(Icons.Rounded.WaterDrop, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_today)) },
                    )
                    NavigationBarItem(
                        selected = route == ROUTE_HISTORY,
                        onClick = { nav.navigateTab(ROUTE_HISTORY) },
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_history)) },
                    )
                    NavigationBarItem(
                        selected = route == ROUTE_SETTINGS,
                        onClick = { nav.navigateTab(ROUTE_SETTINGS) },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startInOnboarding) ROUTE_ONBOARDING else ROUTE_MAIN,
            // Consume the insets already covered by `padding` so the per-screen
            // (nested) Scaffolds don't apply system-bar insets a second time — the
            // double bottom inset was painting a background-coloured strip above
            // the NavigationBar (a black bar in the dark theme).
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(ROUTE_ONBOARDING) {
                OnboardingRoute(
                    onFinished = {
                        nav.navigate(ROUTE_MAIN) {
                            popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            navigation(startDestination = ROUTE_HOME, route = ROUTE_MAIN) {
                composable(ROUTE_HOME) { HomeRoute() }
                composable(ROUTE_HISTORY) { HistoryRoute() }
                composable(ROUTE_SETTINGS) {
                    SettingsRoute(onOpenAbout = { nav.navigate(ROUTE_ABOUT) })
                }
            }
            composable(ROUTE_ABOUT) { AboutRoute(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
