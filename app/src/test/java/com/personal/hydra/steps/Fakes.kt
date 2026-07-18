package com.personal.hydra.steps

import com.personal.hydra.data.db.dao.DayLogDao
import com.personal.hydra.data.db.dao.IntakeDao
import com.personal.hydra.data.db.entity.DayLogEntity
import com.personal.hydra.data.db.entity.IntakeEntryEntity
import com.personal.hydra.data.settings.SettingsRepository
import com.personal.hydra.domain.model.AppLanguage
import com.personal.hydra.domain.model.AppSettings
import com.personal.hydra.domain.model.BackupMode
import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.PausePeriod
import com.personal.hydra.domain.model.ThemeMode
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeDayLogDao : DayLogDao {
    val store = linkedMapOf<String, DayLogEntity>()

    override suspend fun insertIfAbsent(day: DayLogEntity): Long {
        if (store.containsKey(day.dayKey)) return -1
        store[day.dayKey] = day
        return 1
    }

    override suspend fun getDay(dayKey: String): DayLogEntity? = store[dayKey]
    override fun observeDay(dayKey: String): Flow<DayLogEntity?> = flowOf(store[dayKey])
    override suspend fun setTotal(dayKey: String, total: Int) {
        store[dayKey]?.let { store[dayKey] = it.copy(totalMl = total) }
    }

    override suspend fun close(dayKey: String) {
        store[dayKey]?.let { store[dayKey] = it.copy(closed = true) }
    }

    override suspend fun closeDaysBefore(todayKey: String) {
        store.values.filter { !it.closed && it.dayKey < todayKey }
            .forEach { store[it.dayKey] = it.copy(closed = true) }
    }

    override suspend fun openDaysBefore(todayKey: String): List<DayLogEntity> =
        store.values.filter { !it.closed && it.dayKey < todayKey }

    // No intake source here; production recompute lives in Room. No test drives
    // backup import through the fakes (BackupManager uses the real DB).
    override suspend fun recomputeAllTotals() = Unit

    override fun observeAllDays(): Flow<List<DayLogEntity>> = flowOf(store.values.sortedByDescending { it.dayKey })
    override suspend fun allForExport(): List<DayLogEntity> = store.values.sortedBy { it.dayKey }
    override suspend fun update(day: DayLogEntity) { store[day.dayKey] = day }
    override suspend fun upsertAll(days: List<DayLogEntity>) { days.forEach { store[it.dayKey] = it } }
    override suspend fun clear() { store.clear() }
}

class FakeIntakeDao : IntakeDao {
    val entries = mutableListOf<IntakeEntryEntity>()
    private var nextId = 1L

    override suspend fun insert(entry: IntakeEntryEntity): Long {
        val id = nextId++
        entries += entry.copy(id = id)
        return id
    }

    override suspend fun softDelete(id: Long, ts: Long) {
        val i = entries.indexOfFirst { it.id == id }
        if (i >= 0) entries[i] = entries[i].copy(deletedAt = ts)
    }

    override suspend fun restore(id: Long) {
        val i = entries.indexOfFirst { it.id == id }
        if (i >= 0) entries[i] = entries[i].copy(deletedAt = null)
    }

    override fun observeEntriesOfDay(dayKey: String): Flow<List<IntakeEntryEntity>> =
        flowOf(entries.filter { it.dayKey == dayKey && it.deletedAt == null }.sortedBy { it.timestamp })

    override suspend fun sumOfDay(dayKey: String): Int =
        entries.filter { it.dayKey == dayKey && it.deletedAt == null }.sumOf { it.amountMl }

    override suspend fun sumSince(sinceMillis: Long): Int =
        entries.filter { it.deletedAt == null && it.timestamp >= sinceMillis }.sumOf { it.amountMl }

    override suspend fun lastTimestampOfDay(dayKey: String): Long? =
        entries.filter { it.dayKey == dayKey && it.deletedAt == null }.maxOfOrNull { it.timestamp }

    override suspend fun allForExport(): List<IntakeEntryEntity> = entries.sortedBy { it.timestamp }
    override suspend fun upsertAll(entries: List<IntakeEntryEntity>) { this.entries += entries }
    override suspend fun clear() { entries.clear() }

    fun lastInserted(): IntakeEntryEntity? = entries.lastOrNull { it.deletedAt == null }
}

class FakeSettingsRepository(initial: HydraConfig = HydraConfig()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val config: Flow<HydraConfig> = state
    override suspend fun snapshot(): HydraConfig = state.value

    private fun p(f: (UserProfile) -> UserProfile) { state.value = state.value.copy(profile = f(state.value.profile)) }
    private fun s(f: (AppSettings) -> AppSettings) { state.value = state.value.copy(settings = f(state.value.settings)) }

    override suspend fun setWeightKg(kg: Double) = p { it.copy(weightKg = kg) }
    override suspend fun setFactor(mlPerKg: Int) = p { it.copy(factorMlKg = mlPerKg) }
    override suspend fun setManualAdjust(pct: Int) = p { it.copy(manualAdjustPct = pct) }
    override suspend fun setHeatMode(enabled: Boolean, userInitiated: Boolean) =
        p { it.copy(heatMode = enabled, heatModeUserOverridden = userInitiated || it.heatModeUserOverridden) }
    override suspend fun setWakeTime(min: Int) = s { it.copy(wakeTimeMin = min) }
    override suspend fun setSleepTime(min: Int) = s { it.copy(sleepTimeMin = min) }
    override suspend fun setNightCutoff(min: Int) = s { it.copy(nightCutoffBeforeSleepMin = min) }
    override suspend fun setMorningShare(pct: Int) = s { it.copy(morningSharePct = pct) }
    override suspend fun setPauses(pauses: List<PausePeriod>) {
        state.value = state.value.copy(pauses = pauses)
    }
    override suspend fun setReminderInterval(min: Int) = s { it.copy(reminderIntervalMin = min) }
    override suspend fun setSnooze(min: Int) = s { it.copy(snoozeMin = min) }
    override suspend fun setHourlyCap(ml: Int) = s { it.copy(maxIntakePerHourMl = ml) }
    override suspend fun setRemindersEnabled(enabled: Boolean) = s { it.copy(remindersEnabled = enabled) }
    override suspend fun setConfigMode(mode: ConfigMode) = s { it.copy(configMode = mode) }
    override suspend fun setUnitSystem(u: UnitSystem) = s { it.copy(unitSystem = u) }
    override suspend fun setCountry(code: String) = s { it.copy(countryCode = code.trim().uppercase()) }
    override suspend fun setTheme(m: ThemeMode) = s { it.copy(theme = m) }
    override suspend fun setLanguage(l: AppLanguage) = s { it.copy(language = l) }
    override suspend fun setPresets(presetsMl: List<Int>) = s { it.copy(presetsMl = presetsMl) }
    override suspend fun setBackupMode(m: BackupMode) = s { it.copy(backupMode = m) }
    override suspend fun setInferredSeasonCache(seasonName: String) = Unit
    override suspend fun markOnboardingDone() {
        state.value = state.value.copy(onboarding = state.value.onboarding.copy(onboardingDone = true))
    }
    override suspend fun setOnboardingStep(step: Int) {
        state.value = state.value.copy(onboarding = state.value.onboarding.copy(lastCompletedStep = step))
    }
    override suspend fun replaceConfig(c: HydraConfig) { state.value = c }
}
