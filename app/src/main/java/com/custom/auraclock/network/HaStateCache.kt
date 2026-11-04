package com.custom.auraclock.network

import android.content.Context
import android.content.SharedPreferences

class HaStateCache(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)

    var extTemp: String
        get() = prefs.getString(KEY_EXT_TEMP, "--") ?: "--"
        set(value) = prefs.edit().putString(KEY_EXT_TEMP, value).apply()

    var alarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALARM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_ENABLED, value).apply()

    var alarmTime: String
        get() = prefs.getString(KEY_ALARM_TIME, "07:30:00") ?: "07:30:00"
        set(value) = prefs.edit().putString(KEY_ALARM_TIME, value).apply()

    var weatherState: String
        get() = prefs.getString(KEY_WEATHER_STATE, "sunny") ?: "sunny"
        set(value) = prefs.edit().putString(KEY_WEATHER_STATE, value).apply()

    var isBedsideLightOn: Boolean
        get() = prefs.getBoolean(KEY_BEDSIDE_LIGHT_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_BEDSIDE_LIGHT_ON, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TS, value).apply()

    companion object {
        private const val CACHE_NAME = "ha_state_cache"

        private const val KEY_EXT_TEMP = "cache_ext_temp"
        private const val KEY_ALARM_ENABLED = "cache_alarm_enabled"
        private const val KEY_ALARM_TIME = "cache_alarm_time"
        private const val KEY_WEATHER_STATE = "cache_weather_state"
        private const val KEY_BEDSIDE_LIGHT_ON = "cache_bedside_light_on"
        private const val KEY_LAST_SYNC_TS = "cache_last_sync_ts"
    }
}
