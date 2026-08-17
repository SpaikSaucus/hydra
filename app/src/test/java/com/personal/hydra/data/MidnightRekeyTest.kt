package com.personal.hydra.data

import app.cash.turbine.test
import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.domain.model.IntakeSource
import com.personal.hydra.steps.FakeDayLogDao
import com.personal.hydra.steps.FakeIntakeDao
import com.personal.hydra.steps.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The app is allowed to sit open across midnight. When it does, the "today" flows
 * must re-key themselves: an intake logged at 00:05 is already filed under the new
 * calendar day, so a Home screen still bound to yesterday would show a ring that
 * refuses to move and a list the drink never appears in.
 *
 * Driven by a [Clock] wired to the test scheduler's VIRTUAL time, so the ticker's
 * sleep and the calendar day advance together and the test costs no real seconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MidnightRekeyTest {

    private val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    private lateinit var dayDao: FakeDayLogDao
    private lateinit var intakeDao: FakeIntakeDao

    @Test
    fun `today re-keys itself after midnight with no config write`() = runTest {
        val repo = repoStartingAt("2026-06-13T23:50")

        repo.observeToday().test {
            assertEquals("2026-06-13", awaitItem()?.dayKey)
            advanceTimeBy(11 * 60_000L) // 00:01, next calendar day
            assertEquals("2026-06-14", awaitItem()?.dayKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the observed entry list starts empty on the new day`() = runTest {
        val clock = virtualClock("2026-06-13T23:55")
        val repo = repoWith(clock)
        repo.addIntake(500, IntakeSource.MANUAL, clock.instant().toEpochMilli())

        repo.observeTodayEntries().test {
            assertEquals(listOf(500), awaitItem().map { it.amountMl })
            advanceTimeBy(10 * 60_000L) // 00:05
            assertEquals(emptyList<Int>(), awaitItem().map { it.amountMl })
            cancelAndIgnoreRemainingEvents()
        }
        // And the glass that follows is filed under the NEW day, matching the list.
        repo.addIntake(250, IntakeSource.MANUAL, clock.instant().toEpochMilli())
        assertEquals("2026-06-14", intakeDao.lastInserted()?.dayKey)
    }

    @Test
    fun `yesterday closes itself when the day rolls over while subscribed`() = runTest {
        val repo = repoStartingAt("2026-06-13T23:50")

        repo.observeToday().test {
            awaitItem()
            advanceTimeBy(11 * 60_000L)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue("yesterday must be frozen", dayDao.store.getValue("2026-06-13").closed)
        assertFalse("today must stay open", dayDao.store.getValue("2026-06-14").closed)
    }

    // ------------------------------------------------------------------ helpers

    private fun kotlinx.coroutines.test.TestScope.repoStartingAt(at: String) = repoWith(virtualClock(at))

    private fun repoWith(clock: Clock): HydrationRepositoryImpl {
        dayDao = FakeDayLogDao()
        intakeDao = FakeIntakeDao()
        return HydrationRepositoryImpl(
            dayLogDao = dayDao,
            intakeDao = intakeDao,
            settings = FakeSettingsRepository(),
            transaction = { it() },
            dayKeyResolver = DayKeyResolver(clock),
            clock = clock,
        )
    }

    /** A [Clock] that advances with the coroutine test scheduler. */
    private fun kotlinx.coroutines.test.TestScope.virtualClock(at: String): Clock {
        // Local copy on purpose: inside a Clock subclass, `zone` resolves to the
        // synthetic property of getZone() and recurses forever.
        val clockZone = zone
        val start = LocalDateTime.parse(at).atZone(clockZone).toInstant()
        val scheduler = testScheduler
        return object : Clock() {
            override fun getZone(): ZoneId = clockZone
            override fun withZone(z: ZoneId?): Clock = this
            override fun instant(): Instant = start.plusMillis(scheduler.currentTime)
        }
    }
}
