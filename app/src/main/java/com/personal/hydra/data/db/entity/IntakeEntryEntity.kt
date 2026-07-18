package com.personal.hydra.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.hydra.domain.model.IntakeSource
import com.personal.hydra.domain.model.UnitSystem

/** One drink. Always stored in canonical ml. Soft-deleted via [deletedAt]. */
@Entity(
    tableName = "intake_entry",
    foreignKeys = [
        ForeignKey(
            entity = DayLogEntity::class,
            parentColumns = ["day_key"],
            childColumns = ["day_key"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("day_key"),
        Index("timestamp"),
    ],
)
data class IntakeEntryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "amount_ml") val amountMl: Int,
    @ColumnInfo(name = "source") val source: IntakeSource,
    @ColumnInfo(name = "entered_unit") val enteredUnit: UnitSystem = UnitSystem.METRIC,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)
