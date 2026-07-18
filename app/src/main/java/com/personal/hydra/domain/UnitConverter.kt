package com.personal.hydra.domain

import com.personal.hydra.domain.model.UnitSystem
import kotlin.math.roundToInt

/**
 * Metric <-> imperial conversions. Internally everything is canonical ml (Int)
 * and kg (Double); imperial is presentation/input only. Pure & deterministic.
 */
object UnitConverter {
    const val LB_PER_KG = 2.2046226218
    const val ML_PER_FLOZ = 29.5735295625 // US fluid ounce

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG
    fun lbToKg(lb: Double): Double = lb / LB_PER_KG
    fun mlToFloz(ml: Int): Double = ml / ML_PER_FLOZ
    fun flozToMl(floz: Double): Int = (floz * ML_PER_FLOZ).roundToInt()
    fun mlToLiters(ml: Int): Double = round2(ml / 1000.0)

    /** Edit input -> canonical kg (1-decimal rounding in the user's unit). */
    fun normalizeWeightToKg(value: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.METRIC -> round1(value)
        UnitSystem.IMPERIAL -> lbToKg(round1(value))
    }

    /** Canonical kg -> value to display in the requested unit (1 decimal). */
    fun displayWeight(kg: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.METRIC -> round1(kg)
        UnitSystem.IMPERIAL -> round1(kgToLb(kg))
    }

    /** A volume already in ml -> display value (fl oz integer; ml integer). */
    fun displayVolume(ml: Int, system: UnitSystem): Double = when (system) {
        UnitSystem.METRIC -> ml.toDouble()
        UnitSystem.IMPERIAL -> mlToFloz(ml).roundToInt().toDouble()
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
    private fun round2(v: Double) = (v * 100).roundToInt() / 100.0
}
