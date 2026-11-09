package com.custom.auraclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.custom.auraclock.alarm.ReliableAlarmManager
import com.custom.auraclock.config.AppConfig

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i(TAG, "Dispositivo riavviato: ripristino AuraClockCoreService e rischedulazione sveglia")

            val appConfig = AppConfig(context)
            val alarmManager = ReliableAlarmManager(context, appConfig)

            val cachedTs = appConfig.cachedNextAlarmTimestamp
            if (cachedTs > System.currentTimeMillis()) {
                alarmManager.scheduleAlarmAt(cachedTs)
            } else {
                // Calcola orario per la prossima occorrenza
                alarmManager.syncAlarmFromHa(true, appConfig.entityAlarmTime)
            }

            val serviceIntent = Intent(context, AuraClockCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Avvia esplicitamente MainActivity all'accensione del dispositivo
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(activityIntent)

            // Garantisce che ADB su porta 5555 rimanga sempre attivo via Wi-Fi ad ogni boot
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop service.adb.tcp.port 5555 && stop adbd && start adbd"))
            } catch (e: Exception) {
                Log.w(TAG, "Impossibile attivare ADB Wi-Fi: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
