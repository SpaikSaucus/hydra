package com.personal.hydra.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.di.AppContainer
import com.personal.hydra.domain.CaffeineCutoff
import com.personal.hydra.domain.DayPace
import com.personal.hydra.domain.Hydration
import com.personal.hydra.domain.MuteManager
import com.personal.hydra.domain.PaceCurve
import com.personal.hydra.domain.PauseManager
import com.personal.hydra.domain.ScheduleGenerator
import com.personal.hydra.domain.TimedIntake
import com.personal.hydra.domain.model.IntakeSource
import com.personal.hydra.domain.model.Ranges
import com.personal.hydra.domain.model.UnitSystem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class EntryRow(val id: Long, val dayKey: String, val time: String, val amountMl: Int)

/** Times behind the caffeine advisory, so the banner can name both of them. */
data class CaffeineNotice(val fromMinute: Int, val sleepMinute: Int)

data class HomeUiState(
    val loading: Boolean = true,
    val unit: UnitSystem = UnitSystem.METRIC,
    val goalMl: Int = 0,
    val consumedMl: Int = 0,
    val remainingMl: Int = 0,
    val progress: Float = 0f,
    val presetsMl: List<Int> = Ranges.DEFAULT_PRESETS,
    val remindersEnabled: Boolean = false,
    val entries: List<EntryRow> = emptyList(),
    /** Days of pause left including today; 0 = tracking is active. */
    val pauseRemainingDays: Int = 0,
    /** Reminders silenced for the rest of today via the top-bar toggle. */
    val remindersMuted: Boolean = false,
    /** Today's cumulative intake against the pacing target. */
    val pace: DayPace? = null,
    /** Non-null while the caffeine advisory applies and the user keeps it on. */
    val caffeineNotice: CaffeineNotice? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository
    private val hydration = container.hydrationRepository
    private val resolver = DayKeyResolver()
    private val zone: ZoneId = ZoneId.systemDefault()
    // Locale-aware short time (24h or AM/PM per the active locale), not forced 24h.
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    init {
        viewModelScope.launch { hydration.ensureToday() }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        settings.config,
        hydration.observeToday(),
        hydration.observeTodayEntries(),
    ) { c, day, entries ->
        val goal = day?.goalMl ?: Hydration.goal(c, LocalDate.now()).goalMl
        val consumed = day?.totalMl ?: 0
        val s = c.settings
        // Same wake -> night-cutoff window the reminders pace against.
        val sleepFromWake = ScheduleGenerator.minutesFromWake(s.wakeTime, s.sleepTime)
            .let { if (it == 0) 1440 else it }
        val windowMinutes = (sleepFromWake - s.nightCutoffBeforeSleepMin).coerceIn(1, 1440)
        // Read at the edge, like the pace curve: the state refreshes on changes
        // rather than ticking on its own.
        val now = LocalTime.now()
        HomeUiState(
            loading = false,
            unit = c.settings.unitSystem,
            goalMl = goal,
            consumedMl = consumed,
            remainingMl = (goal - consumed).coerceAtLeast(0),
            progress = if (goal > 0) consumed.toFloat() / goal else 0f,
            presetsMl = c.settings.presetsMl,
            remindersEnabled = c.settings.remindersEnabled,
            pauseRemainingDays = PauseManager.remainingDays(c.pauses, LocalDate.parse(resolver.todayKey())),
            remindersMuted = MuteManager.isMuted(c.remindersMutedDay, LocalDate.parse(resolver.todayKey())),
            pace = PaceCurve.of(
                goalMl = goal,
                wakeMinute = s.wakeTimeMin,
                windowMinutes = windowMinutes,
                morningSharePct = s.morningSharePct,
                intakes = entries.map { TimedIntake(it.timestamp, it.amountMl) },
                zone = zone,
                now = now,
            ),
            caffeineNotice = CaffeineNotice(
                fromMinute = CaffeineCutoff.warningStartMinute(s.sleepTimeMin),
                sleepMinute = s.sleepTimeMin,
            ).takeIf {
                s.caffeineWarningEnabled &&
                    CaffeineCutoff.shouldWarn(now.hour * 60 + now.minute, s.sleepTimeMin)
            },
            entries = entries.map {
                EntryRow(
                    id = it.id,
                    dayKey = it.dayKey,
                    time = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalTime().format(timeFmt),
                    amountMl = it.amountMl,
                )
            }.reversed(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun quickLog(ml: Int) {
        viewModelScope.launch { hydration.addIntake(ml, IntakeSource.PRESET) }
    }

    fun addCustom(ml: Int, saveAsPreset: Boolean) {
        viewModelScope.launch {
            if (saveAsPreset) {
                val current = settings.snapshot().settings.presetsMl
                if (ml !in current) settings.setPresets((current + ml).distinct().sorted())
            }
            hydration.addIntake(ml, IntakeSource.MANUAL)
        }
    }

    fun deleteEntry(id: Long, dayKey: String) {
        viewModelScope.launch { hydration.undoIntake(id, dayKey) }
    }

    /** Ends the active pause today; already-elapsed paused days stay neutral. */
    fun resumeTracking() {
        viewModelScope.launch {
            val c = settings.snapshot()
            val today = LocalDate.parse(resolver.todayKey())
            settings.setPauses(PauseManager.resumeEarly(c.pauses, today))
        }
    }

    /**
     * Silences / re-enables reminders for the rest of today. Muting also dismisses
     * any notification currently on screen; the mute expires by itself tomorrow.
     */
    fun toggleMuteToday() {
        viewModelScope.launch {
            val c = settings.snapshot()
            val today = LocalDate.parse(resolver.todayKey())
            if (MuteManager.isMuted(c.remindersMutedDay, today)) {
                settings.setRemindersMutedDay(null)
            } else {
                settings.setRemindersMutedDay(MuteManager.muteKey(today))
                container.notifier.cancel()
            }
        }
    }

    companion object {
        // Canonical ml bounds live in Ranges (shared source of truth).
        const val CUSTOM_MIN = Ranges.CUSTOM_MIN_ML
        const val CUSTOM_MAX = Ranges.CUSTOM_MAX_ML
        const val CUSTOM_STEP = Ranges.CUSTOM_STEP_ML
    }
}
