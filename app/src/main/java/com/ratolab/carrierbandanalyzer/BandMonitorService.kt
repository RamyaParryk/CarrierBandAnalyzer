package com.ratolab.carrierbandanalyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

class BandMonitorService : Service() {

    private lateinit var analyzer: BandAnalyzer
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        analyzer = BandAnalyzer(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // サービスが既に動いていれば何もしない
        if (monitorJob?.isActive == true) return START_STICKY

        // 1. 通知を出してフォアグラウンド化（死なないようにする）
        val notification = buildNotification("監視を開始しました...")

        // Android 14対応: サービスの種類を明示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // 2. 監視ループ開始 (Coroutines)
        monitorJob = serviceScope.launch {
            while (isActive) {
                // バンドスキャン & 履歴保存
                val now = analyzer.scanNowBands()
                val carrier = analyzer.getCarrier()

                // 通知の文字を更新
                val text = if (now.isEmpty()) "No signal" else "$carrier: ${now.sorted().joinToString(", ")}"
                updateNotification(text)

                // 3秒待機 (バッテリー消費とのトレードオフ)
                delay(3000)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        // 本来はここにPendingIntentを入れて、タップしたらアプリが開くようにすると親切
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Band Analyzer 監視中")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // アイコンは適宜変更可
            .setOnlyAlertOnce(true) // 更新のたびに音が鳴らないようにする
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Monitor",
                NotificationManager.IMPORTANCE_LOW // 音を鳴らさない設定
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "band_monitor_channel"
        private const val NOTIF_ID = 999
    }
}