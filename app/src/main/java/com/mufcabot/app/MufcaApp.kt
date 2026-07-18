package com.mufcabot.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MufcaApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "mufca_signals"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Trading signals",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "New signals, TP1 hits, and other MUFCA alerts"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
