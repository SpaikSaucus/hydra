package com.personal.hydra.screenshots

import com.personal.hydra.domain.TimedIntake
import com.personal.hydra.domain.model.DayStat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Deterministic 12-week history used to render the documentation screenshots.
 *
 * Everything is generated from a fixed seed and a fixed date/zone — no clock, no
 * `Math.random` — so Roborazzi produces byte-identical PNGs on every machine.
 * The shape is deliberately realistic (weekends weaker, a couple of bad days,
 * morning-heavy drinking) so the charts show something worth looking at.
 */
internal object Sample {

    val ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    val TODAY: LocalDate = LocalDate.of(2026, 6, 14)
    const val GOAL_ML = 2541

    /** Today so far, matching [todayIntakes]. */
    const val TODAY_CONSUMED_ML = 1750

    /**
     * 84 days (12 weeks) ending on [TODAY]; the last one is still in progress.
     * The sample user is weaker at weekends and slowly improving over the three
     * months, which is what makes the weekday chart and the 7-day average line
     * show something instead of a flat wall.
     */
    fun days(count: Int = 84): List<DayStat> {
        val rng = Lcg(20260614L)
        return (0 until count).map { i ->
            val date = TODAY.minusDays((count - 1 - i).toLong())
            if (date == TODAY) {
                DayStat(date, GOAL_ML, TODAY_CONSUMED_ML)
            } else {
                val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                val base = if (weekend) 90 else 106
                val trend = -8 + 16 * i / (count - 1) // improving over the 12 weeks
                var pct = base + rng.int(26) - 11 + trend
                if (rng.int(20) == 0) pct = 34 + rng.int(22) // the occasional bad day
                DayStat(date, GOAL_ML, GOAL_ML * pct.coerceIn(18, 125) / 100)
            }
        }
    }

    /**
     * Individual drinks for the last [dayCount] days, spread over the waking
     * hours with a morning-heavy profile so the hourly chart has a real shape.
     * Uses largest-remainder on 50 ml units, so the day's total is honoured
     * without dumping the rounding leftovers into a single fake evening drink.
     */
    fun intakes(days: List<DayStat>, dayCount: Int = 30): List<TimedIntake> {
        val rng = Lcg(777L)
        val hours = HOUR_WEIGHTS.entries.sortedBy { it.key }
        val totalWeight = hours.sumOf { it.value }
        val out = mutableListOf<TimedIntake>()

        days.takeLast(dayCount).forEach { day ->
            if (day.date == TODAY) {
                out += todayIntakes()
                return@forEach
            }
            val units = day.totalMl / 50
            val raw = hours.map { it.value.toDouble() * units / totalWeight }
            val perHour = raw.map { it.toInt() }.toMutableList()
            var left = units - perHour.sum()
            raw.mapIndexed { i, v -> i to (v - v.toInt()) }
                .sortedByDescending { it.second }
                .forEach { (i, _) -> if (left > 0) { perHour[i]++; left-- } }

            hours.forEachIndexed { i, (hour, _) ->
                val ml = perHour[i] * 50
                if (ml > 0) out += at(day.date, hour, 5 + rng.int(50), ml)
            }
        }
        return out
    }

    /** Today's partial log — 1 750 ml across the morning and early afternoon. */
    fun todayIntakes(): List<TimedIntake> = listOf(
        at(TODAY, 7, 20, 250),
        at(TODAY, 8, 40, 500),
        at(TODAY, 10, 15, 250),
        at(TODAY, 12, 40, 500),
        at(TODAY, 15, 5, 250),
    )

    /** The clock time the Home screenshot is taken at. */
    val NOW: LocalTime = LocalTime.of(15, 30)

    private val HOUR_WEIGHTS = mapOf(
        7 to 9, 8 to 12, 9 to 8, 10 to 13, 11 to 11, 12 to 7, 13 to 5,
        14 to 7, 15 to 8, 16 to 6, 17 to 5, 18 to 4, 19 to 3, 20 to 2,
    )

    private fun at(date: LocalDate, hour: Int, minute: Int, ml: Int) = TimedIntake(
        date.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli(),
        ml,
    )

    /** Minimal 64-bit LCG — deterministic and dependency-free. */
    private class Lcg(seed: Long) {
        private var state = seed
        fun int(bound: Int): Int {
            state = state * 6364136223846793005L + 1442695040888963407L
            return ((state ushr 33).toInt() and 0x7fffffff) % bound
        }
    }
}
