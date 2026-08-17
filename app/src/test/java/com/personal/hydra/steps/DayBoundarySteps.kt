package com.personal.hydra.steps

import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.data.HydrationRepositoryImpl
import com.personal.hydra.domain.ReminderEvaluator
import com.personal.hydra.domain.model.AppSettings
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.IntakeSource
import com.personal.hydra.domain.model.toMinuteOfDay
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Drives the REAL repository through a movable fixed clock to pin down which
 * calendar day an intake lands on. Everything goes through the public repository
 * API (never the resolver directly) so the scenarios describe behaviour, not
 * implementation.
 */
class DayBoundarySteps {

    private lateinit var zone: ZoneId
    private lateinit var dayDao: FakeDayLogDao
    private lateinit var intakeDao: FakeIntakeDao
    private lateinit var settings: FakeSettingsRepository
    private lateinit var repo: HydrationRepositoryImpl

    @Given("a day-boundary repository in zone {string} waking at {string} and sleeping at {string}")
    fun freshRepo(zoneId: String, wake: String, sleep: String) {
        zone = ZoneId.of(zoneId)
        dayDao = FakeDayLogDao()
        intakeDao = FakeIntakeDao()
        settings = FakeSettingsRepository(
            HydraConfig(
                settings = AppSettings(
                    wakeTimeMin = LocalTime.parse(wake).toMinuteOfDay(),
                    sleepTimeMin = LocalTime.parse(sleep).toMinuteOfDay(),
                ),
            ),
        )
    }

    /** Moves the clock: the DAOs survive, only the repository/resolver are rebuilt. */
    @Given("the boundary clock reads {string}")
    fun clockReads(localDateTime: String) {
        val clock = Clock.fixed(instantOf(localDateTime), zone)
        repo = HydrationRepositoryImpl(
            dayLogDao = dayDao,
            intakeDao = intakeDao,
            settings = settings,
            transaction = { it() },
            dayKeyResolver = DayKeyResolver(clock),
            clock = clock,
        )
        now = localDateTime
        runBlocking { repo.ensureToday() }
    }

    @When("{int} ml is logged at {string}")
    fun logAt(ml: Int, localDateTime: String) = runBlocking {
        repo.addIntake(ml, IntakeSource.MANUAL, instantOf(localDateTime).toEpochMilli())
        Unit
    }

    @Then("the logged intake day key is {string}")
    fun lastIntakeDayKey(key: String) = assertEquals(key, intakeDao.lastInserted()?.dayKey)

    @Then("the open hydration day is {string}")
    fun openDay(key: String) = runBlocking { assertEquals(key, repo.ensureToday().dayKey) }

    @Then("the hydration day total is {int} ml")
    fun dayTotal(ml: Int) = runBlocking { assertEquals(ml, repo.consumedToday()) }

    @Then("the hydration day {string} is closed")
    fun dayClosed(key: String) = runBlocking {
        assertTrue("day $key should be closed", dayDao.getDay(key)?.closed == true)
    }

    @Given("the morning balance is {int} percent")
    fun morningBalance(pct: Int) = runBlocking { settings.setMorningShare(pct) }

    @Then("the hydration day {string} was paced at {int} percent")
    fun dayMorningShare(key: String, pct: Int) = runBlocking {
        assertEquals(pct, dayDao.getDay(key)?.morningSharePct)
    }

    @Then("the hydration day {string} is open")
    fun dayOpen(key: String) = runBlocking {
        assertFalse("day $key should be open", dayDao.getDay(key)?.closed ?: true)
    }

    @Then("the boundary reminder decision at {string} is {string}")
    fun reminderReason(time: String, reason: String) = runBlocking {
        val c = settings.snapshot()
        val decision = ReminderEvaluator.evaluate(
            config = c,
            consumedMl = repo.consumedToday(),
            minutesSinceLastIntake = repo.minutesSinceLastIntake(instantOf(now).toEpochMilli()),
            now = LocalTime.parse(time),
            // The calendar date of the clock — the day the notification belongs to.
            today = LocalDateTime.parse(now).toLocalDate(),
        )
        assertEquals(reason, decision.reason.name)
    }

    // ------------------- how long a subscription may sleep --------------------

    private var untilNextDayMs: Long = 0

    @Given("a resolver clock reading {string} in {string}")
    fun resolverClock(localDateTime: String, zoneId: String) {
        val z = ZoneId.of(zoneId)
        val clock = Clock.fixed(LocalDateTime.parse(localDateTime).atZone(z).toInstant(), z)
        untilNextDayMs = DayKeyResolver(clock).millisUntilNextDay()
    }

    @Then("the time until the next hydration day is {int} minutes")
    fun untilNextDay(minutes: Int) = assertEquals(minutes.toLong(), untilNextDayMs / 60_000L)

    private var now: String = ""

    private fun instantOf(localDateTime: String) =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant()
}
