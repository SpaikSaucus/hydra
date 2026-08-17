package com.personal.hydra.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A point of a cumulative-intake curve, on a minute-of-day (0..1440) axis. */
data class PacePoint(val minuteOfDay: Int, val ml: Int)

/**
 * Today's plan vs. reality: the [ideal] pacing curve the reminders aim at and
 * the [actual] cumulative curve of what was really logged, both on a clock axis
 * so they can be drawn on top of each other.
 */
data class DayPace(
    val goalMl: Int,
    val wakeMinute: Int,
    val cutoffMinute: Int,
    val ideal: List<PacePoint>,
    val actual: List<PacePoint>,
    /**
     * One point per REAL drink, at the running total it produced. [actual] can't
     * be used for this: it also carries a synthetic origin and a flat tail out to
     * "now", which would render as two drinks that never happened.
     */
    val drinks: List<PacePoint>,
    val nowMinute: Int,
    val nowIdealMl: Int,
    val nowActualMl: Int,
) {
    /** Positive = ahead of the plan, negative = behind it. */
    val deltaMl: Int get() = nowActualMl - nowIdealMl
}

/**
 * The single source of truth for the morning/afternoon pacing target. Both the
 * reminder decision and the Home chart read [idealAt], so the notification can
 * never disagree with the curve the user is looking at. Pure; time injected.
 */
object PaceCurve {

    /**
     * Piecewise-linear pace: the first half of the wake->cutoff window targets
     * [morningSharePct]% of the goal, the second half the complement, so the
     * accumulated target is continuous and reaches exactly `goal` at cutoff.
     */
    fun idealAt(goalMl: Int, elapsedMin: Int, windowMinutes: Int, morningSharePct: Int): Int {
        val total = windowMinutes.coerceAtLeast(1)
        val elapsed = elapsedMin.coerceIn(0, total)
        val half = total / 2
        if (half <= 0) return (goalMl.toLong() * elapsed / total).toInt()
        val morningMl = goalMl.toLong() * morningSharePct / 100L
        return if (elapsed <= half) {
            (morningMl * elapsed / half).toInt()
        } else {
            val afternoonMl = goalMl.toLong() - morningMl
            val rest = (total - half).coerceAtLeast(1)
            (morningMl + afternoonMl * (elapsed - half) / rest).toInt()
        }
    }

    /**
     * Same target, addressed by clock time instead of elapsed minutes. Before
     * waking up the target is 0 (the day hasn't started pacing yet); past the
     * night cutoff it stays at the full goal.
     */
    fun idealAtClock(
        goalMl: Int,
        minuteOfDay: Int,
        wakeMinute: Int,
        windowMinutes: Int,
        morningSharePct: Int,
    ): Int {
        if (minuteOfDay <= wakeMinute) return 0
        return idealAt(goalMl, minuteOfDay - wakeMinute, windowMinutes, morningSharePct)
    }

    fun of(
        goalMl: Int,
        wakeMinute: Int,
        windowMinutes: Int,
        morningSharePct: Int,
        intakes: List<TimedIntake>,
        zone: ZoneId,
        now: LocalTime,
        samples: Int = 48,
    ): DayPace {
        val cutoff = (wakeMinute + windowMinutes).coerceAtMost(1440)
        val nowMinute = now.hour * 60 + now.minute

        val ideal = (0..samples).map { i ->
            val m = (1440L * i / samples).toInt()
            PacePoint(m, idealAtClock(goalMl, m, wakeMinute, windowMinutes, morningSharePct))
        }

        // Step curve: one point per logged drink, plus the origin and a flat
        // segment up to "now" so the line always reaches the current time.
        var acc = 0
        val steps = mutableListOf(PacePoint(0, 0))
        val drinks = mutableListOf<PacePoint>()
        intakes
            .map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalTime().let { t -> t.hour * 60 + t.minute } to it.amountMl }
            .sortedBy { it.first }
            .forEach { (minute, ml) ->
                acc += ml
                val point = PacePoint(minute.coerceIn(0, 1440), acc)
                steps += point
                drinks += point
            }
        if (steps.last().minuteOfDay < nowMinute) steps += PacePoint(nowMinute, acc)

        return DayPace(
            goalMl = goalMl,
            wakeMinute = wakeMinute,
            cutoffMinute = cutoff,
            ideal = ideal,
            actual = steps,
            drinks = drinks,
            nowMinute = nowMinute,
            nowIdealMl = idealAtClock(goalMl, nowMinute, wakeMinute, windowMinutes, morningSharePct),
            nowActualMl = acc,
        )
    }
}
