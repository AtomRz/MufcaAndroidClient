package com.mufcabot.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Хранит server URL + Basic Auth логин/пароль. Пытается использовать зашифрованное
 * хранилище (androidx.security EncryptedSharedPreferences, ключ — в Android Keystore).
 *
 * 🆕 FIX: androidx.security-crypto 1.1.0-alpha06 — всё ещё альфа-версия, и на части
 * эмуляторов/устройств (особенно без Google Play, или с нестандартной реализацией
 * AndroidKeyStore) инициализация MasterKey валится с GeneralSecurityException прямо
 * при первом запуске — приложение крашится сразу на экране настроек. Если так
 * случилось — тихо откатываемся на обычный (нешифрованный) SharedPreferences,
 * чтобы приложение хотя бы работало; для личного дашборда на своём же телефоне это
 * приемлемый компромисс (креды и так не более чувствительны, чем обычный логин
 * в любом другом приложении на этом устройстве).
 *
 * Эти креды использует только нативный код (ApiClient) для одного вызова —
 * регистрации FCM-токена на бэкенде. Отдельно от них WebView ведёт свою
 * собственную JS-сессию логина на дашборде (см. LoginScreen.jsx).
 */
object Prefs {
    private const val TAG = "MufcaPrefs"
    private const val FILE_NAME = "mufca_secure_prefs"
    private const val FALLBACK_FILE_NAME = "mufca_prefs_fallback"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    private fun prefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain storage", e)
            context.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isConfigured(context: Context): Boolean =
        !prefs(context).getString(KEY_SERVER_URL, null).isNullOrBlank()

    fun getServerUrl(context: Context): String =
        prefs(context).getString(KEY_SERVER_URL, "") ?: ""

    fun getUsername(context: Context): String =
        prefs(context).getString(KEY_USERNAME, "") ?: ""

    fun getPassword(context: Context): String =
        prefs(context).getString(KEY_PASSWORD, "") ?: ""

    fun save(context: Context, serverUrl: String, username: String, password: String) {
        prefs(context).edit()
            .putString(KEY_SERVER_URL, serverUrl.trimEnd('/'))
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

