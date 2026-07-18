package com.personal.hydra.domain

import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.HydrationStats
import com.personal.hydra.domain.model.PausePeriod
import java.time.LocalDate

/**
 * Streak and aggregate stats over the day history. A day counts only at 100%
 * of its goal. Today, while still in progress and not yet complete, does NOT
 * break the current streak (it just isn't counted yet). Paused days are
 * NEUTRAL: an incomplete paused day neither breaks a streak nor drags the
 * average down, but a paused day whose goal was still met counts normally.
 * Pure & deterministic.
 */
object StreakCalculator {

    fun stats(days: List<DayStat>, today: LocalDate, pauses: List<PausePeriod> = emptyList()): HydrationStats {
        val completed = days.filter { it.completed }.map { it.date }.toSet()
        val counted = days.filter { it.completed || !PauseManager.isPaused(pauses, it.date) }
        val avg = if (counted.isEmpty()) 0.0 else counted.map { minOf(it.percent, 1.0) }.average()
        return HydrationStats(
            currentStreak = currentStreak(completed, today, pauses),
            bestStreak = bestStreak(completed, pauses),
            daysCompleted = completed.size,
            totalDays = counted.size,
            averagePercent = avg,
        )
    }

    fun currentStreak(completed: Set<LocalDate>, today: LocalDate, pauses: List<PausePeriod> = emptyList()): Int {
        // If today isn't completed yet, start counting from yesterday (today still in progress).
        var d = if (today in completed) today else today.minusDays(1)
        var streak = 0
        while (true) {
            when {
                d in completed -> streak++
                PauseManager.isPaused(pauses, d) -> Unit // neutral: skip without breaking
                else -> return streak
            }
            d = d.minusDays(1)
        }
    }

    fun bestStreak(completed: Set<LocalDate>, pauses: List<PausePeriod> = emptyList()): Int {
        if (completed.isEmpty()) return 0
        val sorted = completed.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            val consecutive = sorted[i] == sorted[i - 1].plusDays(1)
            if (consecutive || allPausedBetween(sorted[i - 1], sorted[i], pauses)) {
                run++
                best = maxOf(best, run)
            } else {
                run = 1
            }
        }
        return best
    }

    /** True when every day strictly between [prev] and [next] was paused. */
    private fun allPausedBetween(prev: LocalDate, next: LocalDate, pauses: List<PausePeriod>): Boolean {
        if (pauses.isEmpty()) return false
        var d = prev.plusDays(1)
        while (d < next) {
            if (!PauseManager.isPaused(pauses, d)) return false
            d = d.plusDays(1)
        }
        return true
    }
}
