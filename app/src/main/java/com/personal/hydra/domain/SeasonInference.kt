package com.personal.hydra.domain

import com.personal.hydra.domain.model.DomainWarning
import com.personal.hydra.domain.model.Hemisphere
import com.personal.hydra.domain.model.Season
import com.personal.hydra.domain.model.SeasonInfo
import com.personal.hydra.domain.model.WarningCode
import java.time.LocalDate

/**
 * OFFLINE season inference: locale country -> hemisphere -> season by date.
 *
 * LIMITATION (documented intentionally): with no internet nor an ambient
 * temperature sensor, real weather/temperature CANNOT be known — only the
 * SEASON is inferred. The suggested "heat mode" is heuristic and always
 * overridable by the user. Pure & deterministic.
 */
object SeasonInference {

    /** Countries predominantly in the SOUTHERN hemisphere (ISO-3166 alpha-2). */
    val SOUTHERN_COUNTRIES: Set<String> = setOf(
        // South America
        "AR", "CL", "UY", "PY", "BO", "PE", "BR",
        // Southern Africa
        "ZA", "NA", "BW", "ZW", "MZ", "ZM", "MW", "AO", "LS", "SZ", "MG",
        // Oceania
        "AU", "NZ", "PG", "FJ", "SB", "VU", "NC", "PF", "WS", "TO", "TV", "NR", "KI",
        // Other / mostly-southern
        "TL", "ID",
    )

    fun hemisphereFor(countryCode: String?): Hemisphere {
        val cc = countryCode?.trim()?.uppercase()
        return if (cc != null && cc in SOUTHERN_COUNTRIES) Hemisphere.SOUTH else Hemisphere.NORTH
    }

    /** Meteorological season (not astronomical) for robustness/simplicity. */
    fun seasonFor(date: LocalDate, hemisphere: Hemisphere): Season {
        val northern = when (date.monthValue) {
            12, 1, 2 -> Season.WINTER
            3, 4, 5 -> Season.SPRING
            6, 7, 8 -> Season.SUMMER
            else -> Season.AUTUMN // 9, 10, 11
        }
        return if (hemisphere == Hemisphere.NORTH) northern else invert(northern)
    }

    private fun invert(s: Season): Season = when (s) {
        Season.WINTER -> Season.SUMMER
        Season.SPRING -> Season.AUTUMN
        Season.SUMMER -> Season.WINTER
        Season.AUTUMN -> Season.SPRING
    }

    fun infer(countryCode: String?, date: LocalDate): SeasonInfo {
        val hemi = hemisphereFor(countryCode)
        val season = seasonFor(date, hemi)
        return SeasonInfo(
            countryCode = countryCode?.trim()?.uppercase() ?: "",
            hemisphere = hemi,
            season = season,
            suggestsHeatMode = season == Season.SUMMER,
        )
    }

    fun suggestsHeatMode(season: Season): Boolean = season == Season.SUMMER

    /**
     * Discrepancy warning between the inferred season and the user's heat-mode choice:
     *  - summer + heat OFF -> warn (you may need more water)
     *  - winter + heat ON  -> info (goal will rise; fine if you exercise / hot environment)
     */
    fun heatModeWarning(season: Season, userHeatModeEnabled: Boolean): DomainWarning? = when {
        season == Season.SUMMER && !userHeatModeEnabled -> DomainWarning(WarningCode.HEAT_MODE_DISABLED_IN_SUMMER)
        season == Season.WINTER && userHeatModeEnabled -> DomainWarning(WarningCode.HEAT_MODE_ENABLED_IN_WINTER)
        else -> null
    }
}
