package com.custom.auraclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.custom.auraclock.MainActivity
import com.custom.auraclock.config.AppConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReliableAlarmManager(private val context: Context, private val appConfig: AppConfig) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun syncAlarmFromHa(enabled: Boolean, timeStr: String) {
        if (!enabled) {
            cancelAlarm()
            return
        }

        val targetTimestamp = calculateNextAlarmTimestamp(timeStr)
        if (targetTimestamp > 0L) {
            scheduleAlarmAt(targetTimestamp)
        }
    }

    fun scheduleAlarmAt(triggerTs: Long) {
        appConfig.cachedNextAlarmTimestamp = triggerTs

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pendingAlarm = PendingIntent.getBroadcast(
            context,
            RC_ALARM_TRIGGER,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show MainActivity on clock tap
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingShow = PendingIntent.getActivity(
            context,
            RC_SHOW_INTENT,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(triggerTs, pendingShow)
        alarmManager.setAlarmClock(clockInfo, pendingAlarm)

        val sunriseMin = appConfig.sunriseDurationMin
        if (sunriseMin > 0) {
            val sunriseTs = triggerTs - (sunriseMin * 60 * 1000L)
            if (sunriseTs > System.currentTimeMillis()) {
                scheduleSunriseAt(sunriseTs)
            }
        }

        val sdf = SimpleDateFormat("HH:mm (dd/MM)", Locale.getDefault())
        Log.i(TAG, "Sveglia hardware schedulata per: ${sdf.format(triggerTs)}")
    }

    private fun scheduleSunriseAt(sunriseTs: Long) {
        val sunriseIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SUNRISE_TRIGGER
        }
        val pendingSunrise = PendingIntent.getBroadcast(
            context,
            RC_SUNRISE_TRIGGER,
            sunriseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            sunriseTs,
            pendingSunrise
        )
        Log.i(TAG, "Trigger alba artificiale schedulato alle: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(sunriseTs)}")
    }

    fun scheduleSnooze(minutes: Int = appConfig.alarmSnoozeMinutes) {
        val snoozeTs = System.currentTimeMillis() + (minutes * 60 * 1000L)
        Log.i(TAG, "Snooze attivato per i prossimi $minutes minuti")
        scheduleAlarmAt(snoozeTs)
    }

    fun cancelAlarm() {
        appConfig.cachedNextAlarmTimestamp = 0L

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pendingAlarm = PendingIntent.getBroadcast(
            context,
            RC_ALARM_TRIGGER,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingAlarm)

        val sunriseIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SUNRISE_TRIGGER
        }
        val pendingSunrise = PendingIntent.getBroadcast(
            context,
            RC_SUNRISE_TRIGGER,
            sunriseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingSunrise)

        Log.i(TAG, "Sveglia hardware annullata")
    }

    fun calculateNextAlarmTimestamp(timeStr: String): Long {
        try {
            val parts = timeStr.trim().split(":")
            if (parts.size < 2) return 0L
            val hour = parts[0].toInt()
            val min = parts[1].toInt()
            val sec = if (parts.size >= 3) parts[2].toInt() else 0

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, sec)
                set(Calendar.MILLISECOND, 0)
            }

            // Se l'orario per oggi è già passato, programma per domani
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Errore calcolo timestamp da timeStr '$timeStr': ${e.message}")
            return 0L
        }
    }

    fun getNextAlarmFormatted(): String {
        val ts = appConfig.cachedNextAlarmTimestamp
        if (ts <= 0L || ts < System.currentTimeMillis()) return "Disattivata"
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(ts)
    }

    companion object {
        private const val TAG = "ReliableAlarmManager"

        const val ACTION_ALARM_TRIGGER = "com.custom.auraclock.ACTION_ALARM_TRIGGER"
        const val ACTION_SUNRISE_TRIGGER = "com.custom.auraclock.ACTION_SUNRISE_TRIGGER"

        private const val RC_ALARM_TRIGGER = 1001
        private const val RC_SUNRISE_TRIGGER = 1002
        private const val RC_SHOW_INTENT = 1003
    }
}
