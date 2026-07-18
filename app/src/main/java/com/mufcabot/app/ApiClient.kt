package com.mufcabot.app

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Единственные два нативных HTTP-вызова во всём приложении — весь остальной
 * функционал (Status/Chart/History/Settings) живёт внутри WebView, который
 * ходит на тот же бэкенд сам, своей JS-сессией.
 */
object ApiClient {
    private const val TAG = "MufcaApiClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun authHeader(username: String, password: String): String? =
        if (username.isNotBlank() || password.isNotBlank()) Credentials.basic(username, password) else null

    /**
     * Синхронный тест соединения — обязательно вызывать из фонового потока.
     * Возвращает null при успехе, иначе текст ошибки для показа пользователю.
     */
    fun testConnection(serverUrl: String, username: String, password: String): String? {
        return try {
            val requestBuilder = Request.Builder().url("${serverUrl.trimEnd('/')}/api/config")
            authHeader(username, password)?.let { requestBuilder.header("Authorization", it) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> null
                    response.code == 401 -> "Invalid username or password"
                    else -> "Server responded with HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            "Could not reach server: ${e.message}"
        }
    }

    /**
     * Регистрирует текущий FCM-токен на бэкенде (best-effort, в отдельном потоке —
     * не блокирует вызывающий код, ошибки только логируются). Вызывается и при
     * первой настройке приложения, и каждый раз при onNewToken (ротация токена).
     */
    fun registerDevice(context: Context, token: String) {
        if (!Prefs.isConfigured(context)) return
        val serverUrl = Prefs.getServerUrl(context)
        val username = Prefs.getUsername(context)
        val password = Prefs.getPassword(context)

        Thread {
            try {
                val json = JSONObject().apply {
                    put("token", token)
                    put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/api/devices/register")
                    .post(body)
                authHeader(username, password)?.let { requestBuilder.header("Authorization", it) }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Device registered for push notifications")
                    } else {
                        Log.w(TAG, "Device registration failed: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Device registration failed", e)
            }
        }.start()
    }
}
