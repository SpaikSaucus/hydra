package com.personal.hydra.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalTime

/**
 * User profile (formula inputs). Persisted in DataStore and exported in the
 * JSON backup. The daily goal is always DERIVED (never stored raw) so changing
 * weight/factor/heat re-adjusts the goal automatically and stays in range.
 */
@Serializable
data class UserProfile(
    val weightKg: Double = 70.0,
    val factorMlKg: Int = Ranges.FACTOR_NORMAL,
    val heatMode: Boolean = false,
    /** True once the user manually toggled heat mode against the season suggestion. */
    val heatModeUserOverridden: Boolean = false,
    val manualAdjustPct: Int = 0,
)

/**
 * App preferences + intake window. Times are stored as minutes-from-midnight
 * (serializable, comparable, sum-friendly for cutoff/redistribution math).
 */
@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /** ISO-3166 alpha-2 region used for season/hemisphere inference. Editable. */
    val countryCode: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val configMode: ConfigMode = ConfigMode.SIMPLE,
    val wakeTimeMin: Int = 7 * 60,
    val sleepTimeMin: Int = 23 * 60,
    val nightCutoffBeforeSleepMin: Int = 180,
    /**
     * Share of the daily goal paced into the FIRST half of the wake->cutoff
     * window; the second half gets the complement (100 - value). Front-loading
     * intake reduces evening accumulation and night-time bathroom trips.
     */
    val morningSharePct: Int = Ranges.MORNING_SHARE_DEFAULT,
    val reminderIntervalMin: Int = 90,
    val snoozeMin: Int = 15,
    val maxIntakePerHourMl: Int = 900,
    val remindersEnabled: Boolean = false,
    val presetsMl: List<Int> = Ranges.DEFAULT_PRESETS,
    val backupMode: BackupMode = BackupMode.LOCAL_ONLY,
) {
    val wakeTime: LocalTime get() = LocalTime.of(wakeTimeMin / 60, wakeTimeMin % 60)
    val sleepTime: LocalTime get() = LocalTime.of(sleepTimeMin / 60, sleepTimeMin % 60)
}

@Serializable
data class OnboardingState(
    val onboardingDone: Boolean = false,
    val lastCompletedStep: Int = 0,
    val schemaVersion: Int = 1,
)

/**
 * One tracking pause, as an INCLUSIVE range of hydration-day keys (ISO-8601
 * dates, so lexicographic comparison is chronological). Elapsed pauses are kept
 * so past paused days stay neutral for streaks.
 */
@Serializable
data class PausePeriod(
    val startDay: String,
    val endDay: String,
) {
    operator fun contains(day: java.time.LocalDate): Boolean = day.toString() in startDay..endDay
}

/** Root config — also the shape of the manual JSON backup's `config` field. */
@Serializable
data class HydraConfig(
    val profile: UserProfile = UserProfile(),
    val settings: AppSettings = AppSettings(),
    val onboarding: OnboardingState = OnboardingState(),
    val pauses: List<PausePeriod> = emptyList(),
    val configSchemaVersion: Int = 1,
)

fun minToLocalTime(min: Int): LocalTime = LocalTime.of((min / 60) % 24, min % 60)
fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
