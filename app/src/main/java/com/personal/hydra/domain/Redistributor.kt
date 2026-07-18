package com.personal.hydra.domain

import com.personal.hydra.domain.model.DomainWarning
import com.personal.hydra.domain.model.Schedule
import com.personal.hydra.domain.model.ScheduleParams
import com.personal.hydra.domain.model.WarningCode
import java.time.LocalTime

data class RedistributeInput(
    val base: ScheduleParams,
    val now: LocalTime,
    val consumedMl: Int,
    val extendMaxMinutes: Int = 90,
)

/**
 * Recomputes the plan for the time REMAINING until the cutoff, spreading
 * (goal - consumed) without exceeding the hourly cap. If it does not fit, tries
 * a soft extension up to sleep time; flags aggressive/overflow situations.
 * Pure & deterministic.
 */
object Redistributor {

    fun redistribute(inp: RedistributeInput): Schedule {
        val p = inp.base
        val remaining = (p.goalMl - inp.consumedMl).coerceAtLeast(0)
        if (remaining == 0) return Schedule(emptyList(), 0, emptyList())

        val sleepFromWake = ScheduleGenerator.minutesFromWake(p.wakeTime, p.sleepTime)
            .let { if (it == 0) 1440 else it }
        val cutoffMin = (sleepFromWake - p.nightCutoffMinutes).coerceIn(0, 1440)
        val nowFromWake = ScheduleGenerator.minutesFromWake(p.wakeTime, inp.now)

        if (nowFromWake >= cutoffMin) {
            return tryExtended(p, inp.now, remaining, sleepFromWake, inp.extendMaxMinutes, alreadyPastCutoff = true)
        }

        val windowMin = cutoffMin - nowFromWake
        val virtual = p.copy(
            wakeTime = inp.now,
            goalMl = remaining,
            sleepTime = inp.now.plusMinutes(windowMin.toLong()),
            nightCutoffMinutes = 0,
        )
        var sched = ScheduleGenerator.generate(virtual)
        if (sched.warnings.any { it.code == WarningCode.GOAL_DOES_NOT_FIT_WINDOW }) {
            sched = tryExtended(p, inp.now, remaining, sleepFromWake, inp.extendMaxMinutes, alreadyPastCutoff = false)
        }
        return sched.copy(warnings = sched.warnings + aggressiveFlag(sched, p.maxPerHourMl))
    }

    private fun tryExtended(
        p: ScheduleParams,
        now: LocalTime,
        remaining: Int,
        sleepFromWake: Int,
        extendMax: Int,
        alreadyPastCutoff: Boolean,
    ): Schedule {
        val nowFromWake = ScheduleGenerator.minutesFromWake(p.wakeTime, now)
        val baseCutoff = (sleepFromWake - p.nightCutoffMinutes).coerceIn(0, 1440)
        val extendedCutoff = (baseCutoff + extendMax).coerceAtMost(sleepFromWake)
        val windowMin = (extendedCutoff - nowFromWake).coerceAtLeast(0)
        if (windowMin <= 0) {
            return Schedule(
                emptyList(),
                0,
                listOf(DomainWarning(WarningCode.GOAL_DOES_NOT_FIT_WINDOW, remaining.toDouble())),
            )
        }
        val virtual = p.copy(
            wakeTime = now,
            goalMl = remaining,
            sleepTime = now.plusMinutes(windowMin.toLong()),
            nightCutoffMinutes = 0,
        )
        val sched = ScheduleGenerator.generate(virtual)
        val extra = mutableListOf<DomainWarning>()
        if (extendedCutoff > baseCutoff || alreadyPastCutoff) {
            extra += DomainWarning(WarningCode.SCHEDULE_EXTENDED_PAST_CUTOFF)
        }
        return sched.copy(warnings = sched.warnings + extra + aggressiveFlag(sched, p.maxPerHourMl))
    }

    private fun aggressiveFlag(s: Schedule, maxPerHour: Int): List<DomainWarning> =
        if (s.intakes.any { it.amountMl >= 0.9 * maxPerHour }) {
            listOf(DomainWarning(WarningCode.BEHIND_SCHEDULE_AGGRESSIVE))
        } else {
            emptyList()
        }
}
