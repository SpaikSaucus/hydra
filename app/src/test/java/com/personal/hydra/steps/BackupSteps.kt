package com.personal.hydra.steps

import com.personal.hydra.data.backup.DayLogDto
import com.personal.hydra.data.backup.HydraBackup
import com.personal.hydra.data.backup.IntakeDto
import com.personal.hydra.domain.model.HydraConfig
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals

class BackupSteps {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var backup: HydraBackup
    private lateinit var decoded: HydraBackup

    @Given("a backup with {int} day and {int} intakes totalling {int} ml")
    fun aBackup(days: Int, intakes: Int, total: Int) {
        val dayDtos = (1..days).map {
            DayLogDto(
                dayKey = "2026-06-14", goalMl = total, baseGoalMl = total, weightKg = 77f,
                factorMlKg = 33, manualAdjustPct = 0, heatMode = false, inferredSeason = "WINTER",
                wakeMinuteOfDay = 420, cutoffMinuteOfDay = 1200, hourlyCapMl = 900,
                zoneId = "America/Argentina/Buenos_Aires", totalMl = total, createdAt = 1_000L, closed = false,
            )
        }
        val each = total / intakes
        val intakeDtos = (1..intakes).map {
            IntakeDto(it.toLong(), "2026-06-14", 1_000L + it, each, "MANUAL", "METRIC", null)
        }
        backup = HydraBackup(exportedAt = 1_000L, config = HydraConfig(), days = dayDtos, entries = intakeDtos)
    }

    @When("the backup is encoded and decoded as JSON")
    fun roundTrip() {
        decoded = json.decodeFromString(json.encodeToString(backup))
    }

    @Then("the decoded backup has {int} day and {int} intakes")
    fun decodedHas(days: Int, intakes: Int) {
        assertEquals(days, decoded.days.size)
        assertEquals(intakes, decoded.entries.size)
    }

    @Then("the decoded day total is {int} ml")
    fun decodedTotal(t: Int) {
        assertEquals(t, decoded.days.first().totalMl)
    }
}
