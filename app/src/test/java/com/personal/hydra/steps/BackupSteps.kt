package com.personal.hydra.steps

import com.personal.hydra.data.backup.BACKUP_SCHEMA_VERSION
import com.personal.hydra.data.backup.BackupErrorCode
import com.personal.hydra.data.backup.BackupException
import com.personal.hydra.data.backup.BackupOp
import com.personal.hydra.data.backup.BackupOutcome
import com.personal.hydra.data.backup.BackupOutcomes
import com.personal.hydra.data.backup.BackupReport
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
                zoneId = "America/Argentina/Buenos_Aires", totalMl = total, createdAt = 1_000L,
                closed = false, morningSharePct = 65,
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

    // --------------------------- outcome reporting ----------------------------

    private lateinit var op: BackupOp
    // Not lateinit: Result is a value class, so it cannot be one.
    private var result: Result<BackupReport>? = null
    private var outcome: BackupOutcome? = null

    @Given("an {string} that handled {int} days and {int} entries")
    fun aSuccess(operation: String, days: Int, entries: Int) {
        op = BackupOp.valueOf(operation)
        result = Result.success(BackupReport(days, entries))
    }

    @Given("an {string} that failed with {string}")
    fun aFailure(operation: String, failure: String) {
        op = BackupOp.valueOf(operation)
        result = Result.failure(throwableFor(failure))
    }

    @When("the backup outcome is resolved")
    fun resolveOutcome() {
        outcome = BackupOutcomes.of(op, result!!)
    }

    // A null outcome IS the defect: it means the screen has nothing to show.
    @Then("the outcome is a finished {string} of {int} days and {int} entries")
    fun outcomeDone(operation: String, days: Int, entries: Int) = assertEquals(
        BackupOutcome.Done(BackupOp.valueOf(operation), BackupReport(days, entries)),
        outcome,
    )

    @Then("the outcome is a failed {string} with code {string}")
    fun outcomeFailed(operation: String, code: String) = assertEquals(
        BackupOutcome.Failed(BackupOp.valueOf(operation), BackupErrorCode.valueOf(code)),
        outcome,
    )

    /**
     * Real throwables, not hand-built codes, wherever the app can produce one:
     * picking the wrong file really does surface a kotlinx SerializationException,
     * and the schema guard really is the one the importer calls.
     */
    private fun throwableFor(failure: String): Throwable = when (failure) {
        "unwritable destination" -> BackupException(BackupErrorCode.OPEN_DESTINATION)
        "unreadable source" -> BackupException(BackupErrorCode.OPEN_SOURCE)
        "newer version" -> runCatching { BackupOutcomes.checkSchema(BACKUP_SCHEMA_VERSION + 1) }.exceptionOrNull()!!
        "malformed json" -> runCatching { json.decodeFromString<HydraBackup>("{ nope }") }.exceptionOrNull()!!
        "unexpected crash" -> RuntimeException("boom")
        else -> error("unknown failure kind: $failure")
    }
}
