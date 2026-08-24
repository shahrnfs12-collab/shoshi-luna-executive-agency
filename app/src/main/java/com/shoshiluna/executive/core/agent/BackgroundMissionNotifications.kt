package com.shoshiluna.executive.core.agent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shoshiluna.executive.MainActivity
import com.shoshiluna.executive.core.empire.MissionStatus

object BackgroundMissionNotifications {
    private const val CHANNEL_ID = "executive_missions"

    fun show(context: Context, missionId: String, title: String, status: MissionStatus) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "משימות שושי ולונה", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val (heading, detail) = when (status) {
            MissionStatus.REVIEW -> "המשימה מוכנה לבדיקה" to title
            MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE -> "המשימה ממתינה לחיבור AI" to title
            MissionStatus.FAILED -> "המשימה נכשלה" to title
            else -> "המשימה עודכנה" to "$title • $status"
        }
        val openApp = PendingIntent.getActivity(
            context,
            missionId.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            missionId.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(heading)
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        )
    }
}
