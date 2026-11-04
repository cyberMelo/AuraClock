package com.custom.auraclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.custom.auraclock.AuraClockCoreService
import com.custom.auraclock.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Ricevuto broadcast sveglia: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AuraClock:AlarmReceiverWakeLock"
        )
        wakeLock.acquire(30000L) // Rilasciato dopo 30s max di sicurezza

        try {
            when (action) {
                ReliableAlarmManager.ACTION_ALARM_TRIGGER -> {
                    // 1. Avvia/Mostra MainActivity
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_ALARM_TRIGGER, true)
                    }
                    context.startActivity(activityIntent)

                    // 2. Avvia la riproduzione audio dal Service
                    val serviceIntent = Intent(context, AuraClockCoreService::class.java).apply {
                        this.action = AuraClockCoreService.ACTION_START_ALARM_AUDIO
                    }
                    context.startService(serviceIntent)
                }

                ReliableAlarmManager.ACTION_SUNRISE_TRIGGER -> {
                    val serviceIntent = Intent(context, AuraClockCoreService::class.java).apply {
                        this.action = AuraClockCoreService.ACTION_START_SUNRISE
                    }
                    context.startService(serviceIntent)
                }
            }
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
