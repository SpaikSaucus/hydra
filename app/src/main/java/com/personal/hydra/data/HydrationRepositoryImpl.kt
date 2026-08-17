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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    /**
     * The current day key, re-emitted just after every midnight.
     *
     * Without this, a "today" flow only re-keyed when the CONFIG happened to
     * change: leaving the app open across midnight left Home bound to yesterday's
     * row while new drinks were already being filed under the new day — a ring
     * that refused to move. Costs one suspended coroutine, and only while someone
     * collects (the ViewModels use `WhileSubscribed`), so it is not polling.
     */
    private fun dayKeys(): Flow<String> = flow {
        while (true) {
            emit(dayKeyResolver.todayKey(clock.zone))
            delay(dayKeyResolver.millisUntilNextDay(clock.zone))
        }
    }

    override fun observeToday(): Flow<DayLogEntity?> =
        combine(settings.config, dayKeys()) { c, key -> c to key }
            .flatMapLatest { (c, key) ->
                flow {
                    // Rolling over here (not only in HomeViewModel.init) is what makes
                    // yesterday freeze without the app being restarted. Idempotent, and
                    // one UPDATE that normally matches no rows.
                    dayLogDao.closeDaysBefore(key)
                    ensureDayOpen(key, c)
                    emitAll(dayLogDao.observeDay(key))
                }
            }

    // No config dependency: since v1.4 the day key derives from the calendar alone,
    // so the only thing that can re-key this flow is the clock.
    override fun observeTodayEntries(): Flow<List<IntakeEntryEntity>> =
        dayKeys().flatMapLatest { key -> intakeDao.observeEntriesOfDay(key) }

    override fun observeHistory(): Flow<List<DayLogEntity>> = dayLogDao.observeAllDays()

    override fun observeEntriesBetween(fromKey: String, toKey: String): Flow<List<IntakeEntryEntity>> =
        intakeDao.observeEntriesBetween(fromKey, toKey)

    override suspend fun ensureToday(): DayLogEntity {
        val c = settings.snapshot()
        val key = dayKeyResolver.todayKey(clock.zone)
        dayLogDao.closeDaysBefore(key)
        return ensureDayOpen(key, c)
    }

    /**
     * Opens today's day with a fresh snapshot, OR refreshes the snapshot of the
     * still-open current day when the profile changed (goal "today and onward").
     * Closed days are never touched (immutable history).
     */
    private suspend fun ensureDayOpen(dayKey: String, c: HydraConfig): DayLogEntity {
        // The day's OWN date drives goal/season, so a late-night entry that opens
        // yesterday's row is snapshotted with yesterday's inputs.
        val today = LocalDate.parse(dayKey)
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
                morningSharePct = s.morningSharePct,
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
            // Re-derived like the goal: the OPEN day follows the current balance
            // ("today and onward"), and freezes when the day closes.
            morningSharePct = s.morningSharePct,
        )
        if (desired != existing) dayLogDao.update(desired)
        return desired
    }

    override suspend fun addIntake(amountMl: Int, source: IntakeSource, at: Long): Long {
        val c = settings.snapshot()
        val key = dayKeyResolver.dayKeyFor(at, clock.zone)
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

    override suspend fun consumedToday(): Int = intakeDao.sumOfDay(dayKeyResolver.todayKey(clock.zone))

    override suspend fun intakeInLastHour(at: Long): Int = intakeDao.sumSince(at - 3_600_000L)

    override suspend fun minutesSinceLastIntake(at: Long): Int? {
        val key = dayKeyResolver.todayKey(clock.zone)
        val last = intakeDao.lastTimestampOfDay(key) ?: return null
        return ((at - last) / 60_000L).toInt()
    }
}
