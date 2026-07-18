package com.personal.hydra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.personal.hydra.data.db.dao.DayLogDao
import com.personal.hydra.data.db.dao.IntakeDao
import com.personal.hydra.data.db.entity.DayLogEntity
import com.personal.hydra.data.db.entity.IntakeEntryEntity

@Database(
    entities = [DayLogEntity::class, IntakeEntryEntity::class],
    version = 1,
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
                    // Never destroy user history: migrations must be explicit from v2+.
                    .build()
                    .also { instance = it }
            }
    }
}
