package com.ratolab.carrierbandanalyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class BandMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var analyzer: BandAnalyzer

    override fun onCreate() {
        super.onCreate()
        analyzer = BandAnalyzer(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // サービス起動時の初期通知
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notif_scanning)))

        // 監視ループ開始
        serviceScope.launch {
            while (isActive) {
                // 1. バンド取得
                val nowBands = analyzer.scanNowBands()

                // 2. ログ保存の実行 (Priority 4)
                if (nowBands.isNotEmpty()) {
                    analyzer.saveLog(nowBands)
                }

                // 3. 通知の文字を作る
                val contentText = if (nowBands.isEmpty()) {
                    getString(R.string.notif_scanning)
                } else {
                    val bandStr = nowBands.sorted().joinToString(", ")
                    getString(R.string.notif_connected, bandStr)
                }

                // 4. 通知を更新
                updateNotification(contentText)

                delay(3000) // 3秒ごとに更新
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "band_monitor_channel"
        private const val NOTIFICATION_ID = 1
    }
}