package com.personal.hydra.domain

import com.personal.hydra.domain.model.DayStat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Inclusive day range picked on the 30-day chart. Always normalised (from <= to). */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()

    operator fun contains(d: LocalDate): Boolean = !d.isBefore(from) && !d.isAfter(to)

    companion object {
        fun of(a: LocalDate, b: LocalDate): DateRange = if (a.isAfter(b)) DateRange(b, a) else DateRange(a, b)
    }
}

/**
 * Aggregates of a picked range. [dailyAverageMl] divides by the range's CALENDAR
 * days (not by the days that have a record), so a gap honestly drags the average
 * down instead of hiding.
 */
data class RangeSummary(
    val range: DateRange,
    val loggedDays: Int,
    val completedDays: Int,
    val totalMl: Int,
    val dailyAverageMl: Int,
    val averagePercent: Double,
)

/** One weekday's average across the whole history (Monday..Sunday, ISO order). */
data class WeekdayStat(
    val dayOfWeek: DayOfWeek,
    val days: Int,
    val completedDays: Int,
    val averagePercent: Double,
)

/** One cell of the calendar heat-map; [percent] is null when the day has no record. */
data class HeatCell(val date: LocalDate, val percent: Double?, val completed: Boolean)

/** Pure aggregation over the day history for the history screen. No clock, no Android. */
object HistoryAnalytics {

    fun daysIn(days: List<DayStat>, range: DateRange): List<DayStat> = days.filter { it.date in range }

    fun summarize(days: List<DayStat>, range: DateRange): RangeSummary {
        val inRange = daysIn(days, range)
        val total = inRange.sumOf { it.totalMl }
        // Completion is capped at 100% per day so one huge day can't mask several empty ones.
        val avgPct = if (inRange.isEmpty()) 0.0 else inRange.map { minOf(it.percent, 1.0) }.average()
        return RangeSummary(
            range = range,
            loggedDays = inRange.size,
            completedDays = inRange.count { it.completed },
            totalMl = total,
            dailyAverageMl = if (range.days > 0) total / range.days else 0,
            averagePercent = avgPct,
        )
    }

    /**
     * Average completion per day of the week — the "I always fail on Saturdays"
     * chart. Always 7 entries in ISO order (Monday first) so the chart never
     * reshuffles; weekdays with no history report 0 days and 0%.
     */
    fun byWeekday(days: List<DayStat>): List<WeekdayStat> {
        val grouped = days.groupBy { it.date.dayOfWeek }
        return DayOfWeek.entries.map { dow ->
            val d = grouped[dow].orEmpty()
            WeekdayStat(
                dayOfWeek = dow,
                days = d.size,
                completedDays = d.count { it.completed },
                averagePercent = if (d.isEmpty()) 0.0 else d.map { minOf(it.percent, 1.0) }.average(),
            )
        }
    }

    /**
     * Trailing mean of the completion percentage over [window] CALENDAR slots,
     * aligned one-to-one with [slots] (one entry per day, null where that day
     * has no record).
     *
     * A slot only gets a value when the whole window behind it is present: a
     * "7-day average" computed from 2 days is not a 7-day average, and drawing
     * it made the line start at the floor and rocket up, which is exactly the
     * shape a user reads as "this chart is broken".
     */
    /** Slots the trailing mean spans by default — a "7-day average". */
    const val ROLLING_WINDOW = 7

    /**
     * The line as the chart should draw it for a window of [slots] calendar days.
     * A window no longer than [window] can hold at most ONE point, and one point
     * is not a line: it renders as a lone dot that reads as a rendering glitch.
     */
    fun rollingAverageFor(slots: List<DayStat?>, window: Int = ROLLING_WINDOW): List<Double?> =
        if (slots.size <= window) emptyList() else rollingAverage(slots, window)

    fun rollingAverage(slots: List<DayStat?>, window: Int = ROLLING_WINDOW): List<Double?> {
        val w = window.coerceAtLeast(1)
        return slots.indices.map { i ->
            if (i < w - 1) return@map null
            val win = slots.subList(i - w + 1, i + 1)
            if (win.any { it == null }) null
            else win.map { minOf(it!!.percent, 1.0) }.average()
        }
    }

    /**
     * Average completion of each heat-map column, for the bar rendering of the
     * same 12 weeks.
     *
     * A day with no record counts as ZERO, over the days of that week that have
     * already happened: these bars are a trend of ADHERENCE, and a week you
     * forgot to log is not a perfect week. (That differs from [byWeekday], which
     * answers "when I do log, which weekday is weakest" and so averages only the
     * days present.) A week entirely in the future is null and draws nothing.
     */
    fun weeklyAverages(weeks: List<List<HeatCell>>, today: LocalDate): List<Double?> =
        weeks.map { column ->
            val elapsed = column.filter { !it.date.isAfter(today) }
            // A week with nothing logged is a GAP, not a 0% week — the same rule
            // the grid applies to a single day, so it draws no bar rather than a
            // flat one that would read as "I tried and failed".
            if (elapsed.none { it.percent != null }) null
            else elapsed.map { it.percent ?: 0.0 }.average()
        }

    /**
     * Calendar heat-map of the last [weeks] weeks, as columns of 7 cells running
     * Monday..Sunday. The last column is the week containing [endDate]; days
     * after it (and days with no record) carry a null percent.
     */
    fun heatmap(days: List<DayStat>, endDate: LocalDate, weeks: Int = 12): List<List<HeatCell>> {
        val byDate = days.associateBy { it.date }
        val lastMonday = endDate.minusDays((endDate.dayOfWeek.value - 1).toLong())
        val firstMonday = lastMonday.minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w ->
            val monday = firstMonday.plusWeeks(w.toLong())
            (0 until 7).map { d ->
                val date = monday.plusDays(d.toLong())
                val stat = byDate[date].takeIf { !date.isAfter(endDate) }
                HeatCell(
                    date = date,
                    percent = stat?.let { minOf(it.percent, 1.0) },
                    completed = stat?.completed == true,
                )
            }
        }
    }
}
