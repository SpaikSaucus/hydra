package com.personal.hydra.steps

import com.personal.hydra.domain.PauseManager
import com.personal.hydra.domain.ReminderEvaluator
import com.personal.hydra.domain.StreakCalculator
import com.personal.hydra.domain.model.AppSettings
import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.HydrationStats
import com.personal.hydra.domain.model.PausePeriod
import com.personal.hydra.domain.model.ReminderDecision
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.time.LocalTime

/**
 * Steps for the pause and morning/afternoon balance features. Self-contained
 * (Cucumber glue classes share no state) with distinct wording so no step
 * expression collides with DomainSteps/GamificationSteps.
 */
class PauseBalanceSteps {

    private var config = HydraConfig()
    private lateinit var decision: ReminderDecision
    private var pauses: List<PausePeriod> = emptyList()
    private val streakDays = mutableListOf<DayStat>()
    private lateinit var stats: HydrationStats

    // ---- pause vs reminder decision ----

    @Given("a pause config from {string} to {string}")
    fun pauseConfig(from: String, to: String) {
        config = HydraConfig(pauses = listOf(PausePeriod(from, to)))
    }

    @When("the paused evaluation runs with {int} ml at {string} on {string}")
    fun evalPaused(consumed: Int, time: String, date: String) {
        decision = ReminderEvaluator.evaluate(config, consumed, null, LocalTime.parse(time), LocalDate.parse(date))
    }

    @Then("the paused decision reason is {string}")
    fun pausedReason(r: String) = assertEquals(r, decision.reason.name)

    @Then("the paused decision posts no reminder")
    fun pausedNoPost() = assertFalse(decision.shouldNotify)

    @Then("the paused decision posts a reminder")
    fun pausedPosts() = assertTrue(decision.shouldNotify)

    // ---- pause lifecycle (start / clamp / resume early) ----

    @Given("no pause history")
    fun noPauses() {
        pauses = emptyList()
    }

    @When("a pause of {int} days starts on {string}")
    fun startPause(days: Int, date: String) {
        pauses = PauseManager.startPause(pauses, LocalDate.parse(date), days)
    }

    @When("the pause is resumed early on {string}")
    fun resumeEarly(date: String) {
        pauses = PauseManager.resumeEarly(pauses, LocalDate.parse(date))
    }

    @Then("the last pause period ends on {string}")
    fun lastPauseEnds(date: String) = assertEquals(date, pauses.last().endDay)

    @Then("the remaining pause days on {string} are {int}")
    fun remainingDays(date: String, n: Int) =
        assertEquals(n, PauseManager.remainingDays(pauses, LocalDate.parse(date)))

    @Then("there is no pause history")
    fun noPauseHistory() = assertTrue(pauses.isEmpty())

    // ---- pause vs streaks ----

    @Given("a streak day completed on {string}")
    fun streakCompleted(d: String) {
        streakDays += DayStat(LocalDate.parse(d), goalMl = 1000, totalMl = 1000)
    }

    @Given("a streak day incomplete on {string}")
    fun streakIncomplete(d: String) {
        streakDays += DayStat(LocalDate.parse(d), goalMl = 1000, totalMl = 500)
    }

    @Given("a pause covering {string} to {string}")
    fun pauseCovering(from: String, to: String) {
        pauses = pauses + PausePeriod(from, to)
    }

    @When("the paused stats are computed for {string}")
    fun computePausedStats(today: String) {
        stats = StreakCalculator.stats(streakDays, LocalDate.parse(today), pauses)
    }

    @Then("the paused current streak is {int}")
    fun pausedCurrentStreak(n: Int) = assertEquals(n, stats.currentStreak)

    @Then("the paused best streak is {int}")
    fun pausedBestStreak(n: Int) = assertEquals(n, stats.bestStreak)

    @Then("the paused average percent is {int}")
    fun pausedAverage(pct: Int) = assertEquals(pct.toDouble(), stats.averagePercent * 100, 0.01)

    // ---- config JSON compatibility ----

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @When("the config is encoded and decoded as backup JSON")
    fun configRoundTrip() {
        config = json.decodeFromString(json.encodeToString(config))
    }

    @Then("the decoded config still has a pause on {string}")
    fun decodedPauseOn(d: String) = assertTrue(PauseManager.isPaused(config.pauses, LocalDate.parse(d)))

    @When("an old config JSON without the new fields is decoded")
    fun decodeOldConfig() {
        val raw = """{"profile":{},"settings":{},"onboarding":{},"configSchemaVersion":1}"""
        config = json.decodeFromString(raw)
    }

    @Then("the decoded morning share is {int}")
    fun decodedMorningShare(n: Int) = assertEquals(n, config.settings.morningSharePct)

    @Then("the decoded config has no pauses")
    fun decodedNoPauses() = assertTrue(config.pauses.isEmpty())

    // ---- morning/afternoon balance ----

    // Northern winter (default country -> NORTH), so no seasonal heat inflation.
    private val balanceToday: LocalDate = TestDates.NORTHERN_WINTER

    @Given("a balance config with morning share {int}")
    fun balanceConfig(share: Int) {
        config = HydraConfig(settings = AppSettings(morningSharePct = share))
    }

    @When("the balance is evaluated with {int} ml consumed and last intake {int} minutes ago at {string}")
    fun evalBalance(consumed: Int, mins: Int, time: String) {
        decision = ReminderEvaluator.evaluate(config, consumed, mins, LocalTime.parse(time), balanceToday)
    }

    @Then("the balance decision posts no reminder")
    fun balanceNoPost() = assertFalse(decision.shouldNotify)

    @Then("the balance decision posts a reminder")
    fun balancePosts() = assertTrue(decision.shouldNotify)

    @Then("the balance decision reason is {string}")
    fun balanceReason(r: String) = assertEquals(r, decision.reason.name)
}
