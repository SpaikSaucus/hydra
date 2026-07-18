@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.personal.hydra.data

import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.data.db.dao.DayLogDao
import com.personal.hydra.data.db.dao.IntakeDao
import com.personal.hydra.data.db.entity.DayLogEntity
import com.personal.hydra.data.db.entity.IntakeEntryEntity
import com.personal.hydra.data.settings.SettingsRepository
import com.personal.hydra.domain.Hydration
import com.personal.hydra.domain.SeasonInference
import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.IntakeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class HydrationRepositoryImpl(
    private val dayLogDao: DayLogDao,
    private val intakeDao: IntakeDao,
    private val settings: SettingsRepository,
    /** Runs the block atomically (Room transaction in prod; pass-through in tests). */
    private val transaction: suspend (suspend () -> Unit) -> Unit,
    private val dayKeyResolver: DayKeyResolver = DayKeyResolver(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : HydrationRepository {

    override fun observeToday(): Flow<DayLogEntity?> =
        settings.config.flatMapLatest { c ->
            val key = dayKeyResolver.todayKey(c.settings.wakeTime, clock.zone)
            flow {
                ensureDayOpen(key, c)
                emitAll(dayLogDao.observeDay(key))
            }
        }

    override fun observeTodayEntries(): Flow<List<IntakeEntryEntity>> =
        settings.config.flatMapLatest { c ->
            intakeDao.observeEntriesOfDay(dayKeyResolver.todayKey(c.settings.wakeTime, clock.zone))
        }

    override fun observeHistory(): Flow<List<DayLogEntity>> = dayLogDao.observeAllDays()

    override suspend fun ensureToday(): DayLogEntity {
        val c = settings.snapshot()
        val key = dayKeyResolver.todayKey(c.settings.wakeTime, clock.zone)
        dayLogDao.closeDaysBefore(key)
        return ensureDayOpen(key, c)
    }

    /**
     * Opens today's day with a fresh snapshot, OR refreshes the snapshot of the
     * still-open current day when the profile changed (goal "today and onward").
     * Closed days are never touched (immutable history).
     */
    private suspend fun ensureDayOpen(dayKey: String, c: HydraConfig): DayLogEntity {
        val today = LocalDate.now(clock)
        val existing = dayLogDao.getDay(dayKey)
        if (existing != null && existing.closed) return existing

        val goal = Hydration.goal(c, today)
        val season = SeasonInference.infer(c.settings.countryCode, today).season
        val advanced = c.settings.configMode == ConfigMode.ADVANCED
        val s = c.settings
        val cutoffMin = ((s.sleepTimeMin - s.nightCutoffBeforeSleepMin) % 1440 + 1440) % 1440

        if (existing == null) {
            val day = DayLogEntity(
                dayKey = dayKey,
                goalMl = goal.goalMl,
                baseGoalMl = goal.baseGoalMl,
                weightKg = c.profile.weightKg.toFloat(),
                factorMlKg = goal.effectiveFactor,
                manualAdjustPct = if (advanced) c.profile.manualAdjustPct else 0,
                heatMode = Hydration.effectiveHeatMode(c, today),
                inferredSeason = season.name,
                wakeMinuteOfDay = s.wakeTimeMin,
                cutoffMinuteOfDay = cutoffMin,
                hourlyCapMl = s.maxIntakePerHourMl,
                zoneId = clock.zone.id,
                totalMl = 0,
                createdAt = Instant.now(clock).toEpochMilli(),
                closed = false,
            )
            dayLogDao.insertIfAbsent(day)
            return dayLogDao.getDay(dayKey)!!
        }

        // Open day: re-derive snapshot, keep totalMl/createdAt; upsert only if changed.
        val desired = existing.copy(
            goalMl = goal.goalMl,
            baseGoalMl = goal.baseGoalMl,
            weightKg = c.profile.weightKg.toFloat(),
            factorMlKg = goal.effectiveFactor,
            manualAdjustPct = if (advanced) c.profile.manualAdjustPct else 0,
            heatMode = Hydration.effectiveHeatMode(c, today),
            inferredSeason = season.name,
            wakeMinuteOfDay = s.wakeTimeMin,
            cutoffMinuteOfDay = cutoffMin,
            hourlyCapMl = s.maxIntakePerHourMl,
        )
        if (desired != existing) dayLogDao.update(desired)
        return desired
    }

    override suspend fun addIntake(amountMl: Int, source: IntakeSource, at: Long): Long {
        val c = settings.snapshot()
        val key = dayKeyResolver.dayKeyFor(at, c.settings.wakeTime, clock.zone)
        var id = 0L
        transaction {
            // Open the day inside the transaction so the row + entry + total commit
            // (or roll back) together.
            ensureDayOpen(key, c)
            id = intakeDao.insert(
                IntakeEntryEntity(
                    dayKey = key,
                    timestamp = at,
                    amountMl = amountMl,
                    source = source,
                    enteredUnit = c.settings.unitSystem,
                ),
            )
            dayLogDao.setTotal(key, intakeDao.sumOfDay(key))
        }
        return id
    }

    override suspend fun undoIntake(id: Long, dayKey: String) {
        transaction {
            intakeDao.softDelete(id, Instant.now(clock).toEpochMilli())
            dayLogDao.setTotal(dayKey, intakeDao.sumOfDay(dayKey))
        }
    }

    override suspend fun restoreIntake(id: Long, dayKey: String) {
        transaction {
            intakeDao.restore(id)
            dayLogDao.setTotal(dayKey, intakeDao.sumOfDay(dayKey))
        }
    }

    override suspend fun consumedToday(): Int {
        val c = settings.snapshot()
        return intakeDao.sumOfDay(dayKeyResolver.todayKey(c.settings.wakeTime, clock.zone))
    }

    override suspend fun intakeInLastHour(at: Long): Int = intakeDao.sumSince(at - 3_600_000L)

    override suspend fun minutesSinceLastIntake(at: Long): Int? {
        val c = settings.snapshot()
        val key = dayKeyResolver.todayKey(c.settings.wakeTime, clock.zone)
        val last = intakeDao.lastTimestampOfDay(key) ?: return null
        return ((at - last) / 60_000L).toInt()
    }
}
