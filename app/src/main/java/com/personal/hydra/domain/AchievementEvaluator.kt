package com.personal.hydra.domain

import com.personal.hydra.domain.model.Achievement
import com.personal.hydra.domain.model.HydrationStats

object AchievementEvaluator {

    fun unlocked(stats: HydrationStats): Set<Achievement> =
        Achievement.entries.filter { isUnlocked(it, stats) }.toSet()

    fun isUnlocked(a: Achievement, stats: HydrationStats): Boolean = when (a.kind) {
        Achievement.Kind.DAYS -> stats.daysCompleted >= a.threshold
        Achievement.Kind.STREAK -> stats.bestStreak >= a.threshold
    }
}
