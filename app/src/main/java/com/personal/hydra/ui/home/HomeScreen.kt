package com.personal.hydra.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.hydra.R
import com.personal.hydra.domain.CaffeineCutoff
import com.personal.hydra.domain.UnitConverter
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.domain.model.minToLocalTime
import com.personal.hydra.ui.components.IntStepperField
import com.personal.hydra.ui.components.PaceChart
import com.personal.hydra.ui.components.SectionCard
import com.personal.hydra.ui.components.WarningBanner
import com.personal.hydra.ui.components.WaterProgressRing
import com.personal.hydra.ui.hydraViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.personal.hydra.util.VolumeFormat
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun HomeRoute() {
    val vm = hydraViewModel { HomeViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onLog = vm::quickLog,
        onCustom = vm::addCustom,
        onDelete = vm::deleteEntry,
        onResume = vm::resumeTracking,
        onToggleMute = vm::toggleMuteToday,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onLog: (Int) -> Unit,
    onCustom: (Int, Boolean) -> Unit,
    onDelete: (Long, String) -> Unit,
    onResume: () -> Unit = {},
    onToggleMute: () -> Unit = {},
) {
    var showCustom by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EntryRow?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val timeFmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val celebrationMsg = stringResource(R.string.goal_reached_celebration)
    val mutedMsg = stringResource(R.string.mute_today_msg)
    val unmutedMsg = stringResource(R.string.unmute_today_msg)
    val reached = state.goalMl > 0 && state.consumedMl >= state.goalMl
    var celebrated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(reached) {
        if (reached && !celebrated) {
            celebrated = true
            snackbar.showSnackbar(celebrationMsg)
        } else if (!reached) {
            celebrated = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    // Only meaningful while reminders are on; a multi-day tracking
                    // pause already silences everything, so hide it there too.
                    if (state.remindersEnabled && state.pauseRemainingDays == 0) {
                        val muted = state.remindersMuted
                        IconButton(
                            onClick = {
                                onToggleMute()
                                scope.launch { snackbar.showSnackbar(if (muted) unmutedMsg else mutedMsg) }
                            },
                        ) {
                            Icon(
                                if (muted) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                                contentDescription = stringResource(
                                    if (muted) R.string.unmute_today else R.string.mute_today,
                                ),
                                tint = if (muted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.pauseRemainingDays > 0) {
                PauseBanner(daysLeft = state.pauseRemainingDays, onResume = onResume)
            }
            WaterProgressRing(
                progress = state.progress,
                centerLabel = stringResource(
                    R.string.goal_progress,
                    VolumeFormat.volume(state.consumedMl, state.unit),
                    VolumeFormat.volume(state.goalMl, state.unit),
                ),
                subLabel = if (reached) {
                    stringResource(R.string.goal_reached)
                } else {
                    stringResource(R.string.remaining_left, VolumeFormat.volume(state.remainingMl, state.unit))
                },
                overGoal = reached,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Advisory only, and one line tall on purpose: it sits between the ring
            // and the logging buttons, which is where you look before reaching for
            // a coffee, without pushing the buttons off screen.
            state.caffeineNotice?.let { notice ->
                WarningBanner(
                    stringResource(
                        R.string.caffeine_notice,
                        minToLocalTime(notice.sleepMinute).format(timeFmt),
                        minToLocalTime(notice.fromMinute).format(timeFmt),
                        CaffeineCutoff.HOURS_BEFORE_SLEEP,
                    ),
                    isInfo = true,
                )
            }

            SectionCard(title = stringResource(R.string.add_water)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.presetsMl.forEach { ml ->
                        AssistChip(
                            onClick = { onLog(ml) },
                            label = { Text(VolumeFormat.volume(ml, state.unit)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.LocalDrink, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                            },
                        )
                    }
                }
                FilledTonalButton(onClick = { showCustom = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.custom_amount), Modifier.padding(start = 8.dp))
                }
            }

            state.pace?.let { pace ->
                SectionCard(title = stringResource(R.string.pace_title)) {
                    PaceChart(pace = pace)
                    val delta = pace.deltaMl
                    Text(
                        when {
                            delta >= 0 -> stringResource(
                                R.string.pace_ahead,
                                VolumeFormat.volume(delta, state.unit),
                            )
                            else -> stringResource(
                                R.string.pace_behind,
                                VolumeFormat.volume(-delta, state.unit),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        stringResource(R.string.pace_legend),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.today_log)) {
                if (state.entries.isEmpty()) {
                    Text(
                        stringResource(R.string.no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.entries.forEach { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.time, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                VolumeFormat.volume(e.amountMl, state.unit),
                                Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(onClick = { pendingDelete = e }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }

            val footnote = when {
                !state.remindersEnabled -> R.string.no_reminder
                state.remindersMuted -> R.string.mute_today_active
                else -> null
            }
            footnote?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showCustom) {
        CustomAmountDialog(
            unit = state.unit,
            onConfirm = { ml, save -> showCustom = false; onCustom(ml, save) },
            onDismiss = { showCustom = false },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text("${entry.time} · ${VolumeFormat.volume(entry.amountMl, state.unit)}") },
            confirmButton = {
                Button(onClick = { onDelete(entry.id, entry.dayKey); pendingDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PauseBanner(daysLeft: Int, onResume: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Pause, contentDescription = null)
            Text(
                pluralStringResource(R.plurals.pause_banner, daysLeft, daysLeft),
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onResume) { Text(stringResource(R.string.pause_resume)) }
        }
    }
}

@Composable
private fun CustomAmountDialog(
    unit: UnitSystem,
    onConfirm: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val metric = unit == UnitSystem.METRIC
    // Seed a round value in the active unit so the display and the canonical ml
    // never start out mismatched (imperial: 8 fl oz = 237 ml, not 250 -> 8 -> 236).
    var amountMl by remember(unit) {
        mutableIntStateOf(if (metric) 250 else UnitConverter.flozToMl(8.0))
    }
    var saveAsPreset by remember { mutableStateOf(false) }
    // Imperial bounds derived from the canonical ml range so both units cover the
    // same [CUSTOM_MIN, CUSTOM_MAX] ml span (ceil the min / floor the max to stay inside it).
    val minFloz = ceil(UnitConverter.mlToFloz(HomeViewModel.CUSTOM_MIN)).toInt()
    val maxFloz = floor(UnitConverter.mlToFloz(HomeViewModel.CUSTOM_MAX)).toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_amount)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (metric) {
                    IntStepperField(
                        value = amountMl,
                        onValueChange = { amountMl = it },
                        suffix = "ml",
                        min = HomeViewModel.CUSTOM_MIN,
                        max = HomeViewModel.CUSTOM_MAX,
                        step = HomeViewModel.CUSTOM_STEP,
                    )
                } else {
                    IntStepperField(
                        value = UnitConverter.mlToFloz(amountMl).roundToInt(),
                        onValueChange = { amountMl = UnitConverter.flozToMl(it.toDouble()) },
                        suffix = "fl oz",
                        min = minFloz,
                        max = maxFloz,
                        step = 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveAsPreset, onCheckedChange = { saveAsPreset = it })
                    Text(stringResource(R.string.save_as_quick), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(amountMl, saveAsPreset) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
