package com.personal.hydra.data

import com.personal.hydra.data.db.entity.DayLogEntity
import com.personal.hydra.data.db.entity.IntakeEntryEntity
import com.personal.hydra.domain.model.IntakeSource
import kotlinx.coroutines.flow.Flow

interface HydrationRepository {
    /** Today's day snapshot (reactive), resolved by the wake-hour day key. */
    fun observeToday(): Flow<DayLogEntity?>
    fun observeTodayEntries(): Flow<List<IntakeEntryEntity>>
    fun observeHistory(): Flow<List<DayLogEntity>>

    /** Opens today's day with a fresh snapshot if needed; closes stale days. */
    suspend fun ensureToday(): DayLogEntity

    suspend fun addIntake(amountMl: Int, source: IntakeSource, at: Long = System.currentTimeMillis()): Long
    suspend fun undoIntake(id: Long, dayKey: String)
    suspend fun restoreIntake(id: Long, dayKey: String)

    suspend fun consumedToday(): Int
    /** Rolling sum over the last hour, for the ~1 L/h kidney cap. */
    suspend fun intakeInLastHour(at: Long = System.currentTimeMillis()): Int
    suspend fun minutesSinceLastIntake(at: Long = System.currentTimeMillis()): Int?
}
