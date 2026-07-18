package com.personal.hydra.util

import com.personal.hydra.domain.UnitConverter
import com.personal.hydra.domain.model.UnitSystem
import java.util.Locale
import kotlin.math.roundToInt

/** Shared volume/weight formatting (used by notifications and UI). */
object VolumeFormat {

    fun volume(ml: Int, system: UnitSystem, locale: Locale = Locale.getDefault()): String = when (system) {
        UnitSystem.METRIC ->
            if (ml >= 1000) String.format(locale, "%.2f L", ml / 1000.0) else "$ml ml"
        UnitSystem.IMPERIAL ->
            "${UnitConverter.mlToFloz(ml).roundToInt()} fl oz"
    }

    fun weight(kg: Double, system: UnitSystem, locale: Locale = Locale.getDefault()): String = when (system) {
        UnitSystem.METRIC -> String.format(locale, "%.1f kg", kg)
        UnitSystem.IMPERIAL -> String.format(locale, "%.1f lb", UnitConverter.kgToLb(kg))
    }

    /** Whole-number weight (no decimals) for the stepper. */
    fun weightWhole(kg: Double, system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> "${kg.roundToInt()} kg"
        UnitSystem.IMPERIAL -> "${UnitConverter.kgToLb(kg).roundToInt()} lb"
    }
}
