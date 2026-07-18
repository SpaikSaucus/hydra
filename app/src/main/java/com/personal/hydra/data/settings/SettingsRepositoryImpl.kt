package com.personal.hydra.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.personal.hydra.domain.model.AppLanguage
import com.personal.hydra.domain.model.AppSettings
import com.personal.hydra.domain.model.BackupMode
import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.PausePeriod
import com.personal.hydra.domain.model.Ranges
import com.personal.hydra.domain.model.ThemeMode
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores the whole [HydraConfig] as a single JSON value in Preferences DataStore.
 * Small, flat config -> one blob keeps the code minimal and makes the JSON
 * backup trivial (it is the same shape). Single-user app, so whole-blob writes
 * are fine.
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : SettingsRepository {

    private val key = stringPreferencesKey("hydra_config_json")

    override val config: Flow<HydraConfig> = dataStore.data.map { prefs -> decode(prefs[key]) }

    override suspend fun snapshot(): HydraConfig = config.first()

    private fun decode(raw: String?): HydraConfig {
        val parsed = raw?.let { runCatching { json.decodeFromString<HydraConfig>(it) }.getOrNull() } ?: HydraConfig()
        return migrate(parsed)
    }

    /**
     * Forward-migrates a decoded config. Additive fields are handled automatically
     * by kotlinx-serialization defaults + ignoreUnknownKeys; this hook is where any
     * BREAKING shape change gets upgraded, branching on [configSchemaVersion].
     * Currently a no-op beyond stamping the current version (schema v1).
     */
    private fun migrate(c: HydraConfig): HydraConfig =
        if (c.configSchemaVersion >= CONFIG_SCHEMA_VERSION) c
        else c.copy(configSchemaVersion = CONFIG_SCHEMA_VERSION)

    private suspend fun update(transform: (HydraConfig) -> HydraConfig) {
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(transform(decode(prefs[key])))
        }
    }

    private inline fun HydraConfig.mapProfile(f: (UserProfile) -> UserProfile) = copy(profile = f(profile))
    private inline fun HydraConfig.mapSettings(f: (AppSettings) -> AppSettings) = copy(settings = f(settings))

    override suspend fun setWeightKg(kg: Double) = update {
        it.mapProfile { p -> p.copy(weightKg = kg.coerceIn(Ranges.WEIGHT_MIN, Ranges.WEIGHT_MAX)) }
    }

    override suspend fun setFactor(mlPerKg: Int) = update {
        it.mapProfile { p -> p.copy(factorMlKg = mlPerKg.coerceIn(Ranges.FACTOR_MIN, Ranges.FACTOR_MAX)) }
    }

    override suspend fun setManualAdjust(pct: Int) = update {
        it.mapProfile { p -> p.copy(manualAdjustPct = pct.coerceIn(Ranges.ADJ_MIN, Ranges.ADJ_MAX)) }
    }

    override suspend fun setHeatMode(enabled: Boolean, userInitiated: Boolean) = update {
        it.mapProfile { p ->
            p.copy(heatMode = enabled, heatModeUserOverridden = userInitiated || p.heatModeUserOverridden)
        }
    }

    override suspend fun setWakeTime(min: Int) = update {
        it.mapSettings { s -> s.copy(wakeTimeMin = min.coerceIn(0, 1439)) }
    }

    override suspend fun setSleepTime(min: Int) = update {
        it.mapSettings { s -> s.copy(sleepTimeMin = min.coerceIn(0, 1439)) }
    }

    override suspend fun setNightCutoff(min: Int) = update {
        it.mapSettings { s -> s.copy(nightCutoffBeforeSleepMin = min.coerceIn(Ranges.CUTOFF_MIN, Ranges.CUTOFF_MAX)) }
    }

    override suspend fun setMorningShare(pct: Int) = update {
        it.mapSettings { s -> s.copy(morningSharePct = pct.coerceIn(Ranges.MORNING_SHARE_MIN, Ranges.MORNING_SHARE_MAX)) }
    }

    override suspend fun setPauses(pauses: List<PausePeriod>) = update {
        it.copy(pauses = sanitizedPauses(pauses))
    }

    /**
     * Drops malformed periods (unparseable dates or start > end). Applied on
     * every path that can write pauses — including [replaceConfig], which backup
     * import uses — because a poisoned period would hang StreakCalculator's
     * backwards walk or crash PauseManager's date parsing.
     */
    private fun sanitizedPauses(pauses: List<PausePeriod>): List<PausePeriod> =
        pauses.filter { p ->
            runCatching {
                !java.time.LocalDate.parse(p.startDay).isAfter(java.time.LocalDate.parse(p.endDay))
            }.getOrDefault(false)
        }

    override suspend fun setReminderInterval(min: Int) = update {
        it.mapSettings { s -> s.copy(reminderIntervalMin = min.coerceIn(Ranges.INTERVAL_MIN, Ranges.INTERVAL_MAX)) }
    }

    override suspend fun setSnooze(min: Int) = update {
        it.mapSettings { s -> s.copy(snoozeMin = min.coerceIn(Ranges.SNOOZE_MIN, Ranges.SNOOZE_MAX)) }
    }

    override suspend fun setHourlyCap(ml: Int) = update {
        it.mapSettings { s -> s.copy(maxIntakePerHourMl = ml.coerceIn(Ranges.MAX_PER_HOUR_MIN, Ranges.MAX_PER_HOUR_MAX)) }
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) = update {
        it.mapSettings { s -> s.copy(remindersEnabled = enabled) }
    }

    override suspend fun setConfigMode(mode: ConfigMode) = update {
        it.mapSettings { s -> s.copy(configMode = mode) }
    }

    override suspend fun setUnitSystem(u: UnitSystem) = update {
        it.mapSettings { s ->
            // If presets are still the (untouched) defaults, swap them for the new unit's
            // nice defaults; keep them if the user customised them.
            val wasDefault = s.presetsMl == Ranges.DEFAULT_PRESETS || s.presetsMl == Ranges.DEFAULT_PRESETS_IMPERIAL
            val presets = if (wasDefault) Ranges.defaultPresets(u == UnitSystem.METRIC) else s.presetsMl
            s.copy(unitSystem = u, presetsMl = presets)
        }
    }

    override suspend fun setCountry(code: String) = update {
        it.mapSettings { s -> s.copy(countryCode = code.trim().uppercase()) }
    }

    override suspend fun setTheme(m: ThemeMode) = update { it.mapSettings { s -> s.copy(theme = m) } }

    override suspend fun setLanguage(l: AppLanguage) = update { it.mapSettings { s -> s.copy(language = l) } }

    override suspend fun setPresets(presetsMl: List<Int>) = update {
        it.mapSettings { s -> s.copy(presetsMl = presetsMl.map { v -> v.coerceIn(Ranges.PRESET_MIN, Ranges.PRESET_MAX) }) }
    }

    override suspend fun setBackupMode(m: BackupMode) = update { it.mapSettings { s -> s.copy(backupMode = m) } }

    override suspend fun setInferredSeasonCache(seasonName: String) = Unit // reserved; season recomputed each open

    override suspend fun markOnboardingDone() = update {
        it.copy(onboarding = it.onboarding.copy(onboardingDone = true, lastCompletedStep = 7))
    }

    override suspend fun setOnboardingStep(step: Int) = update {
        it.copy(onboarding = it.onboarding.copy(lastCompletedStep = step))
    }

    override suspend fun replaceConfig(c: HydraConfig) = update { c.copy(pauses = sanitizedPauses(c.pauses)) }

    private companion object {
        /** Current config JSON schema version; bump + branch in [migrate] on breaking changes. */
        const val CONFIG_SCHEMA_VERSION = 1
    }
}
