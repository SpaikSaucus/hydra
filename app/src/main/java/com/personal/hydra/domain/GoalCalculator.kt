package com.personal.hydra.domain

import com.personal.hydra.domain.model.DomainWarning
import com.personal.hydra.domain.model.GoalInput
import com.personal.hydra.domain.model.GoalResult
import com.personal.hydra.domain.model.Ranges
import com.personal.hydra.domain.model.WarningCode
import kotlin.math.roundToInt

/**
 * Daily goal = factor (ml/kg) x weight (kg), then +/- manual adjustment.
 * Heat mode is a hard override that fixes the factor at 40. All inputs are
 * clamped to valid ranges and any clamp is surfaced as a warning.
 *
 * Pure & deterministic — no Android, no clock, no locale.
 */
object GoalCalculator {

    fun calculate(input: GoalInput): GoalResult {
        val warnings = mutableListOf<DomainWarning>()

        val weight = input.weightKg.coerceIn(Ranges.WEIGHT_MIN, Ranges.WEIGHT_MAX)
        if (weight != input.weightKg) warnings += DomainWarning(WarningCode.WEIGHT_CLAMPED, weight)

        val factor: Int = if (input.heatMode) {
            Ranges.FACTOR_HEAT
        } else {
            input.factorMlKg.coerceIn(Ranges.FACTOR_MIN, Ranges.FACTOR_MAX).also {
                if (it != input.factorMlKg) warnings += DomainWarning(WarningCode.FACTOR_CLAMPED, it.toDouble())
            }
        }

        val baseGoal = (weight * factor).roundToInt()

        val pct = input.manualAdjustPct.coerceIn(Ranges.ADJ_MIN, Ranges.ADJ_MAX)
        if (pct != input.manualAdjustPct) warnings += DomainWarning(WarningCode.ADJUSTMENT_CLAMPED, pct.toDouble())

        val goal = (baseGoal * (1.0 + pct / 100.0)).roundToInt()

        return GoalResult(
            goalMl = goal,
            baseGoalMl = baseGoal,
            effectiveFactor = factor,
            warnings = warnings,
        )
    }
}
