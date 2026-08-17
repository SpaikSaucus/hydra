package com.personal.hydra.ui.settings

import android.Manifest
import android.app.Activity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.hydra.R
import com.personal.hydra.data.backup.BackupErrorCode
import com.personal.hydra.data.backup.BackupOp
import com.personal.hydra.data.backup.BackupOutcome
import com.personal.hydra.domain.CaffeineCutoff
import com.personal.hydra.domain.SeasonInference
import com.personal.hydra.domain.UnitConverter
import com.personal.hydra.domain.model.AppLanguage
import com.personal.hydra.domain.model.BackupMode
import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.Ranges
import com.personal.hydra.domain.model.Season
import com.personal.hydra.domain.model.ThemeMode
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.domain.model.WarningCode
import com.personal.hydra.domain.model.minToLocalTime
import com.personal.hydra.ui.components.CountryField
import com.personal.hydra.ui.components.SectionCard
import com.personal.hydra.ui.components.WarningBanner
import com.personal.hydra.ui.components.WeightStepper
import com.personal.hydra.ui.hydraViewModel
import com.personal.hydra.util.LocaleHelper
import com.personal.hydra.util.VolumeFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(onOpenAbout: () -> Unit) {
    val vm = hydraViewModel { SettingsViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val profile = state.config.profile
    val s = state.config.settings
    val advanced = s.configMode == ConfigMode.ADVANCED
    val timeFmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val season = remember(s.countryCode) { SeasonInference.infer(s.countryCode, LocalDate.now()).season }

    var showAdvancedConfirm by remember { mutableStateOf(false) }
    var pendingDeletePreset by remember { mutableStateOf<Int?>(null) }
    var pendingPauseDays by remember { mutableStateOf<Int?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setRemindersEnabled(true)
    }
    fun requestRemindersOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setRemindersEnabled(true)
        }
    }

    // Cancelling the file picker hands back a null Uri: that is the user backing
    // out, not a failure, so it stays silent.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    // Every export/import ends in a message — silence used to make a failed
    // import indistinguishable from a successful one.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.backupEvents.collect { outcome ->
            snackbar.showSnackbar(backupMessage(context, outcome), duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile
            SectionCard(title = stringResource(R.string.section_profile)) {
                Text(stringResource(R.string.weight), style = MaterialTheme.typography.bodyMedium)
                WeightStepper(kg = profile.weightKg, unit = s.unitSystem, onChange = vm::setWeight)
                Text(stringResource(R.string.units), style = MaterialTheme.typography.bodyMedium)
                ChipRow(
                    options = listOf(
                        UnitSystem.METRIC to stringResource(R.string.unit_metric),
                        UnitSystem.IMPERIAL to stringResource(R.string.unit_imperial),
                    ),
                    selected = s.unitSystem,
                    onSelect = vm::setUnit,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.country), Modifier.weight(1f))
                    CountryField(code = s.countryCode, onSelect = vm::setCountry)
                }
            }

            // Goal + heat
            SectionCard(title = stringResource(R.string.daily_goal)) {
                Text(
                    VolumeFormat.volume(state.goalMl, s.unitSystem),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (advanced) {
                    LabeledSlider(
                        value = profile.manualAdjustPct,
                        range = Ranges.ADJ_MIN..Ranges.ADJ_MAX,
                        enabled = true,
                        label = { pct -> stringResource(R.string.adjust_goal, pct) },
                        onCommit = vm::setAdjust,
                    )
                    LabeledSlider(
                        value = profile.factorMlKg,
                        range = Ranges.FACTOR_MIN..Ranges.FACTOR_MAX,
                        enabled = true,
                        label = { f -> "${stringResource(R.string.factor_label)}: $f" },
                        onCommit = vm::setFactor,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.heat_mode), Modifier.weight(1f))
                        Switch(checked = profile.heatMode, onCheckedChange = vm::setHeat)
                    }
                    val warn = SeasonInference.heatModeWarning(season, profile.heatMode)
                    if (warn?.code == WarningCode.HEAT_MODE_DISABLED_IN_SUMMER) {
                        WarningBanner(stringResource(R.string.heat_mode_off_warning))
                    } else if (warn?.code == WarningCode.HEAT_MODE_ENABLED_IN_WINTER) {
                        WarningBanner(stringResource(R.string.heat_mode_on_info), isInfo = true)
                    }
                } else {
                    Text(
                        stringResource(R.string.heat_auto, stringResource(seasonLabel(season))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Schedule
            SectionCard(title = stringResource(R.string.section_schedule)) {
                Text(
                    stringResource(R.string.schedule_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeRow(stringResource(R.string.wake_time), minToLocalTime(s.wakeTimeMin).format(timeFmt)) {
                    pickTime(context, s.wakeTimeMin) { vm.setWake(it) }
                }
                TimeRow(stringResource(R.string.sleep_time), minToLocalTime(s.sleepTimeMin).format(timeFmt)) {
                    pickTime(context, s.sleepTimeMin) { vm.setSleep(it) }
                }
                LabeledSlider(
                    value = s.nightCutoffBeforeSleepMin,
                    range = Ranges.CUTOFF_MIN..Ranges.CUTOFF_MAX,
                    enabled = advanced,
                    step = 30,
                    label = { min ->
                        stringResource(R.string.night_cutoff) + ": " +
                            stringResource(R.string.night_cutoff_value, min / 60)
                    },
                    onCommit = vm::setCutoff,
                )
                LabeledSlider(
                    value = s.morningSharePct,
                    range = Ranges.MORNING_SHARE_MIN..Ranges.MORNING_SHARE_MAX,
                    enabled = advanced,
                    step = 5,
                    label = { pct ->
                        stringResource(R.string.balance_label) + ": " +
                            stringResource(R.string.balance_value, pct, 100 - pct)
                    },
                    onCommit = vm::setMorningShare,
                )
                Text(
                    stringResource(R.string.balance_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Lives in Schedule, not Reminders: the notice is derived from the
                // sleep time set right above it, and it posts nothing.
                SwitchRow(stringResource(R.string.caffeine_setting), s.caffeineWarningEnabled) {
                    vm.setCaffeineWarning(it)
                }
                Text(
                    stringResource(R.string.caffeine_setting_hint, CaffeineCutoff.HOURS_BEFORE_SLEEP),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!advanced) LockedHint()
            }

            // Pause tracking
            SectionCard(title = stringResource(R.string.section_pause)) {
                if (state.pauseRemainingDays > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.pause_active,
                            state.pauseRemainingDays,
                            formatIsoDay(state.pauseEndDay),
                            state.pauseRemainingDays,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = vm::resumePause, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pause_resume))
                    }
                } else {
                    Text(
                        stringResource(R.string.pause_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ranges.PAUSE_PRESET_DAYS.forEach { days ->
                            OutlinedButton(onClick = { pendingPauseDays = days }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pause_days_option, days))
                            }
                        }
                    }
                }
            }

            // Appearance
            SectionCard(title = stringResource(R.string.section_appearance)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyMedium)
                ChipRow(
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                    ),
                    selected = s.theme,
                    onSelect = vm::setTheme,
                )
                Text(stringResource(R.string.language), style = MaterialTheme.typography.bodyMedium)
                ChipRow(
                    options = listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                        AppLanguage.ES to stringResource(R.string.lang_es),
                        AppLanguage.EN to stringResource(R.string.lang_en),
                    ),
                    selected = s.language,
                    onSelect = { l ->
                        vm.setLanguage(l)
                        LocaleHelper.setLocale(context, langTag(l))
                        (context as? Activity)?.recreate()
                    },
                )
            }

            // Reminders
            SectionCard(title = stringResource(R.string.section_reminders)) {
                SwitchRow(stringResource(R.string.enable_reminders), s.remindersEnabled) { on ->
                    if (on) requestRemindersOn() else vm.setRemindersEnabled(false)
                }
                LabeledSlider(
                    value = s.reminderIntervalMin,
                    range = Ranges.INTERVAL_MIN..Ranges.INTERVAL_MAX,
                    enabled = advanced,
                    step = 15,
                    label = { min ->
                        stringResource(R.string.reminder_frequency) + " " +
                            stringResource(R.string.minutes_value, min)
                    },
                    onCommit = vm::setInterval,
                )
                LabeledSlider(
                    value = s.maxIntakePerHourMl,
                    range = Ranges.MAX_PER_HOUR_MIN..Ranges.MAX_PER_HOUR_MAX,
                    enabled = advanced,
                    step = 100,
                    label = { ml ->
                        stringResource(R.string.hourly_cap) + ": " + VolumeFormat.volume(ml, s.unitSystem)
                    },
                    onCommit = vm::setHourlyCap,
                )
                if (!advanced) LockedHint()
            }

            // Advanced mode + presets
            SectionCard(title = stringResource(R.string.advanced_mode)) {
                SwitchRow(stringResource(R.string.advanced_mode), advanced) { on ->
                    if (on) showAdvancedConfirm = true else vm.setAdvanced(false)
                }
                if (advanced) {
                    Text(stringResource(R.string.presets_label), style = MaterialTheme.typography.bodyMedium)
                    PresetsEditor(
                        presets = s.presetsMl,
                        unit = s.unitSystem,
                        onSize = vm::setPresetSize,
                        onRemove = { pendingDeletePreset = it },
                        onAdd = vm::addPreset,
                    )
                }
            }

            // Backup
            SectionCard(title = stringResource(R.string.section_backup)) {
                ChipRow(
                    options = listOf(
                        BackupMode.LOCAL_ONLY to stringResource(R.string.backup_local),
                        BackupMode.ANDROID_AUTO to stringResource(R.string.backup_auto),
                        BackupMode.MANUAL_JSON to stringResource(R.string.backup_json),
                    ),
                    selected = s.backupMode,
                    onSelect = vm::setBackupMode,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("hydra-backup.json") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.export_data))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.import_data))
                    }
                }
            }

            // About
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.about), Modifier.weight(1f))
                IconButton(onClick = onOpenAbout) { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
            }
        }
    }

    if (showAdvancedConfirm) {
        AlertDialog(
            onDismissRequest = { showAdvancedConfirm = false },
            title = { Text(stringResource(R.string.advanced_enable_title)) },
            text = { Text(stringResource(R.string.advanced_enable_msg)) },
            confirmButton = {
                Button(onClick = { showAdvancedConfirm = false; vm.setAdvanced(true) }) {
                    Text(stringResource(R.string.enable))
                }
            },
            dismissButton = { TextButton(onClick = { showAdvancedConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pendingDeletePreset?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingDeletePreset = null },
            title = { Text(stringResource(R.string.delete_preset_title)) },
            text = { Text(VolumeFormat.volume(s.presetsMl.getOrElse(index) { 0 }, s.unitSystem)) },
            confirmButton = {
                Button(onClick = { vm.removePreset(index); pendingDeletePreset = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeletePreset = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pendingPauseDays?.let { days ->
        AlertDialog(
            onDismissRequest = { pendingPauseDays = null },
            title = { Text(stringResource(R.string.pause_confirm_title, days)) },
            text = { Text(stringResource(R.string.pause_confirm_msg)) },
            confirmButton = {
                Button(onClick = { vm.startPause(days); pendingPauseDays = null }) {
                    Text(stringResource(R.string.pause_button))
                }
            },
            dismissButton = { TextButton(onClick = { pendingPauseDays = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Slider that persists only the RELEASED value.
 *
 * `Slider.onValueChange` fires on every pointer delta, and every persisted value
 * is a DataStore file write whose emission restarts the Room "today" queries,
 * re-opens the day snapshot and re-runs the whole history analytics. Writing per
 * pixel turned one drag into dozens of full cascades (and dozens of fsyncs), so
 * the dragged position is kept here and committed once, on release.
 *
 * [label] receives the value being SHOWN, so the read-out still tracks the finger.
 */
@Composable
private fun LabeledSlider(
    value: Int,
    range: IntRange,
    enabled: Boolean,
    step: Int = 1,
    label: @Composable (Int) -> String,
    onCommit: (Int) -> Unit,
) {
    // Re-seeded whenever the committed value arrives, which also drops the pending
    // drag once it has been persisted.
    var pending by remember(value) { mutableStateOf<Int?>(null) }
    val shown = pending ?: value
    Column {
        Text(
            label(shown),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = shown.coerceIn(range.first, range.last).toFloat(),
            onValueChange = { pending = snapToStep(it, step, range) },
            onValueChangeFinished = { pending?.let(onCommit) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

/** Slider positions are continuous; every setting here is a discrete step. */
private fun snapToStep(raw: Float, step: Int, range: IntRange): Int =
    ((raw / step).roundToInt() * step).coerceIn(range.first, range.last)

@Composable
private fun LockedHint() {
    Text(
        stringResource(R.string.locked_advanced_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(value, style = MaterialTheme.typography.titleMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun PresetsEditor(
    presets: List<Int>,
    unit: UnitSystem,
    onSize: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    // 1 fl oz vs 50 ml — imperial step derived from the converter (matches SettingsViewModel.addPreset).
    val step = if (unit == UnitSystem.IMPERIAL) UnitConverter.flozToMl(1.0) else 50
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEachIndexed { index, ml ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(VolumeFormat.volume(ml, unit), Modifier.weight(1f))
                IconButton(onClick = { onSize(index, -step) }) { Icon(Icons.Rounded.Remove, contentDescription = "-") }
                IconButton(onClick = { onSize(index, step) }) { Icon(Icons.Rounded.Add, contentDescription = "+") }
                IconButton(onClick = { onRemove(index) }, enabled = presets.size > 1) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
        if (presets.size < 8) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.add_size), Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun formatIsoDay(iso: String?): String = iso?.let {
    runCatching {
        LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrDefault(it)
} ?: ""

/**
 * The one place a backup outcome becomes words. The data layer only produces
 * codes and row counts (same rule the domain's warnings follow), so both locales
 * are translated here and a success always states what it moved.
 */
private fun backupMessage(context: Context, outcome: BackupOutcome): String = when (outcome) {
    is BackupOutcome.Done -> context.getString(
        if (outcome.op == BackupOp.EXPORT) R.string.backup_export_ok else R.string.backup_import_ok,
        outcome.report.days,
        outcome.report.entries,
    )
    is BackupOutcome.Failed -> context.getString(
        if (outcome.op == BackupOp.EXPORT) R.string.backup_export_failed else R.string.backup_import_failed,
        context.getString(backupReason(outcome.code)),
    )
}

private fun backupReason(code: BackupErrorCode): Int = when (code) {
    BackupErrorCode.OPEN_DESTINATION -> R.string.backup_error_open_dest
    BackupErrorCode.OPEN_SOURCE -> R.string.backup_error_open_src
    BackupErrorCode.NEWER_SCHEMA -> R.string.backup_error_newer_version
    BackupErrorCode.MALFORMED -> R.string.backup_error_malformed
    BackupErrorCode.UNKNOWN -> R.string.backup_error_unknown
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

private fun langTag(l: AppLanguage): String = when (l) {
    AppLanguage.SYSTEM -> ""
    AppLanguage.ES -> "es"
    AppLanguage.EN -> "en"
}
