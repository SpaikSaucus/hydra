package com.personal.hydra.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.hydra.data.db.entity.DayLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(day: DayLogEntity): Long

    @Query("SELECT * FROM day_log WHERE day_key = :dayKey LIMIT 1")
    suspend fun getDay(dayKey: String): DayLogEntity?

    @Query("SELECT * FROM day_log WHERE day_key = :dayKey LIMIT 1")
    fun observeDay(dayKey: String): Flow<DayLogEntity?>

    @Query("UPDATE day_log SET total_ml = :total WHERE day_key = :dayKey")
    suspend fun setTotal(dayKey: String, total: Int)

    @Query("UPDATE day_log SET closed = 1 WHERE day_key = :dayKey")
    suspend fun close(dayKey: String)

    /** Atomically close every still-open day before today (one statement). */
    @Query("UPDATE day_log SET closed = 1 WHERE closed = 0 AND day_key < :todayKey")
    suspend fun closeDaysBefore(todayKey: String)

    @Query("SELECT * FROM day_log WHERE closed = 0 AND day_key < :todayKey")
    suspend fun openDaysBefore(todayKey: String): List<DayLogEntity>

    /** Recompute every day's cached total from live (non-deleted) intake rows. */
    @Query(
        "UPDATE day_log SET total_ml = (" +
            "SELECT COALESCE(SUM(amount_ml), 0) FROM intake_entry " +
            "WHERE intake_entry.day_key = day_log.day_key AND deleted_at IS NULL)",
    )
    suspend fun recomputeAllTotals()

    @Query("SELECT * FROM day_log ORDER BY day_key DESC")
    fun observeAllDays(): Flow<List<DayLogEntity>>

    @Query("SELECT * FROM day_log ORDER BY day_key ASC")
    suspend fun allForExport(): List<DayLogEntity>

    /**
     * In-place update of an existing day (matched by PK). MUST be @Update, NOT an
     * INSERT-OR-REPLACE: REPLACE deletes the old day_log row first, which cascades
     * (intake_entry FK ON DELETE CASCADE) and would wipe the day's logged intakes.
     */
    @Update
    suspend fun update(day: DayLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<DayLogEntity>)

    @Query("DELETE FROM day_log")
    suspend fun clear()
}
