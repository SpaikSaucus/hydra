package com.personal.hydra.steps

import com.personal.hydra.domain.DateRange
import com.personal.hydra.domain.DistributionSummary
import com.personal.hydra.domain.HeatCell
import com.personal.hydra.domain.HistoryAnalytics
import com.personal.hydra.domain.IntakeDistribution
import com.personal.hydra.domain.RangeSummary
import com.personal.hydra.domain.TimedIntake
import com.personal.hydra.domain.WeekdayStat
import com.personal.hydra.domain.model.DayStat
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Steps for the two history charts: the picked date range and the hourly distribution. */
class ChartAnalyticsSteps {

    // ---------------------------- date range ---------------------------------

    private var history: List<DayStat> = emptyList()
    private lateinit var summary: RangeSummary
    private var listed: List<DayStat> = emptyList()

    @Given("a history of days")
    fun historyOfDays(table: DataTable) {
        history = table.asMaps().map { r ->
            DayStat(LocalDate.parse(r.getValue("date")), r.getValue("goal").toInt(), r.getValue("total").toInt())
        }
    }

    @When("the range {string} to {string} is summarised")
    fun summarise(from: String, to: String) {
        summary = HistoryAnalytics.summarize(history, DateRange.of(LocalDate.parse(from), LocalDate.parse(to)))
    }

    @When("the days in {string} to {string} are listed")
    fun listDays(from: String, to: String) {
        listed = HistoryAnalytics.daysIn(history, DateRange.of(LocalDate.parse(from), LocalDate.parse(to)))
    }

    @Then("the range covers {int} days")
    fun rangeCovers(n: Int) = assertEquals(n, summary.range.days)

    @Then("the range has {int} logged days")
    fun rangeLogged(n: Int) = assertEquals(n, summary.loggedDays)

    @Then("the range has {int} completed days")
    fun rangeCompleted(n: Int) = assertEquals(n, summary.completedDays)

    @Then("the range total is {int} ml")
    fun rangeTotal(ml: Int) = assertEquals(ml, summary.totalMl)

    @Then("the range daily average is {int} ml")
    fun rangeAverage(ml: Int) = assertEquals(ml, summary.dailyAverageMl)

    @Then("the range average completion is {int} percent")
    fun rangeCompletion(pct: Int) = assertEquals(pct, (summary.averagePercent * 100).roundToInt())

    @Then("the listed days are {string}")
    fun listedDays(csv: String) = assertEquals(csv, listed.joinToString(",") { it.date.toString() })

    // -------------------- weekday / rolling average / heat-map ----------------

    private var weekday: List<WeekdayStat> = emptyList()
    private var rolling: List<Double?> = emptyList()
    private var heatmap: List<List<HeatCell>> = emptyList()

    @When("the weekday pattern is computed")
    fun computeWeekday() {
        weekday = HistoryAnalytics.byWeekday(history)
    }

    @Then("the weekday pattern has {int} entries")
    fun weekdayEntries(n: Int) = assertEquals(n, weekday.size)

    @Then("the weekday average for {string} is {int} percent")
    fun weekdayAverage(dow: String, pct: Int) = assertEquals(
        pct,
        (weekday.first { it.dayOfWeek == DayOfWeek.valueOf(dow) }.averagePercent * 100).roundToInt(),
    )

    @Then("the weekday {string} has {int} days")
    fun weekdayDays(dow: String, n: Int) =
        assertEquals(n, weekday.first { it.dayOfWeek == DayOfWeek.valueOf(dow) }.days)

    @Then("the weakest weekday is {string}")
    fun weakestWeekday(dow: String) = assertEquals(
        DayOfWeek.valueOf(dow),
        weekday.filter { it.days > 0 }.minByOrNull { it.averagePercent }?.dayOfWeek,
    )

    /** Expands the history into one slot per calendar day, so gaps stay visible. */
    @When("the rolling average is computed")
    fun computeRolling() {
        rolling = HistoryAnalytics.rollingAverage(calendarSlots())
    }

    /** What the chart actually draws, which also decides whether to draw at all. */
    @When("the rolling average line is computed")
    fun computeRollingLine() {
        rolling = HistoryAnalytics.rollingAverageFor(calendarSlots())
    }

    private fun calendarSlots(): List<DayStat?> {
        val sorted = history.sortedBy { it.date }
        val byDate = sorted.associateBy { it.date }
        val slots = mutableListOf<DayStat?>()
        var d = sorted.first().date
        while (!d.isAfter(sorted.last().date)) {
            slots += byDate[d]
            d = d.plusDays(1)
        }
        return slots
    }

    @Then("the rolling average has {int} points")
    fun rollingSize(n: Int) = assertEquals(n, rolling.size)

    @Then("the rolling average has no points")
    fun rollingSilent() = assertTrue("expected no plotted points, got $rolling", rolling.all { it == null })

    @Then("the rolling average at index {int} is empty")
    fun rollingEmptyAt(i: Int) = assertNull(rolling.getOrNull(i))

    @Then("the rolling average at index {int} is {int} percent")
    fun rollingAt(i: Int, pct: Int) = assertEquals(pct, (rolling[i]!! * 100).roundToInt())

    @When("the heatmap is computed for {string}")
    fun computeHeatmap(endDate: String) {
        heatmap = HistoryAnalytics.heatmap(history, LocalDate.parse(endDate))
    }

    @Then("the heatmap has {int} columns")
    fun heatmapColumns(n: Int) = assertEquals(n, heatmap.size)

    @Then("every heatmap column has {int} cells")
    fun heatmapRows(n: Int) = assertTrue(heatmap.all { it.size == n })

    @Then("the heatmap starts on {string}")
    fun heatmapStart(d: String) = assertEquals(LocalDate.parse(d), heatmap.first().first().date)

    @Then("the heatmap ends on {string}")
    fun heatmapEnd(d: String) = assertEquals(LocalDate.parse(d), heatmap.last().last().date)

    @Then("the heatmap cell for {string} is {int} percent")
    fun heatmapCell(d: String, pct: Int) =
        assertEquals(pct, ((cell(d)?.percent ?: 0.0) * 100).roundToInt())

    @Then("the heatmap cell for {string} is empty")
    fun heatmapCellEmpty(d: String) = assertNull(cell(d)?.percent)

    private fun cell(d: String): HeatCell? =
        heatmap.flatten().firstOrNull { it.date == LocalDate.parse(d) }

    private var weekly: List<Double?> = emptyList()

    @When("the weekly averages are computed for {string}")
    fun computeWeekly(today: String) {
        weekly = HistoryAnalytics.weeklyAverages(heatmap, LocalDate.parse(today))
    }

    @Then("there are {int} weekly averages")
    fun weeklyCount(n: Int) = assertEquals(n, weekly.size)

    @Then("the weekly average for week {int} is {int} percent")
    fun weeklyAt(index: Int, pct: Int) = assertEquals(pct, (weekly[index]!! * 100).roundToInt())

    @Then("the weekly average for week {int} is empty")
    fun weeklyEmpty(index: Int) = assertNull(weekly[index])

    // ------------------------ hourly distribution ----------------------------

    private lateinit var zone: ZoneId
    private lateinit var wake: LocalTime
    private var windowMinutes = 780
    private val intakes = mutableListOf<TimedIntake>()
    private lateinit var distribution: DistributionSummary

    @Given("a distribution in zone {string} waking at {string} with a {int} minute window")
    fun distributionSetup(zoneId: String, wakeAt: String, window: Int) {
        zone = ZoneId.of(zoneId)
        wake = LocalTime.parse(wakeAt)
        windowMinutes = window
        intakes.clear()
    }

    @Given("an intake of {int} ml at {string}")
    fun anIntake(ml: Int, localDateTime: String) {
        val millis = LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli()
        intakes += TimedIntake(millis, ml)
    }

    @When("the distribution is computed")
    fun computeDistribution() {
        distribution = IntakeDistribution.of(intakes, zone, wake, windowMinutes)
    }

    @Then("hour {int} holds {int} ml")
    fun hourHolds(hour: Int, ml: Int) =
        assertEquals(ml, distribution.buckets.first { it.hour == hour }.totalMl)

    @Then("the distribution total is {int} ml")
    fun distributionTotal(ml: Int) = assertEquals(ml, distribution.totalMl)

    @Then("the distribution has {int} buckets")
    fun bucketCount(n: Int) = assertEquals(n, distribution.buckets.size)

    @Then("the peak window starts at hour {int}")
    fun peakStart(h: Int) = assertEquals(h, distribution.peakStartHour)

    @Then("the peak window share is {int} percent")
    fun peakShare(pct: Int) = assertEquals(pct, distribution.peakSharePct)

    @Then("the observed morning share is {int} percent")
    fun morningShare(pct: Int) = assertEquals(pct, distribution.morningPct)

    @Then("the observed afternoon share is {int} percent")
    fun afternoonShare(pct: Int) = assertEquals(pct, distribution.afternoonPct)

    @Then("the distribution is empty")
    fun distributionEmpty() = assertTrue(distribution.isEmpty)
}
