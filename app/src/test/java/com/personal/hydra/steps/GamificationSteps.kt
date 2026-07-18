package com.personal.hydra.steps

import com.personal.hydra.domain.AchievementEvaluator
import com.personal.hydra.domain.StreakCalculator
import com.personal.hydra.domain.model.Achievement
import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.HydrationStats
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate

class GamificationSteps {

    private var today = LocalDate.of(2026, 6, 14)
    private val days = mutableListOf<DayStat>()
    private lateinit var stats: HydrationStats

    @Given("today is {string}")
    fun todayIs(d: String) { today = LocalDate.parse(d) }

    @Given("a completed day on {string}")
    fun completedDay(d: String) { days += DayStat(LocalDate.parse(d), goalMl = 1000, totalMl = 1000) }

    @Given("an incomplete day on {string}")
    fun incompleteDay(d: String) { days += DayStat(LocalDate.parse(d), goalMl = 1000, totalMl = 500) }

    @Given("completed days from {string} to {string}")
    fun completedRange(from: String, to: String) {
        var d = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        while (!d.isAfter(end)) {
            days += DayStat(d, goalMl = 1000, totalMl = 1000)
            d = d.plusDays(1)
        }
    }

    @When("the stats are computed")
    fun computeStats() { stats = StreakCalculator.stats(days, today) }

    @Then("the current streak is {int}")
    fun currentStreak(n: Int) { assertEquals(n, stats.currentStreak) }

    @Then("the best streak is {int}")
    fun bestStreak(n: Int) { assertEquals(n, stats.bestStreak) }

    @Then("the days completed is {int}")
    fun daysCompleted(n: Int) { assertEquals(n, stats.daysCompleted) }

    @Then("achievement {string} is unlocked")
    fun achievementUnlocked(name: String) {
        assertTrue(AchievementEvaluator.isUnlocked(Achievement.valueOf(name), stats))
    }

    @Then("achievement {string} is locked")
    fun achievementLocked(name: String) {
        assertFalse(AchievementEvaluator.isUnlocked(Achievement.valueOf(name), stats))
    }
}
