package com.shiny.railcctv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.SimpleDateFormat
import java.util.*

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // 1) title/body 폴백: notification -> data
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "알림"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "내용 없음"

        // 2) data 필드
        val locationName = message.data["location_name"].orEmpty()
        val lat = message.data["lat"].orEmpty()
        val lng = message.data["lng"].orEmpty()
        val region = message.data["region"].orEmpty() // 선택사항: 필요시 사용

        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("title", title)
            putExtra("body", body)
            putExtra("time", formattedTime)
            putExtra("location_name", locationName)
            putExtra("lat", lat)
            putExtra("lng", lng)
            putExtra("region", region)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 채널 ID = 서버와 동일하게 "alerts" 로 맞춤
        val channelId = "alerts"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "긴급/경보 알림 채널"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(if (locationName.isNotBlank()) "$body ($locationName)" else body)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 실제 모노 아이콘 권장
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)      // 헤드업
            .setDefaults(NotificationCompat.DEFAULT_ALL)        // 사운드/진동/라이트
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}



