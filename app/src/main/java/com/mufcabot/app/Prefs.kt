package com.mufcabot.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Хранит server URL + Basic Auth логин/пароль в зашифрованном хранилище
 * (androidx.security EncryptedSharedPreferences, ключ — в Android Keystore).
 * Эти креды используются нативным кодом (ApiClient) для одного вызова —
 * регистрации FCM-токена на бэкенде. Отдельно от них WebView ведёт свою
 * собственную JS-сессию логина на дашборде (см. LoginScreen.jsx) — это два
 * независимых, изолированных друг от друга места входа с одними и теми же
 * учётными данными.
 */
object Prefs {
    private const val FILE_NAME = "mufca_secure_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
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
