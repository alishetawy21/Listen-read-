package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.readerSettingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "clinnote_reader_settings")

class ReaderSettingsStore(private val context: Context) {

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val settingsKey = stringPreferencesKey("reader_settings_json")

  val settingsFlow: Flow<ReaderSettings> =
    context.readerSettingsDataStore.data.map { prefs ->
      val raw = prefs[settingsKey]
      if (raw.isNullOrBlank()) {
        ReaderSettings()
      } else {
        runCatching { json.decodeFromString<ReaderSettings>(raw) }.getOrDefault(ReaderSettings())
      }
    }

  suspend fun getCurrentSettings(): ReaderSettings = settingsFlow.first()

  suspend fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
    context.readerSettingsDataStore.edit { prefs ->
      val current =
        prefs[settingsKey]?.let {
          runCatching { json.decodeFromString<ReaderSettings>(it) }.getOrNull()
        } ?: ReaderSettings()
      val updated = transform(current)
      prefs[settingsKey] = json.encodeToString(updated)
    }
  }
}
