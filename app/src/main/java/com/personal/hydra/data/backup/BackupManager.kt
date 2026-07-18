package com.personal.hydra.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.personal.hydra.R
import com.personal.hydra.data.db.HydraDatabase
import com.personal.hydra.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ImportStrategy { MERGE, REPLACE }

data class ImportReport(val days: Int, val entries: Int)

/**
 * Manual JSON export/import via the Storage Access Framework (the caller passes
 * a content Uri obtained from CreateDocument/OpenDocument), which needs NO
 * storage permission.
 */
class BackupManager(
    private val context: Context,
    private val db: HydraDatabase,
    private val settings: SettingsRepository,
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true },
) {

    suspend fun export(target: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val backup = HydraBackup(
                exportedAt = System.currentTimeMillis(),
                config = settings.snapshot(),
                days = db.dayLogDao().allForExport().map { it.toDto() },
                entries = db.intakeDao().allForExport().map { it.toDto() },
            )
            val bytes = json.encodeToString(backup).toByteArray()
            context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                ?: error(context.getString(R.string.backup_error_open_dest))
            Unit
        }
    }

    suspend fun import(source: Uri, strategy: ImportStrategy): Result<ImportReport> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
                ?: error(context.getString(R.string.backup_error_open_src))
            val backup = json.decodeFromString<HydraBackup>(text)
            require(backup.schemaVersion <= BACKUP_SCHEMA_VERSION) {
                context.getString(R.string.backup_error_newer_version)
            }
            db.withTransaction {
                if (strategy == ImportStrategy.REPLACE) {
                    db.intakeDao().clear()
                    db.dayLogDao().clear()
                }
                db.dayLogDao().upsertAll(backup.days.map { it.toEntity() })
                db.intakeDao().upsertAll(backup.entries.map { it.toEntity() })
                // Trust the live intake rows, not the file's cached totals: recompute
                // every day's total so a stale/edited total_ml can't desync history.
                db.dayLogDao().recomputeAllTotals()
            }
            settings.replaceConfig(backup.config)
            ImportReport(backup.days.size, backup.entries.size)
        }
    }
}
