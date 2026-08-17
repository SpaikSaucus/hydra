package com.personal.hydra.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personal.hydra.data.db.HydraDatabase
import com.personal.hydra.data.db.entity.DayLogEntity
import com.personal.hydra.data.db.entity.IntakeEntryEntity
import com.personal.hydra.domain.model.IntakeSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stub Application so Robolectric doesn't run HydraApp.onCreate (WorkManager). */
class RoomTestApp : Application()

/**
 * Guards a real data-loss trap: refreshing the open day's snapshot must UPDATE the
 * row in place, never INSERT-OR-REPLACE it. REPLACE deletes the day_log row first,
 * and intake_entry's ON DELETE CASCADE would wipe the day's logged intakes. Uses a
 * real in-memory Room DB (Robolectric) so FK enforcement is genuinely exercised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RoomTestApp::class)
class DayLogDaoCascadeTest {

    private lateinit var db: HydraDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HydraDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun day(key: String, goal: Int) = DayLogEntity(
        dayKey = key, goalMl = goal, baseGoalMl = goal, weightKg = 70f, factorMlKg = 33,
        manualAdjustPct = 0, heatMode = false, inferredSeason = "WINTER",
        wakeMinuteOfDay = 420, cutoffMinuteOfDay = 1200, hourlyCapMl = 900,
        morningSharePct = 65, zoneId = "UTC", totalMl = 0, createdAt = 0L, closed = false,
    )

    private fun entry(key: String, ml: Int) = IntakeEntryEntity(
        dayKey = key, timestamp = 0L, amountMl = ml, source = IntakeSource.MANUAL,
    )

    @Test
    fun refreshingOpenDaySnapshotKeepsIntakeEntries() = runBlocking {
        db.dayLogDao().insertIfAbsent(day("2026-07-18", 2310))
        db.intakeDao().insert(entry("2026-07-18", 500))
        db.intakeDao().insert(entry("2026-07-18", 250))
        assertEquals(750, db.intakeDao().sumOfDay("2026-07-18"))

        // Profile changed -> ensureDayOpen re-derives and calls update() in place.
        val existing = db.dayLogDao().getDay("2026-07-18")!!
        db.dayLogDao().update(existing.copy(goalMl = 3300, weightKg = 100f))

        // The @Update must NOT cascade-delete the day's intakes (a REPLACE would).
        assertEquals(750, db.intakeDao().sumOfDay("2026-07-18"))
        assertEquals(3300, db.dayLogDao().getDay("2026-07-18")!!.goalMl)
    }

    @Test
    fun foreignKeyCascadeIsEnforced() = runBlocking {
        // Confirms the FK cascade is actually active, so the test above is meaningful.
        db.dayLogDao().insertIfAbsent(day("2026-07-18", 2310))
        db.intakeDao().insert(entry("2026-07-18", 500))
        db.dayLogDao().clear() // DELETE FROM day_log -> cascades to intake_entry
        assertEquals(0, db.intakeDao().sumOfDay("2026-07-18"))
    }
}
