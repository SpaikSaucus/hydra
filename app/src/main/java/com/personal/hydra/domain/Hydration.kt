package com.personal.hydra.domain

import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.GoalInput
import com.personal.hydra.domain.model.GoalResult
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.Ranges
import java.time.LocalDate

/**
 * Single source of truth that maps the stored config to the EFFECTIVE goal,
 * honouring the simple/advanced policy:
 *  - SIMPLE: pure formula — factor 33 (or 40 when the inferred season is summer),
 *    no manual adjustment. Heat mode is automatic from the season.
 *  - ADVANCED: uses the user's raw factor, manual heat toggle and ±% adjustment.
 *
 * Centralising this guarantees Home, the day snapshot, settings preview and the
 * reminder evaluator all agree. Pure & deterministic (date is injected).
 */
object Hydration {

    fun effectiveHeatMode(config: HydraConfig, today: LocalDate): Boolean =
        if (config.settings.configMode == ConfigMode.ADVANCED) {
            config.profile.heatMode
        } else {
            SeasonInference.infer(config.settings.countryCode, today).suggestsHeatMode
        }

    fun goalInput(config: HydraConfig, today: LocalDate): GoalInput {
        val advanced = config.settings.configMode == ConfigMode.ADVANCED
        return if (advanced) {
            GoalInput(
                weightKg = config.profile.weightKg,
                factorMlKg = config.profile.factorMlKg,
                heatMode = config.profile.heatMode,
                manualAdjustPct = config.profile.manualAdjustPct,
            )
        } else {
            GoalInput(
                weightKg = config.profile.weightKg,
                factorMlKg = Ranges.FACTOR_NORMAL,
                heatMode = effectiveHeatMode(config, today),
                manualAdjustPct = 0,
            )
        }
    }

    fun goal(config: HydraConfig, today: LocalDate): GoalResult =
        GoalCalculator.calculate(goalInput(config, today))
}
