package com.personal.hydra.data.backup

import kotlinx.serialization.SerializationException

/** Which half of the backup feature produced an outcome. */
enum class BackupOp { EXPORT, IMPORT }

/**
 * Neutral reason a backup failed. Codes, not sentences: the data layer must not
 * build user-facing text (same rule the domain's [com.personal.hydra.domain.model.WarningCode]
 * follows) — the settings screen translates these to es/en.
 */
enum class BackupErrorCode { OPEN_DESTINATION, OPEN_SOURCE, NEWER_SCHEMA, MALFORMED, UNKNOWN }

/** Failure carrying its own classification, so nothing has to parse a message. */
class BackupException(val code: BackupErrorCode, cause: Throwable? = null) :
    Exception(code.name, cause)

/** How much the operation actually moved — the numbers the confirmation shows. */
data class BackupReport(val days: Int, val entries: Int)

/** What the settings screen has to tell the user after an export or an import. */
sealed interface BackupOutcome {
    val op: BackupOp

    data class Done(override val op: BackupOp, val report: BackupReport) : BackupOutcome
    data class Failed(override val op: BackupOp, val code: BackupErrorCode) : BackupOutcome
}

/**
 * Turns the [Result] of an export/import into something showable. Pure Kotlin so
 * the mapping is unit-tested without Android: the whole point is that neither a
 * success nor a failure may end up silent.
 */
object BackupOutcomes {

    /** Always returns something to show: an outcome is never absent. */
    fun of(op: BackupOp, result: Result<BackupReport>): BackupOutcome = result.fold(
        onSuccess = { BackupOutcome.Done(op, it) },
        onFailure = { BackupOutcome.Failed(op, classify(it)) },
    )

    fun classify(t: Throwable): BackupErrorCode = when (t) {
        is BackupException -> t.code
        // Picking the wrong file is the likeliest mistake, and it lands here.
        is SerializationException -> BackupErrorCode.MALFORMED
        else -> BackupErrorCode.UNKNOWN
    }

    /** Refuses a file written by a newer app: its fields could mean anything here. */
    fun checkSchema(schemaVersion: Int) {
        if (schemaVersion > BACKUP_SCHEMA_VERSION) throw BackupException(BackupErrorCode.NEWER_SCHEMA)
    }
}
