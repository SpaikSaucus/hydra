package com.personal.hydra.domain

import com.personal.hydra.domain.model.DomainWarning
import com.personal.hydra.domain.model.Intake
import com.personal.hydra.domain.model.Schedule
import com.personal.hydra.domain.model.ScheduleParams
import com.personal.hydra.domain.model.WarningCode
import java.time.Duration
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Builds the day's intake plan: from wake time to the night cutoff
 * (sleep - nightCutoffMinutes), distributing the goal morning-to-evening with a
 * decreasing ramp, never exceeding the hourly cap, summing exactly to the goal.
 * Pure & deterministic.
 */
object ScheduleGenerator {

    /** Minutes elapsed from wake to t, handling midnight wrap (mod 1440). */
    fun minutesFromWake(wake: LocalTime, t: LocalTime): Int {
        val raw = Duration.between(wake, t).toMinutes()
        return ((raw % 1440 + 1440) % 1440).toInt()
    }

    fun generate(p: ScheduleParams): Schedule {
        val warnings = mutableListOf<DomainWarning>()

        val sleepFromWake = minutesFromWake(p.wakeTime, p.sleepTime).let { if (it == 0) 1440 else it }
        val cutoffMin = (sleepFromWake - p.nightCutoffMinutes).coerceIn(0, 1440)

        if (cutoffMin <= 0 || p.goalMl <= 0) {
            return Schedule(emptyList(), 0, listOf(DomainWarning(WarningCode.GOAL_DOES_NOT_FIT_WINDOW)))
        }

        val numSlots = ceil(cutoffMin.toDouble() / p.slotMinutes).toInt()
        val slotDur = IntArray(numSlots) { i -> min(p.slotMinutes, cutoffMin - i * p.slotMinutes) }
        val cap = DoubleArray(numSlots) { i -> p.maxPerHourMl * (slotDur[i] / 60.0) }
        val capTotal = cap.sum()

        if (p.goalMl > capTotal) {
            warnings += DomainWarning(WarningCode.GOAL_DOES_NOT_FIT_WINDOW, p.goalMl - capTotal)
        }
        val target = min(p.goalMl.toDouble(), capTotal)

        // Decreasing linear ramp: first slot weight 1.0, last slot endWeight.
        val w = DoubleArray(numSlots) { i ->
            if (numSlots == 1) 1.0 else 1.0 - (1.0 - p.endWeight) * (i.toDouble() / (numSlots - 1))
        }
        val wSum = w.sum()

        // Initial assignment + cap, then spill the overflow into slots with headroom.
        val assigned = DoubleArray(numSlots)
        var overflow = 0.0
        for (i in 0 until numSlots) {
            val raw = target * w[i] / wSum
            assigned[i] = min(raw, cap[i])
            overflow += raw - assigned[i]
        }
        var guard = 0
        while (overflow > 0.5 && guard++ < 50) {
            val headroom = DoubleArray(numSlots) { cap[it] - assigned[it] }
            val hSum = headroom.sum()
            if (hSum <= 0.5) break
            val toSpread = overflow
            overflow = 0.0
            for (i in 0 until numSlots) {
                if (headroom[i] <= 0) continue
                val want = toSpread * headroom[i] / hSum
                val add = min(headroom[i], want)
                assigned[i] += add
                overflow += want - add
            }
        }

        val intAmounts = roundPreservingSum(assigned, target.roundToInt(), p.roundToMl, cap)

        val intakes = ArrayList<Intake>(numSlots)
        for (i in 0 until numSlots) {
            if (intAmounts[i] <= 0) continue
            val offset = i * p.slotMinutes
            val t = p.wakeTime.plusMinutes(offset.toLong())
            val nextDay = (p.wakeTime.toSecondOfDay() + offset * 60) >= 86400
            intakes += Intake(t, nextDay, intAmounts[i])
        }
        return Schedule(intakes, intAmounts.sum(), warnings)
    }

    /**
     * Rounds to multiples of [step] but guarantees the total equals [targetSum]
     * exactly (the sub-step remainder lands on a slot with headroom), never
     * exceeding caps. Requires targetSum <= sum(cap), which the caller ensures.
     */
    private fun roundPreservingSum(raw: DoubleArray, targetSum: Int, step: Int, cap: DoubleArray): IntArray {
        val n = raw.size
        if (n == 0) return IntArray(0)

        val base = IntArray(n) { (floor(raw[it] / step) * step).toInt() }
        var remaining = targetSum - base.sum()

        // Hand out `step` increments by largest fractional remainder, respecting caps.
        val order = (0 until n).sortedByDescending { raw[it] - base[it] }
        var idx = 0
        var spins = 0
        while (remaining >= step && spins++ < n * 4000) {
            val s = order[idx % n]; idx++
            if (base[s] + step <= cap[s] + 0.5) {
                base[s] += step
                remaining -= step
            }
        }
        // Sub-step leftover (e.g. +1 ml): spread across slots with real headroom,
        // NEVER exceeding a cap. If nothing fits, targetSum > sum(cap) — which the
        // caller already clamped and flagged with GOAL_DOES_NOT_FIT_WINDOW — so the
        // tiny remainder is dropped rather than violating the hourly cap.
        for (i in 0 until n) {
            if (remaining <= 0) break
            // +0.5 tolerance mirrors the step-loop's cap check and absorbs float error
            // in `cap` (e.g. 650*42/60 = 454.9999… should allow 455), so the exact-sum
            // contract holds without a real overflow of the integer cap.
            val room = floor(cap[i] + 0.5 - base[i]).toInt()
            if (room > 0) {
                val add = min(room, remaining)
                base[i] += add
                remaining -= add
            }
        }
        return base
    }
}
