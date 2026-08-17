package com.personal.hydra.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalTime

// ----------------------------------------------------------------------------
// Canonical enums. @Serializable because several are reused both in the
// Preferences DataStore wrapper and in the JSON backup format.
// ----------------------------------------------------------------------------
@Serializable enum class UnitSystem { METRIC, IMPERIAL }
@Serializable enum class Hemisphere { NORTH, SOUTH }
@Serializable enum class Season { SUMMER, AUTUMN, WINTER, SPRING }
@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }
@Serializable enum class AppLanguage { SYSTEM, ES, EN }
@Serializable enum class ConfigMode { SIMPLE, ADVANCED }
@Serializable enum class BackupMode { LOCAL_ONLY, ANDROID_AUTO, MANUAL_JSON }
@Serializable enum class IntakeSource { MANUAL, PRESET, NOTIFICATION }

/** How the 12-week card is drawn. GRID = calendar squares, BARS = one bar per week. */
@Serializable enum class HeatmapStyle { GRID, BARS }

/** Valid ranges and canonical defaults shared by domain + settings + UI. */
object Ranges {
    const val WEIGHT_MIN = 30.0
    const val WEIGHT_MAX = 250.0
    const val FACTOR_MIN = 30
    const val FACTOR_MAX = 40
    const val FACTOR_NORMAL = 33
    const val FACTOR_HEAT = 40
    const val ADJ_MIN = -20
    const val ADJ_MAX = 20
    const val INTERVAL_MIN = 30
    const val INTERVAL_MAX = 240
    const val CUTOFF_MIN = 0
    const val CUTOFF_MAX = 360
    const val MAX_PER_HOUR_MIN = 400
    const val MAX_PER_HOUR_MAX = 1000
    const val SNOOZE_MIN = 5
    const val SNOOZE_MAX = 60
    const val PRESET_MIN = 50
    const val PRESET_MAX = 2000
    // Custom-amount dialog bounds (canonical ml). Imperial bounds are derived from
    // these via UnitConverter so both unit systems cover the same ml range.
    const val CUSTOM_MIN_ML = 50
    const val CUSTOM_MAX_ML = 2000
    const val CUSTOM_STEP_ML = 50
    // Morning/afternoon balance: complementary ranges (100 - [45..70] = [30..55]),
    // so a single morning value always yields a 100% total. 65/35 default backed
    // by nocturia guidance (evening fluid restriction) + circadian ADH rhythm.
    const val MORNING_SHARE_MIN = 45
    const val MORNING_SHARE_MAX = 70
    const val MORNING_SHARE_DEFAULT = 65
    const val PAUSE_DAYS_MAX = 30
    val PAUSE_PRESET_DAYS = listOf(5, 10, 30)
    val DEFAULT_PRESETS = listOf(250, 500, 750)
    // ml equivalents of nice imperial sizes: 8, 16, 24 fl oz.
    val DEFAULT_PRESETS_IMPERIAL = listOf(237, 473, 710)
    fun defaultPresets(metric: Boolean): List<Int> = if (metric) DEFAULT_PRESETS else DEFAULT_PRESETS_IMPERIAL
}

// ----------------------------------------------------------------------------
// Warnings: neutral codes; the UI translates them to es/en strings.
// The domain never produces user-facing text.
// ----------------------------------------------------------------------------
@Serializable
enum class WarningCode {
    WEIGHT_CLAMPED,
    FACTOR_CLAMPED,
    ADJUSTMENT_CLAMPED,
    HEAT_MODE_DISABLED_IN_SUMMER,
    HEAT_MODE_ENABLED_IN_WINTER,
    GOAL_DOES_NOT_FIT_WINDOW,
    SCHEDULE_EXTENDED_PAST_CUTOFF,
    BEHIND_SCHEDULE_AGGRESSIVE,
}

data class DomainWarning(val code: WarningCode, val detail: Double? = null)

// ---------------------------- Goal calculation ------------------------------
data class GoalInput(
    val weightKg: Double,
    val factorMlKg: Int,
    val heatMode: Boolean,
    val manualAdjustPct: Int,
)

data class GoalResult(
    val goalMl: Int,
    val baseGoalMl: Int,
    val effectiveFactor: Int,
    val warnings: List<DomainWarning> = emptyList(),
)

// ------------------------------- Scheduling ---------------------------------
data class Intake(val time: LocalTime, val nextDay: Boolean, val amountMl: Int)

data class Schedule(
    val intakes: List<Intake>,
    val totalMl: Int,
    val warnings: List<DomainWarning> = emptyList(),
)

data class ScheduleParams(
    val wakeTime: LocalTime,
    val sleepTime: LocalTime,
    val nightCutoffMinutes: Int = 180,
    val goalMl: Int,
    val maxPerHourMl: Int = 1000,
    val slotMinutes: Int = 60,
    val endWeight: Double = 0.6,
    val roundToMl: Int = 10,
)

// ------------------ Decision consumed by the reminder Worker -----------------
data class ReminderDecision(
    val shouldNotify: Boolean,
    val reason: Reason,
    val consumedMl: Int,
    val goalMl: Int,
    val remainingMl: Int,
    val nextTargetMl: Int,
    val isBehind: Boolean,
    val overflowWarning: Boolean,
) {
    enum class Reason { DUE, BEHIND, ALREADY_DONE, NIGHT_CUTOFF, NOT_YET, PAUSED, MUTED }
}

// --------------------------- Season inference --------------------------------
@Serializable
data class SeasonInfo(
    val countryCode: String,
    val hemisphere: Hemisphere,
    val season: Season,
    val suggestsHeatMode: Boolean,
)
