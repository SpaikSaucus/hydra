package com.personal.hydra.ui.onboarding

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.hydra.R
import com.personal.hydra.domain.model.Season
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.domain.model.minToLocalTime
import com.personal.hydra.ui.components.CountryField
import com.personal.hydra.ui.components.WarningBanner
import com.personal.hydra.ui.components.WeightStepper
import com.personal.hydra.ui.hydraViewModel
import com.personal.hydra.util.VolumeFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val STEPS = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingRoute(onFinished: () -> Unit) {
    val vm = hydraViewModel { OnboardingViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var step by remember { mutableIntStateOf(0) }
    var remindersGranted by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remindersGranted = granted
    }
    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            remindersGranted = true
        }
    }

    val timeFmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.back))
                    }
                }
                Button(
                    onClick = {
                        if (step < STEPS - 1) step++ else vm.finish(remindersGranted, onFinished)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (step < STEPS - 1) R.string.next else R.string.start))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                0 -> {
                    Title(stringResource(R.string.ob_welcome_title))
                    Text(stringResource(R.string.ob_welcome_msg), style = MaterialTheme.typography.bodyLarge)
                }

                1 -> {
                    Title(stringResource(R.string.ob_units_title))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.country), Modifier.weight(1f))
                        CountryField(code = state.countryCode, onSelect = vm::setCountry)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.unitSystem == UnitSystem.METRIC, { vm.setUnit(UnitSystem.METRIC) }, { Text(stringResource(R.string.unit_metric)) })
                        FilterChip(state.unitSystem == UnitSystem.IMPERIAL, { vm.setUnit(UnitSystem.IMPERIAL) }, { Text(stringResource(R.string.unit_imperial)) })
                    }
                    Title(stringResource(R.string.ob_weight_title))
                    WeightStepper(kg = state.weightKg, unit = state.unitSystem, onChange = vm::setWeight)
                }

                2 -> {
                    Title(stringResource(R.string.ob_schedule_title))
                    TimeRow(stringResource(R.string.wake_time), minToLocalTime(state.wakeMin).format(timeFmt)) {
                        pickTime(context, state.wakeMin) { vm.setWake(it) }
                    }
                    TimeRow(stringResource(R.string.sleep_time), minToLocalTime(state.sleepMin).format(timeFmt)) {
                        pickTime(context, state.sleepMin) { vm.setSleep(it) }
                    }
                }

                3 -> {
                    Title(stringResource(R.string.ob_season_title))
                    Text(stringResource(R.string.ob_detected, stringResource(seasonLabel(state.season))))
                    Text(
                        stringResource(R.string.ob_goal_is, VolumeFormat.volume(state.goalMl, state.unitSystem)),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    WarningBanner(stringResource(R.string.ob_season_msg), isInfo = true)
                }

                4 -> {
                    Title(stringResource(R.string.ob_notifications_title))
                    Text(stringResource(R.string.ob_notifications_msg), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { requestPermission() }) {
                        Text(stringResource(R.string.grant_notifications))
                    }
                    if (remindersGranted) {
                        Text(stringResource(R.string.ob_summary_title), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Start)
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(value, style = MaterialTheme.typography.titleMedium) }
    }
}

private fun seasonLabel(season: Season): Int = when (season) {
    Season.SUMMER -> R.string.season_summer
    Season.AUTUMN -> R.string.season_autumn
    Season.WINTER -> R.string.season_winter
    Season.SPRING -> R.string.season_spring
}

private fun pickTime(context: Context, initialMin: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initialMin / 60,
        initialMin % 60,
        true,
    ).show()
}
