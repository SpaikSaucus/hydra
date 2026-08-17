package com.personal.hydra.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** One logged drink, reduced to what the distribution needs. */
data class TimedIntake(val timestampMillis: Long, val amountMl: Int)

/** Millilitres logged in a given hour of the day, plus its share of the total. */
data class HourBucket(val hour: Int, val totalMl: Int, val share: Double)

/**
 * Where the day's water actually lands. [buckets] always has 24 entries (hour
 * 0..23) so the chart never has to deal with holes.
 *
 * [morningPct]/[afternoonPct] split the intake at the midpoint of the
 * wake -> night-cutoff window — the very same split the reminder pacing targets
 * — so the user can compare what they DID against the balance they CONFIGURED.
 */
data class DistributionSummary(
    val buckets: List<HourBucket>,
    val totalMl: Int,
    /** First hour of the busiest [peakWindowHours]-hour block; null when there's no data. */
    val peakStartHour: Int?,
    val peakWindowHours: Int,
    val peakSharePct: Int,
    val morningPct: Int,
    val afternoonPct: Int,
) {
    val isEmpty: Boolean get() = totalMl <= 0
    val peakEndHour: Int? get() = peakStartHour?.let { (it + peakWindowHours) % 24 }
}

/**
 * Pure hourly aggregation. The zone, the wake time and the length of the intake
 * window are all injected — the domain never reads a clock or a locale.
 */
object IntakeDistribution {

    fun of(
        intakes: List<TimedIntake>,
        zone: ZoneId,
        wake: LocalTime,
        windowMinutes: Int,
        peakWindowHours: Int = 3,
    ): DistributionSummary {
        val w = peakWindowHours.coerceIn(1, 24)
        val perHour = IntArray(24)
        var morning = 0L
        var total = 0L
        val half = (windowMinutes.coerceIn(1, 1440)) / 2

        intakes.forEach { i ->
            val t = Instant.ofEpochMilli(i.timestampMillis).atZone(zone).toLocalTime()
            perHour[t.hour] += i.amountMl
            total += i.amountMl
            if (ScheduleGenerator.minutesFromWake(wake, t) < half) morning += i.amountMl
        }

        if (total <= 0L) {
            return DistributionSummary(
                buckets = List(24) { HourBucket(it, 0, 0.0) },
                totalMl = 0,
                peakStartHour = null,
                peakWindowHours = w,
                peakSharePct = 0,
                morningPct = 0,
                afternoonPct = 0,
            )
        }

        val buckets = List(24) { HourBucket(it, perHour[it], perHour[it] / total.toDouble()) }

        // Busiest block of w consecutive hours (no midnight wrap: a block that
        // straddles midnight would read as nonsense on the chart).
        var bestStart = 0
        var bestSum = -1L
        for (start in 0..(24 - w)) {
            var sum = 0L
            for (h in start until start + w) sum += perHour[h]
            if (sum > bestSum) {
                bestSum = sum
                bestStart = start
            }
        }

        val morningPct = ((morning * 100 + total / 2) / total).toInt().coerceIn(0, 100)
        return DistributionSummary(
            buckets = buckets,
            totalMl = total.toInt(),
            peakStartHour = bestStart,
            peakWindowHours = w,
            peakSharePct = ((bestSum * 100 + total / 2) / total).toInt().coerceIn(0, 100),
            morningPct = morningPct,
            afternoonPct = 100 - morningPct,
        )
    }
}
