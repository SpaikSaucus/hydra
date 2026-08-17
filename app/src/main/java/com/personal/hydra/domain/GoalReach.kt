package com.personal.hydra.domain

import com.personal.hydra.domain.model.DayStat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The minute of the day a goal was crossed; null when the day never reached it. */
data class GoalReach(val date: LocalDate, val minuteOfDay: Int?)

data class GoalReachSummary(
    val points: List<GoalReach>,
    val reachedDays: Int,
    val totalDays: Int,
    /** Typical completion minute (median of the days that reached the goal). */
    val medianMinute: Int?,
    val earliestMinute: Int?,
    val latestMinute: Int?,
) {
    val isEmpty: Boolean get() = reachedDays == 0
}

/**
 * "At what time do I actually finish my water?" — the metric that matters for
 * sleep: finishing at 22:00 means the same daily total lands right before bed.
 * Pure; the zone is injected because a timestamp only becomes an hour inside one.
 */
object GoalReachAnalytics {

    fun of(days: List<DayStat>, intakes: List<TimedIntake>, zone: ZoneId): GoalReachSummary {
        val byDate = intakes.groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }

        val points = days.sortedBy { it.date }.map { day ->
            var acc = 0
            var reachedAt: Int? = null
            if (day.goalMl > 0) {
                for (i in byDate[day.date].orEmpty().sortedBy { it.timestampMillis }) {
                    acc += i.amountMl
                    if (acc >= day.goalMl) {
                        val t = Instant.ofEpochMilli(i.timestampMillis).atZone(zone).toLocalTime()
                        reachedAt = t.hour * 60 + t.minute
                        break
                    }
                }
            }
            GoalReach(day.date, reachedAt)
        }

        val reached = points.mapNotNull { it.minuteOfDay }.sorted()
        return GoalReachSummary(
            points = points,
            reachedDays = reached.size,
            totalDays = points.size,
            medianMinute = reached.getOrNull(reached.size / 2),
            earliestMinute = reached.firstOrNull(),
            latestMinute = reached.lastOrNull(),
        )
    }
}
