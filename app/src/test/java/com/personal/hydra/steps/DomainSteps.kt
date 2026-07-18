package com.personal.hydra.steps

import com.personal.hydra.domain.GoalCalculator
import com.personal.hydra.domain.Hydration
import com.personal.hydra.domain.RedistributeInput
import com.personal.hydra.domain.Redistributor
import com.personal.hydra.domain.ReminderEvaluator
import com.personal.hydra.domain.ScheduleGenerator
import com.personal.hydra.domain.SeasonInference
import com.personal.hydra.domain.UnitConverter
import com.personal.hydra.domain.model.AppSettings
import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.GoalInput
import com.personal.hydra.domain.model.GoalResult
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.ReminderDecision
import com.personal.hydra.domain.model.Schedule
import com.personal.hydra.domain.model.ScheduleParams
import com.personal.hydra.domain.model.Season
import com.personal.hydra.domain.model.SeasonInfo
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.domain.model.UserProfile
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.time.LocalTime

class DomainSteps {

    // ---- goal calculation ----
    private var weightKg = 70.0
    private var factor = 33
    private var heatOn = false
    private var adjust = 0
    private lateinit var goalResult: GoalResult

    @Given("a weight of {double} kg")
    fun aWeight(kg: Double) { weightKg = kg }

    @Given("a factor of {int} ml per kg")
    fun aFactor(f: Int) { factor = f }

    @Given("heat mode is on")
    fun heatOn() { heatOn = true }

    @Given("a manual adjustment of {int} percent")
    fun adjust(pct: Int) { adjust = pct }

    @When("the goal is calculated")
    fun calcGoal() {
        goalResult = GoalCalculator.calculate(GoalInput(weightKg, factor, heatOn, adjust))
    }

    @Then("the goal is {int} ml")
    fun goalIs(ml: Int) { assertEquals(ml, goalResult.goalMl) }

    @Then("the effective factor is {int}")
    fun effectiveFactor(f: Int) { assertEquals(f, goalResult.effectiveFactor) }

    @Then("the goal warnings include {string}")
    fun warningsInclude(code: String) {
        assertTrue(goalResult.warnings.any { it.code.name == code })
    }

    // ---- unit conversion ----
    private var volumeMl = 0

    @Given("a volume of {int} ml")
    fun aVolume(ml: Int) { volumeMl = ml }

    @Then("converting to pounds and back is {double} kg")
    fun lbRoundTrip(kg: Double) {
        assertEquals(kg, UnitConverter.lbToKg(UnitConverter.kgToLb(weightKg)), 0.001)
    }

    @Then("converting to fluid ounces and back is {int} ml")
    fun flozRoundTrip(ml: Int) {
        assertEquals(ml, UnitConverter.flozToMl(UnitConverter.mlToFloz(volumeMl)))
    }

    @Then("the imperial display is {int} fl oz")
    fun imperialDisplay(oz: Int) {
        assertEquals(oz.toDouble(), UnitConverter.displayVolume(volumeMl, UnitSystem.IMPERIAL), 0.0)
    }

    @Then("the value in litres is {double}")
    fun litres(l: Double) {
        assertEquals(l, UnitConverter.mlToLiters(volumeMl), 0.001)
    }

    // ---- season inference ----
    private var country = ""
    private var date: LocalDate = LocalDate.of(2026, 6, 14)
    private lateinit var seasonInfo: SeasonInfo
    private var season = Season.SUMMER

    @Given("the country is {string}")
    fun theCountry(c: String) { country = c }

    @Given("the date is {string}")
    fun theDate(d: String) { date = LocalDate.parse(d) }

    @Given("the season is {string}")
    fun givenSeason(s: String) { season = Season.valueOf(s) }

    @When("the season is inferred")
    fun inferSeason() {
        seasonInfo = SeasonInference.infer(country, date)
        season = seasonInfo.season
    }

    @Then("the inferred hemisphere is {string}")
    fun hemisphere(h: String) { assertEquals(h, seasonInfo.hemisphere.name) }

    @Then("the inferred season is {string}")
    fun inferredSeason(s: String) { assertEquals(s, seasonInfo.season.name) }

    @Then("heat mode is suggested")
    fun heatSuggested() { assertTrue(seasonInfo.suggestsHeatMode) }

    @Then("heat mode is not suggested")
    fun heatNotSuggested() { assertFalse(seasonInfo.suggestsHeatMode) }

    @Then("turning heat off gives warning {string}")
    fun heatOffWarning(code: String) {
        assertEquals(code, SeasonInference.heatModeWarning(season, false)?.code?.name)
    }

    @Then("turning heat on gives warning {string}")
    fun heatOnWarning(code: String) {
        assertEquals(code, SeasonInference.heatModeWarning(season, true)?.code?.name)
    }

    // ---- schedule + redistribution ----
    private var wake = LocalTime.of(8, 0)
    private var sleep = LocalTime.MIDNIGHT
    private var cutoffMin = 180
    private var schedGoal = 0
    private var capMl = 1000
    private lateinit var schedule: Schedule
    private lateinit var redistributed: Schedule

    @Given("a schedule with wake {string} and sleep {string} and cutoff {int} minutes")
    fun aSchedule(w: String, s: String, cutoff: Int) {
        wake = LocalTime.parse(w); sleep = LocalTime.parse(s); cutoffMin = cutoff
    }

    @Given("a goal of {int} ml with hourly cap {int} ml")
    fun aGoalCap(goal: Int, cap: Int) { schedGoal = goal; capMl = cap }

    private fun params() = ScheduleParams(
        wakeTime = wake, sleepTime = sleep, nightCutoffMinutes = cutoffMin,
        goalMl = schedGoal, maxPerHourMl = capMl,
    )

    @When("the schedule is generated")
    fun genSchedule() { schedule = ScheduleGenerator.generate(params()) }

    @Then("the schedule total is {int} ml")
    fun schedTotal(t: Int) { assertEquals(t, schedule.totalMl) }

    @Then("the schedule total is at most {int} ml")
    fun schedTotalAtMost(t: Int) { assertTrue(schedule.totalMl <= t) }

    @Then("no intake exceeds {int} ml")
    fun noIntakeExceeds(max: Int) { assertTrue(schedule.intakes.all { it.amountMl <= max }) }

    @Then("there are {int} intakes")
    fun nIntakes(n: Int) { assertEquals(n, schedule.intakes.size) }

    @Then("there is no {string} warning")
    fun noWarning(code: String) { assertTrue(schedule.warnings.none { it.code.name == code }) }

    @Then("there is a {string} warning")
    fun aWarning(code: String) { assertTrue(schedule.warnings.any { it.code.name == code }) }

    @When("{int} ml have been consumed by {string} and the plan is redistributed")
    fun redistribute(consumed: Int, now: String) {
        redistributed = Redistributor.redistribute(RedistributeInput(params(), LocalTime.parse(now), consumed))
    }

    @Then("the redistributed total is {int} ml")
    fun redTotal(t: Int) { assertEquals(t, redistributed.totalMl) }

    @Then("no redistributed intake exceeds {int} ml")
    fun noRedExceeds(max: Int) { assertTrue(redistributed.intakes.all { it.amountMl <= max }) }

    @Then("the redistributed plan is empty")
    fun redEmpty() { assertTrue(redistributed.intakes.isEmpty()) }

    // ---- reminder decision ----
    private var reminderConfig = HydraConfig()
    private val reminderToday = TestDates.NORTHERN_WINTER // northern winter -> no auto heat
    private lateinit var decision: ReminderDecision

    @Given("the default reminder config")
    fun defaultReminderConfig() { reminderConfig = HydraConfig() }

    @When("evaluated with {int} ml consumed and no recent intake at {string}")
    fun evalNoRecent(consumed: Int, now: String) {
        decision = ReminderEvaluator.evaluate(reminderConfig, consumed, null, LocalTime.parse(now), reminderToday)
    }

    @When("evaluated with {int} ml consumed and last intake {int} minutes ago at {string}")
    fun evalRecent(consumed: Int, mins: Int, now: String) {
        decision = ReminderEvaluator.evaluate(reminderConfig, consumed, mins, LocalTime.parse(now), reminderToday)
    }

    @Then("a reminder is posted")
    fun reminderPosted() { assertTrue(decision.shouldNotify) }

    @Then("no reminder is posted")
    fun reminderNotPosted() { assertFalse(decision.shouldNotify) }

    @Then("the reminder goal is {int} ml")
    fun reminderGoal(g: Int) { assertEquals(g, decision.goalMl) }

    @Then("the reminder reason is {string}")
    fun reminderReason(r: String) { assertEquals(r, decision.reason.name) }

    // ---- simple/advanced goal resolution ----
    private var resConfig = HydraConfig()
    private var resDate = LocalDate.of(2026, 6, 14)
    private var resolvedGoal = 0

    @Given("a config with weight {double} kg, factor {int}, adjustment {int} percent in simple mode")
    fun configSimple(kg: Double, f: Int, pct: Int) {
        resConfig = HydraConfig(
            profile = UserProfile(weightKg = kg, factorMlKg = f, manualAdjustPct = pct),
            settings = AppSettings(configMode = ConfigMode.SIMPLE),
        )
    }

    @Given("a config with weight {double} kg, factor {int}, adjustment {int} percent in advanced mode")
    fun configAdvanced(kg: Double, f: Int, pct: Int) {
        resConfig = HydraConfig(
            profile = UserProfile(weightKg = kg, factorMlKg = f, manualAdjustPct = pct),
            settings = AppSettings(configMode = ConfigMode.ADVANCED),
        )
    }

    @Given("the country is {string} with date {string}")
    fun countryWithDate(c: String, d: String) {
        resConfig = resConfig.copy(settings = resConfig.settings.copy(countryCode = c))
        resDate = LocalDate.parse(d)
    }

    @When("the effective goal is resolved")
    fun resolveGoal() { resolvedGoal = Hydration.goal(resConfig, resDate).goalMl }

    @Then("the resolved goal is {int} ml")
    fun resolvedGoalIs(g: Int) { assertEquals(g, resolvedGoal) }
}
