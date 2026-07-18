package com.personal.hydra.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.room.withTransaction
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.personal.hydra.data.HydrationRepository
import com.personal.hydra.data.HydrationRepositoryImpl
import com.personal.hydra.data.backup.BackupManager
import com.personal.hydra.data.db.HydraDatabase
import com.personal.hydra.data.settings.SettingsRepository
import com.personal.hydra.data.settings.SettingsRepositoryImpl
import com.personal.hydra.reminder.HydraNotifier

/** Manual dependency container (Service Locator). Created once in HydraApp. */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile("hydra_settings") },
    )

    private val database: HydraDatabase = HydraDatabase.get(appContext)

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(dataStore)

    val hydrationRepository: HydrationRepository = HydrationRepositoryImpl(
        dayLogDao = database.dayLogDao(),
        intakeDao = database.intakeDao(),
        settings = settingsRepository,
        transaction = { block -> database.withTransaction { block() } },
    )

    val notifier: HydraNotifier = HydraNotifier(appContext)

    val backupManager: BackupManager = BackupManager(appContext, database, settingsRepository)
}
