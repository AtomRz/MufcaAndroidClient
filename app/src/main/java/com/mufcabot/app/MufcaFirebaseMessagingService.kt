package com.mufcabot.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

/**
 * 🔑 Бэкенд шлёт ТОЛЬКО data-only сообщения (см. push.py) — намеренно, не
 * notification+data гибрид. Гибридные сообщения при свёрнутом/убитом приложении
 * показываются системой автоматически, МИНУЯ onMessageReceived() — а значит без
 * intent-экстра, и тап по такому уведомлению не мог бы открыть нужный тикер/tf на
 * графике. Data-only гарантирует, что onMessageReceived() вызывается ВСЕГДА
 * (foreground и background одинаково), и мы сами строим уведомление с нужным intent.
 */
class MufcaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        ApiClient.registerDevice(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val title = data["title"] ?: "MUFCA"
        val body = data["body"] ?: ""
        // tp1_hit -> открыть вкладку Status (там баннер про перенос SL в безубыток),
        // signal -> открыть Chart на этой паре/tf
        val tab = if (data["type"] == "tp1_hit") "status" else "chart"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notif_tab", tab)
            putExtra("notif_ticker", data["ticker"])
            putExtra("notif_tf", data["tf"])
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MufcaApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(Random.nextInt(), notification)
    }
}
