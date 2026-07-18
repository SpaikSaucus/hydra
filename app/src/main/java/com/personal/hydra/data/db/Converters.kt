package com.personal.hydra.data.db

import androidx.room.TypeConverter
import com.personal.hydra.domain.model.IntakeSource
import com.personal.hydra.domain.model.UnitSystem

class Converters {
    @TypeConverter
    fun sourceToString(s: IntakeSource): String = s.name

    @TypeConverter
    fun stringToSource(s: String): IntakeSource =
        runCatching { IntakeSource.valueOf(s) }.getOrDefault(IntakeSource.MANUAL)

    @TypeConverter
    fun unitToString(u: UnitSystem): String = u.name

    @TypeConverter
    fun stringToUnit(s: String): UnitSystem =
        runCatching { UnitSystem.valueOf(s) }.getOrDefault(UnitSystem.METRIC)
}
