package com.navink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    data class Credentials(
        val serverUrl: String = "",
        val username: String = "",
        val password: String = "",
    ) {
        val hasCredentials: Boolean get() =
            serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    suspend fun getCredentials(): Credentials {
        val prefs = dataStore.data.first()
        return Credentials(
            serverUrl = prefs[SERVER_URL_KEY] ?: "",
            username = prefs[USERNAME_KEY] ?: "",
            password = prefs[PASSWORD_KEY] ?: "",
        )
    }

    suspend fun saveCredentials(serverUrl: String, username: String, password: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = serverUrl.trimEnd('/')
            prefs[USERNAME_KEY] = username
            prefs[PASSWORD_KEY] = password
        }
    }

    suspend fun getStorageLocation(): String =
        dataStore.data.first()[STORAGE_KEY] ?: "external"

    suspend fun saveStorageLocation(location: String) {
        dataStore.edit { it[STORAGE_KEY] = location }
    }

    suspend fun getOfflineMode(): Boolean =
        dataStore.data.first()[OFFLINE_MODE_KEY] ?: false

    suspend fun saveOfflineMode(offline: Boolean) {
        dataStore.edit { it[OFFLINE_MODE_KEY] = offline }
    }

    companion object {
        val SERVER_URL_KEY = stringPreferencesKey("server_url")
        val USERNAME_KEY = stringPreferencesKey("username")
        val PASSWORD_KEY = stringPreferencesKey("password")
        val STORAGE_KEY = stringPreferencesKey("storage_location")
        val OFFLINE_MODE_KEY = booleanPreferencesKey("offline_mode")
    }
}
