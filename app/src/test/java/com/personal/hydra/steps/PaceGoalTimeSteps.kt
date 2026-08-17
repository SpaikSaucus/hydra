package com.personal.hydra.steps

import com.personal.hydra.domain.DayPace
import com.personal.hydra.domain.GoalReachAnalytics
import com.personal.hydra.domain.GoalReachSummary
import com.personal.hydra.domain.PaceCurve
import com.personal.hydra.domain.TimedIntake
import com.personal.hydra.domain.model.DayStat
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Steps for the Home pace curve and the goal-completion-time analytics. */
class PaceGoalTimeSteps {

    // ------------------------------ pace curve --------------------------------

    private var goalMl = 0
    private var wakeMinute = 0
    private var windowMinutes = 0
    private var morningShare = 65
    private val paceIntakes = mutableListOf<Pair<String, Int>>()
    private lateinit var pace: DayPace

    /** Any fixed date works: the pace only reads the time of day. */
    private val paceDate: LocalDate = LocalDate.parse("2026-06-14")

    @Given("a pace with goal {int} ml, wake {string}, window {int} minutes and morning share {int}")
    fun paceConfig(goal: Int, wake: String, window: Int, share: Int) {
        goalMl = goal
        wakeMinute = LocalTime.parse(wake).let { it.hour * 60 + it.minute }
        windowMinutes = window
        morningShare = share
        paceIntakes.clear()
    }

    @Then("the pace target at {string} is {int} ml")
    fun paceTarget(time: String, ml: Int) {
        val t = LocalTime.parse(time)
        assertEquals(
            ml,
            PaceCurve.idealAtClock(goalMl, t.hour * 60 + t.minute, wakeMinute, windowMinutes, morningShare),
        )
    }

    @Given("a pace intake of {int} ml at {string}")
    fun paceIntake(ml: Int, time: String) {
        paceIntakes += time to ml
    }

    @When("the pace is computed at {string} in zone {string}")
    fun computePace(time: String, zoneId: String) {
        val zone = ZoneId.of(zoneId)
        pace = PaceCurve.of(
            goalMl = goalMl,
            wakeMinute = wakeMinute,
            windowMinutes = windowMinutes,
            morningSharePct = morningShare,
            intakes = paceIntakes.map { (t, ml) ->
                TimedIntake(paceDate.atTime(LocalTime.parse(t)).atZone(zone).toInstant().toEpochMilli(), ml)
            },
            zone = zone,
            now = LocalTime.parse(time),
        )
    }

    @Then("the pace actual is {int} ml")
    fun paceActual(ml: Int) = assertEquals(ml, pace.nowActualMl)

    @Then("the pace ideal is {int} ml")
    fun paceIdeal(ml: Int) = assertEquals(ml, pace.nowIdealMl)

    @Then("the pace delta is {int} ml")
    fun paceDelta(ml: Int) = assertEquals(ml, pace.deltaMl)

    @Then("the last actual point is at minute {int}")
    fun lastActualPoint(minute: Int) = assertEquals(minute, pace.actual.last().minuteOfDay)

    @Then("the pace marks {int} drinks")
    fun drinkCount(n: Int) = assertEquals(n, pace.drinks.size)

    @Then("drink {int} is marked at minute {int} holding {int} ml")
    fun drinkAt(index: Int, minute: Int, ml: Int) {
        assertEquals(minute, pace.drinks[index].minuteOfDay)
        assertEquals(ml, pace.drinks[index].ml)
    }

    // -------------------------- goal completion time --------------------------

    private val goalDays = mutableListOf<DayStat>()
    private val goalIntakes = mutableListOf<Pair<String, Int>>()
    private lateinit var reach: GoalReachSummary

    @Given("a goal-time day {string} with goal {int} ml")
    fun goalTimeDay(date: String, goal: Int) {
        goalDays += DayStat(LocalDate.parse(date), goalMl = goal, totalMl = 0)
    }

    @Given("a goal-time intake of {int} ml at {string}")
    fun goalTimeIntake(ml: Int, localDateTime: String) {
        goalIntakes += localDateTime to ml
    }

    @When("the goal times are computed in zone {string}")
    fun computeGoalTimes(zoneId: String) {
        val zone = ZoneId.of(zoneId)
        reach = GoalReachAnalytics.of(
            days = goalDays,
            intakes = goalIntakes.map { (t, ml) ->
                TimedIntake(LocalDateTime.parse(t).atZone(zone).toInstant().toEpochMilli(), ml)
            },
            zone = zone,
        )
    }

    @Then("the goal on {string} was reached at {string}")
    fun reachedAt(date: String, time: String) {
        val expected = LocalTime.parse(time).let { it.hour * 60 + it.minute }
        assertEquals(expected, reach.points.first { it.date == LocalDate.parse(date) }.minuteOfDay)
    }

    @Then("the goal on {string} was not reached")
    fun notReached(date: String) =
        assertNull(reach.points.first { it.date == LocalDate.parse(date) }.minuteOfDay)

    @Then("{int} of {int} days reached the goal")
    fun reachedCount(reached: Int, total: Int) {
        assertEquals(reached, reach.reachedDays)
        assertEquals(total, reach.totalDays)
    }

    @Then("the typical completion time is {string}")
    fun typicalTime(time: String) {
        val expected = LocalTime.parse(time).let { it.hour * 60 + it.minute }
        assertEquals(expected, reach.medianMinute)
    }
}
