package com.personal.hydra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.hydra.data.db.dao.DayLogDao
import com.personal.hydra.data.db.dao.IntakeDao
import com.personal.hydra.data.db.entity.DayLogEntity
import com.personal.hydra.data.db.entity.IntakeEntryEntity

/**
 * v1 -> v2: `day_log` remembers the morning/afternoon balance of its own day.
 *
 * Rows written before v2 predate the setting being snapshotted, so they take the
 * app default (65). That is a guess, but a bounded one: it is the value the vast
 * majority of those days actually ran on, and the alternative — reading the
 * CURRENT setting — would silently redraw old days every time the slider moves,
 * which is the bug this column exists to fix.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE day_log ADD COLUMN morning_share_pct INTEGER NOT NULL DEFAULT 65")
    }
}

@Database(
    entities = [DayLogEntity::class, IntakeEntryEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HydraDatabase : RoomDatabase() {
    abstract fun dayLogDao(): DayLogDao
    abstract fun intakeDao(): IntakeDao

    companion object {
        const val NAME = "hydra.db"

        @Volatile
        private var instance: HydraDatabase? = null

        fun get(context: Context): HydraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HydraDatabase::class.java,
                    NAME,
                )
                    // Never destroy user history: every migration is explicit, and
                    // there is deliberately no fallbackToDestructiveMigration.
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
