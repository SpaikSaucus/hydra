package com.personal.hydra.steps

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.personal.hydra.domain.ChartPeriod
import com.personal.hydra.domain.ChartSelection
import com.personal.hydra.domain.ChartSelectionPolicy
import com.personal.hydra.domain.DateRange
import com.personal.hydra.ui.components.ChartTint
import com.personal.hydra.ui.components.HeatmapLayout
import com.personal.hydra.ui.components.HeatmapPalette
import com.personal.hydra.ui.theme.DarkPrimary
import com.personal.hydra.ui.theme.DarkSurfaceContainer
import com.personal.hydra.ui.theme.LightPrimary
import com.personal.hydra.ui.theme.LightSurfaceContainer
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Steps for the range-picker state machine and the heat-map sizing math. */
class ChartUxSteps {

    private var selection = ChartSelection()

    @Given("an empty chart selection")
    fun emptySelection() {
        selection = ChartSelection()
    }

    @Given("a chart selection of {string} to {string}")
    fun pickedSelection(from: String, to: String) {
        selection = ChartSelection(
            range = DateRange(LocalDate.parse(from), LocalDate.parse(to)),
            period = ChartPeriod.SELECTION,
        )
    }

    @When("the chart bar {string} is tapped")
    fun tapBar(date: String) {
        selection = ChartSelectionPolicy.tap(selection, LocalDate.parse(date))
    }

    @When("the chart is dragged from {string} to {string}")
    fun dragBars(from: String, to: String) {
        selection = ChartSelectionPolicy.drag(LocalDate.parse(from), LocalDate.parse(to))
    }

    @When("the chart selection is cleared")
    fun clearSelection() {
        selection = ChartSelectionPolicy.clear(selection)
    }

    @When("the chart pick is cancelled")
    fun cancelPick() {
        selection = ChartSelectionPolicy.cancel(selection)
    }

    @When("the chart period {string} is chosen")
    fun choosePeriod(period: String) {
        selection = ChartSelectionPolicy.setPeriod(selection, ChartPeriod.valueOf(period))
    }

    @Then("the chart anchor is {string}")
    fun anchorIs(date: String) = assertEquals(LocalDate.parse(date), selection.anchor)

    @Then("the chart has no anchor")
    fun noAnchor() = assertNull(selection.anchor)

    @Then("the chart selection has no range")
    fun noRange() = assertNull(selection.range)

    @Then("the chart range is {string} to {string}")
    fun rangeIs(from: String, to: String) =
        assertEquals(DateRange(LocalDate.parse(from), LocalDate.parse(to)), selection.range)

    @Then("the chart period is {string}")
    fun periodIs(period: String) = assertEquals(period, selection.period.name)

    @Then("the chart window on {string} is {string} to {string}")
    fun windowIs(today: String, from: String, to: String) = assertEquals(
        DateRange(LocalDate.parse(from), LocalDate.parse(to)),
        ChartSelectionPolicy.window(selection, LocalDate.parse(today)),
    )

    private var slots: List<LocalDate> = emptyList()

    @Then("the chart draws {int} day slots on {string}")
    fun chartSlots(n: Int, today: String) {
        slots = ChartSelectionPolicy.slots(ChartSelectionPolicy.window(selection, LocalDate.parse(today)))
        assertEquals(n, slots.size)
    }

    @Then("the first day slot is {string}")
    fun firstSlot(date: String) = assertEquals(LocalDate.parse(date), slots.first())

    @Then("the last day slot is {string}")
    fun lastSlot(date: String) = assertEquals(LocalDate.parse(date), slots.last())

    // ------------------------------- heat-map --------------------------------

    private var heatWidth = 0f
    private var heatColumns = 0

    @Given("a heat-map {int} dp wide with {int} columns")
    fun heatmapArea(width: Int, columns: Int) {
        heatWidth = width.toFloat()
        heatColumns = columns
    }

    private val cell: Float get() = HeatmapLayout.cellSide(heatWidth, heatColumns)

    @Then("the heat-map cell side is {int} dp")
    fun cellSide(dp: Int) = assertEquals(dp, cell.roundToInt())

    @Then("the heat-map grid width is {int} dp")
    fun gridWidth(dp: Int) = assertEquals(dp, HeatmapLayout.gridWidth(cell, heatColumns).roundToInt())

    @Then("the heat-map grid height is {int} dp")
    fun gridHeight(dp: Int) = assertEquals(dp, HeatmapLayout.gridHeight(cell).roundToInt())

    // --------------------------- heat-map colours -----------------------------

    /** Smallest per-channel gap that still reads as "a different square". */
    private val minStep = 0.05f

    // Not lateinit: Color is a value class, so it cannot be one.
    private var card: Color = Color.Unspecified
    private var ramp: List<Color> = emptyList()

    @Given("the heat-map palette on the {string} card")
    fun heatmapPalette(theme: String) {
        val dark = theme == "dark"
        // The REAL scheme colours, so this is about the app's themes, not a mock-up.
        card = if (dark) DarkSurfaceContainer else LightSurfaceContainer
        val accent = if (dark) DarkPrimary else LightPrimary
        ramp = HeatmapPalette.ramp(card, accent)
    }

    @Then("the empty square is distinguishable from the card")
    fun emptyVsCard() = assertTrue(
        "empty ${hex(ramp.first())} blends into the card ${hex(card)}",
        distance(ramp.first(), card) >= minStep,
    )

    @Then("each heat-map step is distinguishable from the previous one")
    fun stepsDiffer() = ramp.zipWithNext().forEachIndexed { i, (lower, higher) ->
        assertTrue(
            "step ${i + 1} ${hex(higher)} is indistinguishable from step $i ${hex(lower)}",
            distance(lower, higher) >= minStep,
        )
    }

    @Then("the ramp moves further from the card colour at every step")
    fun rampMovesAway() {
        val away = ramp.map { abs(it.luminance() - card.luminance()) }
        away.zipWithNext().forEachIndexed { i, (lower, higher) ->
            assertTrue(
                "step ${i + 1} sits closer to the card than step $i ($higher vs $lower)",
                higher > lower,
            )
        }
    }

    private var band: Color = Color.Unspecified

    @Given("the pace window band on the {string} card")
    fun paceBand(theme: String) {
        card = if (theme == "dark") DarkSurfaceContainer else LightSurfaceContainer
        band = ChartTint.awayFromCard(card, 0.10f)
    }

    @Then("the band is distinguishable from the card")
    fun bandVsCard() = assertTrue(
        "band ${hex(band)} blends into the card ${hex(card)}",
        distance(band, card) >= minStep,
    )

    @Then("a day at {int} percent of the goal is heat-map step {int}")
    fun stepAt(pct: Int, step: Int) = assertEquals(step, HeatmapPalette.stepOf(pct / 100.0))

    @Then("a day with no record is heat-map step {int}")
    fun stepOfNothing(step: Int) = assertEquals(step, HeatmapPalette.stepOf(null))

    private fun distance(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    private fun hex(c: Color): String = "#%02X%02X%02X".format(
        (c.red * 255).roundToInt(),
        (c.green * 255).roundToInt(),
        (c.blue * 255).roundToInt(),
    )
}
