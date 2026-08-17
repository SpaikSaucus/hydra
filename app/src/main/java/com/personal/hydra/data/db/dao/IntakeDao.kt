package com.personal.hydra.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personal.hydra.data.db.entity.IntakeEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeDao {

    @Insert
    suspend fun insert(entry: IntakeEntryEntity): Long

    @Query("UPDATE intake_entry SET deleted_at = :ts WHERE id = :id")
    suspend fun softDelete(id: Long, ts: Long)

    @Query(
        "SELECT * FROM intake_entry WHERE day_key = :dayKey AND deleted_at IS NULL ORDER BY timestamp ASC",
    )
    fun observeEntriesOfDay(dayKey: String): Flow<List<IntakeEntryEntity>>

    /** Inclusive day-key range (ISO dates sort chronologically), oldest first. */
    @Query(
        "SELECT * FROM intake_entry WHERE deleted_at IS NULL AND day_key BETWEEN :fromKey AND :toKey " +
            "ORDER BY timestamp ASC",
    )
    fun observeEntriesBetween(fromKey: String, toKey: String): Flow<List<IntakeEntryEntity>>

    @Query("SELECT COALESCE(SUM(amount_ml), 0) FROM intake_entry WHERE day_key = :dayKey AND deleted_at IS NULL")
    suspend fun sumOfDay(dayKey: String): Int

    @Query(
        "SELECT COALESCE(SUM(amount_ml), 0) FROM intake_entry " +
            "WHERE deleted_at IS NULL AND timestamp >= :sinceMillis",
    )
    suspend fun sumSince(sinceMillis: Long): Int

    @Query("SELECT MAX(timestamp) FROM intake_entry WHERE day_key = :dayKey AND deleted_at IS NULL")
    suspend fun lastTimestampOfDay(dayKey: String): Long?

    @Query("SELECT * FROM intake_entry ORDER BY timestamp ASC")
    suspend fun allForExport(): List<IntakeEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<IntakeEntryEntity>)

    @Query("DELETE FROM intake_entry")
    suspend fun clear()
}
