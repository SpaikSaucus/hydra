package com.personal.hydra.data

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.personal.hydra.data.db.HydraDatabase
import com.personal.hydra.data.db.MIGRATION_1_2
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stub Application so Robolectric doesn't run HydraApp.onCreate (WorkManager). */
class MigrationTestApp : Application()

/**
 * The v1 -> v2 migration, on a REAL on-disk SQLite file.
 *
 * Two things are asserted, and the second one is the important one: that the
 * user's rows survive, and that Room's own schema validation accepts the result.
 * Room compares the migrated table against the entity on open — including the
 * column's SQL default — so a migration that drifts from `DayLogEntity` fails
 * here instead of on someone's phone, where the only recovery would be losing
 * their history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MigrationTestApp::class)
class DayLogMigrationTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private var db: HydraDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `a v1 database keeps its days and gains the default balance`() {
        createV1WithOneDay()

        db = Room.databaseBuilder(context, HydraDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val day = runBlocking { db!!.dayLogDao().getDay("2026-06-14") }
        assertEquals("the day itself must survive", 2541, day?.goalMl)
        assertEquals("its intakes must survive", 1750, day?.totalMl)
        // Rows written before the column existed take the app default.
        assertEquals(65, day?.morningSharePct)
    }

    /** The v1 `day_log` exactly as schema 1.json defines it, with one row. */
    private fun createV1WithOneDay() {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `day_log` (" +
                                "`day_key` TEXT NOT NULL, `goal_ml` INTEGER NOT NULL, " +
                                "`base_goal_ml` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, " +
                                "`factor_ml_kg` INTEGER NOT NULL, `manual_adj_pct` INTEGER NOT NULL, " +
                                "`heat_mode` INTEGER NOT NULL, `inferred_season` TEXT NOT NULL, " +
                                "`wake_min` INTEGER NOT NULL, `cutoff_min` INTEGER NOT NULL, " +
                                "`hourly_cap_ml` INTEGER NOT NULL, `zone_id` TEXT NOT NULL, " +
                                "`total_ml` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                                "`closed` INTEGER NOT NULL, PRIMARY KEY(`day_key`))",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `intake_entry` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`day_key` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                                "`amount_ml` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                                "`entered_unit` TEXT NOT NULL, `deleted_at` INTEGER, " +
                                "FOREIGN KEY(`day_key`) REFERENCES `day_log`(`day_key`) " +
                                "ON UPDATE CASCADE ON DELETE CASCADE )",
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_intake_entry_day_key` ON `intake_entry` (`day_key`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_intake_entry_timestamp` ON `intake_entry` (`timestamp`)")
                        // Room's own bookkeeping, so it recognises this as schema 1.
                        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                        db.execSQL(
                            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$V1_IDENTITY_HASH')",
                        )
                        db.execSQL(
                            "INSERT INTO day_log VALUES('2026-06-14', 2541, 2541, 77.0, 33, 0, 0, " +
                                "'WINTER', 420, 1200, 900, 'America/Argentina/Buenos_Aires', 1750, 1000, 0)",
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { it.version = 1 }
    }

    private companion object {
        /** From `app/schemas/.../1.json` — Room refuses to open a DB whose hash it can't match. */
        const val V1_IDENTITY_HASH = "6cc69ab14e1970c8dd3bef69013664aa"
    }
}
