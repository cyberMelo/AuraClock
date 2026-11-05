package com.custom.auraclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import com.custom.auraclock.alarm.AlarmAudioPlayer
import com.custom.auraclock.alarm.ReliableAlarmManager
import com.custom.auraclock.config.AppConfig
import com.custom.auraclock.network.HaWebSocketClient

class AuraClockCoreService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): AuraClockCoreService = this@AuraClockCoreService
    }

    private val binder = LocalBinder()

    lateinit var appConfig: AppConfig
        private set
    lateinit var alarmManager: ReliableAlarmManager
        private set
    lateinit var audioPlayer: AlarmAudioPlayer
        private set
    lateinit var wsClient: HaWebSocketClient
        private set

    var currentLuxValue: Float = 50.0f
        private set
    var wsConnectionStatus: String = "Inizializzazione"
        private set

    var onWebSocketStatus: ((String) -> Unit)? = null
    var onExternalTempUpdated: ((String) -> Unit)? = null
    var onAlarmStateUpdated: ((Boolean, String) -> Unit)? = null
    var onSunriseTriggered: (() -> Unit)? = null
    var onHaReconnected: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AuraClockCoreService avviato")

        appConfig = AppConfig(this)
        alarmManager = ReliableAlarmManager(this, appConfig)
        audioPlayer = AlarmAudioPlayer(this)

        startForegroundNotification()

        wsClient = HaWebSocketClient(
            appConfig = appConfig,
            onConnectionStatus = { status ->
                wsConnectionStatus = status
                onWebSocketStatus?.invoke(status)
            },
            onAlarmState = { enabled, timeStr ->
                alarmManager.syncAlarmFromHa(enabled, timeStr)
                onAlarmStateUpdated?.invoke(enabled, timeStr)
            },
            onExternalTemp = { tempStr ->
                onExternalTempUpdated?.invoke(tempStr)
            },
            onHaConnected = {
                onHaReconnected?.invoke()
            }
        )
        wsClient.connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM_AUDIO -> audioPlayer.startAlarmWithFadeIn(appConfig.alarmMaxVolumePct)
            ACTION_STOP_ALARM_AUDIO -> audioPlayer.stop()
            ACTION_START_SUNRISE -> onSunriseTriggered?.invoke()
            ACTION_RELOAD_CONFIG -> reloadConfiguration()
        }
        return START_STICKY
    }

    fun reloadConfiguration() {
        Log.i(TAG, "Ricarica configurazione...")
        wsClient.disconnect()
        wsClient.connect()
        alarmManager.syncAlarmFromHa(appConfig.alarmEnabled, appConfig.alarmTime)
    }

    private fun startForegroundNotification() {
        val channelId = "auraclock_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Aura Clock Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene attivi il sensore luce, la sveglia e la sincronizzazione Home Assistant"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Aura Clock")
            .setContentText("Servizio sveglia e sensori attivo")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    fun updateCurrentLux(lux: Float) {
        currentLuxValue = lux
    }

    fun getNextAlarmFormatted(): String = alarmManager.getNextAlarmFormatted()

    @Suppress("DEPRECATION")
    fun getDeviceIp(): String {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wm.connectionInfo.ipAddress
            if (ipInt == 0) "127.0.0.1" else Formatter.formatIpAddress(ipInt)
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        wsClient.disconnect()
        audioPlayer.stop()
        Log.i(TAG, "AuraClockCoreService terminato")
    }

    companion object {
        private const val TAG = "AuraClockCoreService"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START_ALARM_AUDIO = "com.custom.auraclock.ACTION_START_ALARM_AUDIO"
        const val ACTION_STOP_ALARM_AUDIO = "com.custom.auraclock.ACTION_STOP_ALARM_AUDIO"
        const val ACTION_START_SUNRISE = "com.custom.auraclock.ACTION_START_SUNRISE"
        const val ACTION_RELOAD_CONFIG = "com.custom.auraclock.ACTION_RELOAD_CONFIG"
    }
}
