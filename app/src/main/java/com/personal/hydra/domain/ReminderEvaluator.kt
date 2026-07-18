package com.personal.hydra.domain

import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.Ranges
import com.personal.hydra.domain.model.ReminderDecision
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure decision for "should we remind right now?". Given the config, what's been
 * consumed today, minutes since the last intake and the current local time, it
 * tells the Worker whether to post, plus the suggested next amount (capped to
 * the hourly limit). No Android, no clock, no DB.
 */
object ReminderEvaluator {

    private const val BEHIND_TOLERANCE_ML = 150

    fun evaluate(
        config: HydraConfig,
        consumedMl: Int,
        minutesSinceLastIntake: Int?,
        now: LocalTime,
        today: LocalDate,
    ): ReminderDecision {
        val s = config.settings
        val goal = Hydration.goal(config, today).goalMl

        if (PauseManager.isPaused(config.pauses, today)) {
            return done(ReminderDecision.Reason.PAUSED, consumedMl, goal)
        }

        val sleepFromWake = ScheduleGenerator.minutesFromWake(s.wakeTime, s.sleepTime).let { if (it == 0) 1440 else it }
        val cutoffFromWake = (sleepFromWake - s.nightCutoffBeforeSleepMin).coerceIn(0, 1440)
        val nowFromWake = ScheduleGenerator.minutesFromWake(s.wakeTime, now)
        val remaining = (goal - consumedMl).coerceAtLeast(0)

        if (consumedMl >= goal) {
            return done(ReminderDecision.Reason.ALREADY_DONE, consumedMl, goal)
        }
        val inWindow = nowFromWake in 0 until cutoffFromWake
        if (!inWindow) {
            return done(ReminderDecision.Reason.NIGHT_CUTOFF, consumedMl, goal)
        }

        val totalMin = cutoffFromWake.coerceAtLeast(1)
        val elapsed = nowFromWake.coerceIn(0, totalMin)
        val morningShare = s.morningSharePct.coerceIn(Ranges.MORNING_SHARE_MIN, Ranges.MORNING_SHARE_MAX)
        val ideal = idealAt(goal, elapsed, totalMin, morningShare)
        val isBehind = consumedMl < ideal - BEHIND_TOLERANCE_ML

        val minLeft = (cutoffFromWake - nowFromWake).coerceAtLeast(1)
        val capLeft = (s.maxIntakePerHourMl.toLong() * minLeft / 60L).toInt()
        val overflow = remaining > capLeft

        val slotsLeft = ceil(minLeft / s.reminderIntervalMin.toDouble()).toInt().coerceAtLeast(1)
        val perSlot = ceil(remaining / slotsLeft.toDouble()).toInt()
        // Never suggest more than what's left: with <50 ml to go, suggest exactly
        // the remainder instead of rounding up to the 50 ml floor.
        val capped = min(perSlot, s.maxIntakePerHourMl)
        val nextTarget = if (remaining < 50) remaining else min(roundTo50(capped).coerceAtLeast(50), remaining)

        val due = minutesSinceLastIntake == null || minutesSinceLastIntake >= s.reminderIntervalMin
        val shouldNotify = due || isBehind
        val reason = when {
            isBehind -> ReminderDecision.Reason.BEHIND
            due -> ReminderDecision.Reason.DUE
            else -> ReminderDecision.Reason.NOT_YET
        }
        return ReminderDecision(
            shouldNotify = shouldNotify,
            reason = reason,
            consumedMl = consumedMl,
            goalMl = goal,
            remainingMl = remaining,
            nextTargetMl = nextTarget,
            isBehind = isBehind,
            overflowWarning = overflow,
        )
    }

    /**
     * Piecewise-linear pace: the first half of the wake->cutoff window targets
     * [morningSharePct]% of the goal, the second half the complement, so the
     * accumulated target is continuous and reaches exactly `goal` at cutoff.
     */
    private fun idealAt(goal: Int, elapsed: Int, totalMin: Int, morningSharePct: Int): Int {
        val half = totalMin / 2
        if (half <= 0) return (goal.toLong() * elapsed / totalMin.coerceAtLeast(1)).toInt()
        val morningMl = goal.toLong() * morningSharePct / 100L
        return if (elapsed <= half) {
            (morningMl * elapsed / half).toInt()
        } else {
            val afternoonMl = goal.toLong() - morningMl
            val rest = (totalMin - half).coerceAtLeast(1)
            (morningMl + afternoonMl * (elapsed - half) / rest).toInt()
        }
    }

    private fun done(reason: ReminderDecision.Reason, consumed: Int, goal: Int) = ReminderDecision(
        shouldNotify = false,
        reason = reason,
        consumedMl = consumed,
        goalMl = goal,
        remainingMl = (goal - consumed).coerceAtLeast(0),
        nextTargetMl = 0,
        isBehind = false,
        overflowWarning = false,
    )

    private fun roundTo50(v: Int): Int = ((v / 50.0).roundToInt()) * 50
}
