package io.github.sonoma2727.timetable.data.store

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Credentials(
    val account: String,
    val password: String,
)

private val Context.credentialsDataStore by preferencesDataStore(name = "credentials")

class CredentialsStore(private val context: Context) {

    private val keyAccount = stringPreferencesKey("account")
    private val keyPassword = stringPreferencesKey("password")
    private val keyRemember = booleanPreferencesKey("remember")

    val credentials: Flow<Credentials?> = context.credentialsDataStore.data.map { prefs ->
        val remember = prefs[keyRemember] ?: false
        val account = prefs[keyAccount]
        val encrypted = prefs[keyPassword]
        if (!remember || account.isNullOrEmpty() || encrypted.isNullOrEmpty()) {
            null
        } else {
            try {
                Credentials(account, CryptoManager.decryptFromString(encrypted))
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun current(): Credentials? = credentials.first()

    suspend fun save(account: String, password: String, remember: Boolean) {
        try {
            context.credentialsDataStore.edit { prefs ->
                prefs[keyRemember] = remember
                if (remember) {
                    prefs[keyAccount] = account
                    prefs[keyPassword] = CryptoManager.encryptToString(password)
                } else {
                    prefs.remove(keyAccount)
                    prefs.remove(keyPassword)
                }
            }
        } catch (t: Throwable) {
            Log.e("CredentialsStore", "save failed, login continues without persistence", t)
        }
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
