package com.personal.hydra.domain.model

import java.time.LocalDate

/** One day's outcome. A day is "completed" only at 100% of its goal (strict). */
data class DayStat(
    val date: LocalDate,
    val goalMl: Int,
    val totalMl: Int,
) {
    val completed: Boolean get() = goalMl > 0 && totalMl >= goalMl
    val percent: Double get() = if (goalMl > 0) totalMl.toDouble() / goalMl else 0.0
}

data class HydrationStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val daysCompleted: Int,
    val totalDays: Int,
    val averagePercent: Double,
)

/**
 * Unlockable milestones. DAYS = total completed days; STREAK = best run length.
 * Tier drives the badge colour (bronze/silver/gold); kind drives the icon.
 */
enum class Achievement(val kind: Kind, val threshold: Int, val tier: Tier) {
    FIRST_DAY(Kind.DAYS, 1, Tier.BRONZE),
    DAYS_7(Kind.DAYS, 7, Tier.BRONZE),
    DAYS_30(Kind.DAYS, 30, Tier.SILVER),
    DAYS_100(Kind.DAYS, 100, Tier.GOLD),
    DAYS_365(Kind.DAYS, 365, Tier.GOLD),
    STREAK_3(Kind.STREAK, 3, Tier.BRONZE),
    PERFECT_WEEK(Kind.STREAK, 7, Tier.BRONZE),
    STREAK_14(Kind.STREAK, 14, Tier.SILVER),
    STREAK_30(Kind.STREAK, 30, Tier.SILVER),
    STREAK_100(Kind.STREAK, 100, Tier.GOLD),
    STREAK_365(Kind.STREAK, 365, Tier.GOLD),
    ;

    enum class Kind { DAYS, STREAK }
    enum class Tier { BRONZE, SILVER, GOLD }
}
