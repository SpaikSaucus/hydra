package com.personal.hydra.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per hydration day. Holds an IMMUTABLE snapshot of the calculation for
 * that day (goal/weight/factor/window/cap) so changing the profile later never
 * rewrites past history. `totalMl` is a denormalized cache kept in a
 * transaction; the source of truth is SUM(intake_entry.amount_ml).
 */
@Entity(tableName = "day_log")
data class DayLogEntity(
    @PrimaryKey @ColumnInfo(name = "day_key") val dayKey: String,

    @ColumnInfo(name = "goal_ml") val goalMl: Int,
    @ColumnInfo(name = "base_goal_ml") val baseGoalMl: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Float,
    @ColumnInfo(name = "factor_ml_kg") val factorMlKg: Int,
    @ColumnInfo(name = "manual_adj_pct") val manualAdjustPct: Int,
    @ColumnInfo(name = "heat_mode") val heatMode: Boolean,
    @ColumnInfo(name = "inferred_season") val inferredSeason: String,

    @ColumnInfo(name = "wake_min") val wakeMinuteOfDay: Int,
    @ColumnInfo(name = "cutoff_min") val cutoffMinuteOfDay: Int,
    @ColumnInfo(name = "hourly_cap_ml") val hourlyCapMl: Int,
    @ColumnInfo(name = "zone_id") val zoneId: String,

    @ColumnInfo(name = "total_ml") val totalMl: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "closed") val closed: Boolean = false,
)
