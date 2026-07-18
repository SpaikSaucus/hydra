package com.personal.hydra.steps

import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.data.HydrationRepositoryImpl
import com.personal.hydra.domain.model.AppSettings
import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.IntakeSource
import com.personal.hydra.domain.model.UserProfile
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class IntegrationSteps {

    private lateinit var dayDao: FakeDayLogDao
    private lateinit var intakeDao: FakeIntakeDao
    private lateinit var settings: FakeSettingsRepository
    private lateinit var repo: HydrationRepositoryImpl
    private var lastId = 0L
    private var lastDayKey = ""

    // Fixed instant matching the repository clock, so writes and reads land on the
    // same hydration day regardless of the machine's real date (deterministic).
    private val nowMillis = Instant.parse("2026-06-14T13:00:00Z").toEpochMilli()

    @Given("a fresh repository with profile weight {int} kg, factor {int}, advanced mode")
    fun freshRepo(weight: Int, factor: Int) {
        dayDao = FakeDayLogDao()
        intakeDao = FakeIntakeDao()
        settings = FakeSettingsRepository(
            HydraConfig(
                profile = UserProfile(weightKg = weight.toDouble(), factorMlKg = factor),
                settings = AppSettings(configMode = ConfigMode.ADVANCED),
            ),
        )
        // Fixed clock: 2026-06-14 13:00 UTC == 10:00 in Buenos Aires (UTC-3).
        val clock = Clock.fixed(Instant.ofEpochMilli(nowMillis), ZoneId.of("America/Argentina/Buenos_Aires"))
        repo = HydrationRepositoryImpl(
            dayLogDao = dayDao,
            intakeDao = intakeDao,
            settings = settings,
            transaction = { it() },
            dayKeyResolver = DayKeyResolver(clock),
            clock = clock,
        )
    }

    @When("I ensure today is open")
    fun ensureOpen() = runBlocking { repo.ensureToday(); Unit }

    @When("I log {int} ml")
    fun log(ml: Int) = runBlocking {
        lastId = repo.addIntake(ml, IntakeSource.MANUAL, nowMillis)
        intakeDao.lastInserted()?.let { lastDayKey = it.dayKey }
        Unit
    }

    @When("the weight changes to {int} kg")
    fun changeWeight(kg: Int) = runBlocking { settings.setWeightKg(kg.toDouble()); Unit }

    @When("I delete the last entry")
    fun deleteLast() = runBlocking { repo.undoIntake(lastId, lastDayKey); Unit }

    @Then("today's total is {int} ml")
    fun todayTotal(t: Int) = runBlocking { assertEquals(t, repo.consumedToday()) }

    @Then("today's goal is {int} ml")
    fun todayGoal(g: Int) = runBlocking { assertEquals(g, repo.ensureToday().goalMl) }
}
