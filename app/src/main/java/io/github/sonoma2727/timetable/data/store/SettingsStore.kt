package io.github.sonoma2727.timetable.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val keyFirstWeekMonday = longPreferencesKey("first_week_monday_epoch_day")

    val firstWeekMonday: Flow<Long?> = context.settingsDataStore.data
        .map { it[keyFirstWeekMonday] }

    suspend fun currentFirstWeekMonday(): Long? = firstWeekMonday.first()

    suspend fun setFirstWeekMonday(epochDay: Long) {
        context.settingsDataStore.edit { it[keyFirstWeekMonday] = epochDay }
    }
}
