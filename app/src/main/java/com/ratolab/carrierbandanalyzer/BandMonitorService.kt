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
        // サービス起動時の初期通知（まずは「スキャン中」と出す）
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notif_scanning)))

        // 監視ループ開始
        serviceScope.launch {
            while (isActive) {
                // 1. バンド取得
                val nowBands = analyzer.scanNowBands() // これで履歴保存も行われる

                // 2. 通知の文字を作る
                // bandsが空なら「スキャン中...」、あれば「接続中: B1, B3」のように整形
                val contentText = if (nowBands.isEmpty()) {
                    getString(R.string.notif_scanning)
                } else {
                    val bandStr = nowBands.sorted().joinToString(", ")
                    getString(R.string.notif_connected, bandStr)
                }

                // 3. 通知を更新
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
        // 通知をタップしたときにアプリ（MainActivity）を開くためのIntent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title)) // "バンド解析を実行中" / "Band Analyzer is active"
            .setContentText(contentText)                      // "接続中: B1, B3" など
            .setSmallIcon(R.drawable.ic_launcher_foreground) // アイコンは既存のものを使用
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // 毎回ピロンと鳴らさない
            .setOngoing(true)       // 消せない通知にする
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name), // ユーザーの設定画面に見える名前
                NotificationManager.IMPORTANCE_LOW      // 音を鳴らさない設定
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