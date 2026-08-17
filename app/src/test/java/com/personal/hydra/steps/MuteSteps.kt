package com.personal.hydra.steps

import com.personal.hydra.domain.ReminderEvaluator
import com.personal.hydra.domain.StreakCalculator
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.time.LocalTime

/** Steps for the one-day "mute reminders" toggle in the Home top bar. */
class MuteSteps {

    private var config = HydraConfig()
    private lateinit var decision: ReminderDecision
    private val streakDays = mutableListOf<DayStat>()
    private lateinit var stats: HydrationStats

    @Given("reminders muted on {string}")
    fun mutedOn(day: String) {
        config = config.copy(remindersMutedDay = day)
    }

    @Given("no reminder mute")
    fun noMute() {
        config = HydraConfig()
    }

    @Given("a tracking pause covering {string} to {string}")
    fun pauseCovering(from: String, to: String) {
        config = config.copy(pauses = config.pauses + PausePeriod(from, to))
    }

    @When("the mute evaluation runs at {string} on {string}")
    fun evaluate(time: String, date: String) {
        decision = ReminderEvaluator.evaluate(config, 0, null, LocalTime.parse(time), LocalDate.parse(date))
    }

    @Then("the mute decision reason is {string}")
    fun reason(r: String) = assertEquals(r, decision.reason.name)

    @Then("the mute decision posts no reminder")
    fun noPost() = assertFalse(decision.shouldNotify)

    @Then("the mute decision posts a reminder")
    fun posts() = assertTrue(decision.shouldNotify)

    // ---- muting is not a pause ----

    @Given("a muted-day streak completed on {string}")
    fun streakCompleted(d: String) {
        streakDays += DayStat(LocalDate.parse(d), goalMl = 1000, totalMl = 1000)
    }

    @Given("a muted-day streak incomplete on {string}")
    fun streakIncomplete(d: String) {
        streakDays += DayStat(LocalDate.parse(d), goalMl = 1000, totalMl = 400)
    }

    @When("the muted-day streak is computed for {string}")
    fun computeStreak(today: String) {
        // A mute is invisible to the streak logic on purpose — only pauses are neutral.
        stats = StreakCalculator.stats(streakDays, LocalDate.parse(today), config.pauses)
    }

    @Then("the muted-day current streak is {int}")
    fun currentStreak(n: Int) = assertEquals(n, stats.currentStreak)

    // ---- backup JSON compatibility ----

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @When("the muted config is encoded and decoded as backup JSON")
    fun roundTrip() {
        config = json.decodeFromString(json.encodeToString(config))
    }

    @Then("the decoded muted day is {string}")
    fun decodedMutedDay(d: String) = assertEquals(d, config.remindersMutedDay)

    @When("an old config JSON without the mute field is decoded")
    fun decodeOld() {
        config = json.decodeFromString("""{"profile":{},"settings":{},"onboarding":{},"configSchemaVersion":1}""")
    }

    @Then("the decoded config has no muted day")
    fun decodedNoMute() = assertNull(config.remindersMutedDay)
}
