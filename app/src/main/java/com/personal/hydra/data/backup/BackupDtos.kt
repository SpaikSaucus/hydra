package com.personal.hydra.data.backup

import com.personal.hydra.data.db.entity.DayLogEntity
import com.personal.hydra.data.db.entity.IntakeEntryEntity
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.IntakeSource
import com.personal.hydra.domain.model.UnitSystem
import kotlinx.serialization.Serializable

const val BACKUP_SCHEMA_VERSION = 1

/** Full manual JSON backup: config + day snapshots + intake entries. */
@Serializable
data class HydraBackup(
    val format: String = "hydra-backup",
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAt: Long,
    val config: HydraConfig,
    val days: List<DayLogDto>,
    val entries: List<IntakeDto>,
)

@Serializable
data class DayLogDto(
    val dayKey: String,
    val goalMl: Int,
    val baseGoalMl: Int,
    val weightKg: Float,
    val factorMlKg: Int,
    val manualAdjustPct: Int,
    val heatMode: Boolean,
    val inferredSeason: String,
    val wakeMinuteOfDay: Int,
    val cutoffMinuteOfDay: Int,
    val hourlyCapMl: Int,
    val zoneId: String,
    val totalMl: Int,
    val createdAt: Long,
    val closed: Boolean,
)

@Serializable
data class IntakeDto(
    val id: Long,
    val dayKey: String,
    val timestamp: Long,
    val amountMl: Int,
    val source: String,
    val enteredUnit: String,
    val deletedAt: Long? = null,
)

fun DayLogEntity.toDto() = DayLogDto(
    dayKey, goalMl, baseGoalMl, weightKg, factorMlKg, manualAdjustPct, heatMode, inferredSeason,
    wakeMinuteOfDay, cutoffMinuteOfDay, hourlyCapMl, zoneId, totalMl, createdAt, closed,
)

fun DayLogDto.toEntity() = DayLogEntity(
    dayKey = dayKey, goalMl = goalMl, baseGoalMl = baseGoalMl, weightKg = weightKg,
    factorMlKg = factorMlKg, manualAdjustPct = manualAdjustPct, heatMode = heatMode,
    inferredSeason = inferredSeason, wakeMinuteOfDay = wakeMinuteOfDay,
    cutoffMinuteOfDay = cutoffMinuteOfDay, hourlyCapMl = hourlyCapMl, zoneId = zoneId,
    totalMl = totalMl, createdAt = createdAt, closed = closed,
)

fun IntakeEntryEntity.toDto() = IntakeDto(
    id = id, dayKey = dayKey, timestamp = timestamp, amountMl = amountMl,
    source = source.name, enteredUnit = enteredUnit.name, deletedAt = deletedAt,
)

fun IntakeDto.toEntity() = IntakeEntryEntity(
    id = id, dayKey = dayKey, timestamp = timestamp, amountMl = amountMl,
    source = runCatching { IntakeSource.valueOf(source) }.getOrDefault(IntakeSource.MANUAL),
    enteredUnit = runCatching { UnitSystem.valueOf(enteredUnit) }.getOrDefault(UnitSystem.METRIC),
    deletedAt = deletedAt,
)
