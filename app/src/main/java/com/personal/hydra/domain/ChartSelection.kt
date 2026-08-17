package com.personal.hydra.domain

import java.time.LocalDate

/** Period feeding the two period-scoped history charts. */
enum class ChartPeriod { WEEK, MONTH, QUARTER, SELECTION }

/**
 * What the history screen currently has picked on the 30-day chart.
 *
 * Invariant: [period] is only [ChartPeriod.SELECTION] while [range] is set —
 * otherwise the chip would stay lit while the window silently fell back to the
 * last 30 days, which reads as "the button does nothing".
 */
data class ChartSelection(
    /** First tapped day, waiting for the tap (or drag) that closes the range. */
    val anchor: LocalDate? = null,
    val range: DateRange? = null,
    val period: ChartPeriod = ChartPeriod.MONTH,
)

/** Pure state machine for picking a range on the chart. No clock, no Android. */
object ChartSelectionPolicy {

    /** First tap sets the start, the second closes the range, a third starts over. */
    fun tap(state: ChartSelection, date: LocalDate): ChartSelection {
        val start = state.anchor
        return if (start == null) {
            // Starting over drops the old range, so the period has to let go of it
            // too — otherwise the "Selection" chip stays lit while the window
            // silently falls back to 30 days, which reads as "it does nothing".
            ChartSelection(anchor = date, range = null, period = withoutSelection(state.period))
        } else {
            ChartSelection(anchor = null, range = DateRange.of(start, date), period = ChartPeriod.SELECTION)
        }
    }

    /**
     * Dragging across the chart picks the whole span in one gesture. It always
     * replaces whatever was selected, so no previous state is carried over.
     */
    fun drag(from: LocalDate, to: LocalDate): ChartSelection =
        ChartSelection(anchor = null, range = DateRange.of(from, to), period = ChartPeriod.SELECTION)

    fun clear(state: ChartSelection): ChartSelection =
        ChartSelection(anchor = null, range = null, period = withoutSelection(state.period))

    /**
     * Abandons a pick in progress. Lands in exactly the same place as clearing a
     * finished one — the first tap already dropped the old range, so there is
     * nothing to restore and only one rule to remember: starting a pick loses
     * whatever was picked before, however the pick ends.
     */
    fun cancel(state: ChartSelection): ChartSelection = clear(state)

    /**
     * Choosing a plain period is a decision to stop looking at the pick, so the
     * pick is DISCARDED (along with any half-finished one). Keeping it stored left
     * the "Selection" chip lit and could still paint the band over the new window,
     * which reads as "I changed the period but the old range is stuck".
     */
    fun setPeriod(state: ChartSelection, period: ChartPeriod): ChartSelection = when {
        period != ChartPeriod.SELECTION -> ChartSelection(anchor = null, range = null, period = period)
        // Returning to a pick that no longer exists must be a no-op, not a period
        // pointing at a null range (see the ChartSelection invariant).
        state.range == null -> state
        else -> state.copy(anchor = null, period = ChartPeriod.SELECTION)
    }

    /** Concrete days a [ChartPeriod] resolves to, relative to [today]. */
    fun window(state: ChartSelection, today: LocalDate): DateRange = when (state.period) {
        ChartPeriod.WEEK -> DateRange(today.minusDays(6), today)
        ChartPeriod.MONTH -> DateRange(today.minusDays(29), today)
        ChartPeriod.QUARTER -> DateRange(today.minusDays(89), today)
        ChartPeriod.SELECTION -> state.range ?: DateRange(today.minusDays(29), today)
    }

    /**
     * One slot per calendar day of [window], so a day with no record stays an
     * empty track instead of being squeezed out — the same rule the fixed 30-day
     * chart had, now that the chart follows the period.
     */
    fun slots(window: DateRange): List<LocalDate> =
        (0 until window.days).map { window.from.plusDays(it.toLong()) }

    private fun withoutSelection(period: ChartPeriod) =
        if (period == ChartPeriod.SELECTION) ChartPeriod.MONTH else period
}
