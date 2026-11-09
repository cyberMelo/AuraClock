package com.custom.auraclock.config

import android.content.Context
import android.content.SharedPreferences

class AppConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var haBaseUrl: String
        get() = prefs.getString(KEY_HA_BASE_URL, DEFAULT_HA_BASE_URL)?.trimEnd('/') ?: DEFAULT_HA_BASE_URL
        set(value) = prefs.edit().putString(KEY_HA_BASE_URL, value.trimEnd('/')).apply()

    var haDashboardPath: String
        get() = prefs.getString(KEY_HA_DASHBOARD_PATH, DEFAULT_HA_DASHBOARD_PATH) ?: DEFAULT_HA_DASHBOARD_PATH
        set(value) = prefs.edit().putString(KEY_HA_DASHBOARD_PATH, value).apply()

    var haAccessToken: String
        get() = prefs.getString(KEY_HA_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HA_ACCESS_TOKEN, value.trim()).apply()

    var entityAlarmEnabled: String
        get() = prefs.getString(KEY_ENTITY_ALARM_ENABLED, DEFAULT_ENTITY_ALARM_ENABLED) ?: DEFAULT_ENTITY_ALARM_ENABLED
        set(value) = prefs.edit().putString(KEY_ENTITY_ALARM_ENABLED, value).apply()

    var entityAlarmTime: String
        get() = prefs.getString(KEY_ENTITY_ALARM_TIME, DEFAULT_ENTITY_ALARM_TIME) ?: DEFAULT_ENTITY_ALARM_TIME
        set(value) = prefs.edit().putString(KEY_ENTITY_ALARM_TIME, value).apply()

    var entityExtTemp: String
        get() = prefs.getString(KEY_ENTITY_EXT_TEMP, DEFAULT_ENTITY_EXT_TEMP) ?: DEFAULT_ENTITY_EXT_TEMP
        set(value) = prefs.edit().putString(KEY_ENTITY_EXT_TEMP, value).apply()

    var entityWeather: String
        get() = prefs.getString(KEY_ENTITY_WEATHER, DEFAULT_ENTITY_WEATHER) ?: DEFAULT_ENTITY_WEATHER
        set(value) = prefs.edit().putString(KEY_ENTITY_WEATHER, value).apply()

    var entityBedsideLight: String
        get() = prefs.getString(KEY_ENTITY_BEDSIDE_LIGHT, DEFAULT_ENTITY_BEDSIDE_LIGHT) ?: DEFAULT_ENTITY_BEDSIDE_LIGHT
        set(value) = prefs.edit().putString(KEY_ENTITY_BEDSIDE_LIGHT, value).apply()

    var luxNightEnter: Float
        get() = prefs.getFloat(KEY_LUX_NIGHT_ENTER, DEFAULT_LUX_NIGHT_ENTER)
        set(value) = prefs.edit().putFloat(KEY_LUX_NIGHT_ENTER, value).apply()

    var luxNightExit: Float
        get() = prefs.getFloat(KEY_LUX_NIGHT_EXIT, DEFAULT_LUX_NIGHT_EXIT)
        set(value) = prefs.edit().putFloat(KEY_LUX_NIGHT_EXIT, value).apply()

    var nightPeekDurationSec: Int
        get() = prefs.getInt(KEY_NIGHT_PEEK_DURATION_SEC, DEFAULT_NIGHT_PEEK_DURATION_SEC)
        set(value) = prefs.edit().putInt(KEY_NIGHT_PEEK_DURATION_SEC, value).apply()

    var nightPeekBrightness: Float
        get() = prefs.getFloat(KEY_NIGHT_PEEK_BRIGHTNESS, DEFAULT_NIGHT_PEEK_BRIGHTNESS)
        set(value) = prefs.edit().putFloat(KEY_NIGHT_PEEK_BRIGHTNESS, value).apply()

    var nightBrightness: Float
        get() = prefs.getFloat(KEY_NIGHT_BRIGHTNESS, DEFAULT_NIGHT_BRIGHTNESS)
        set(value) = prefs.edit().putFloat(KEY_NIGHT_BRIGHTNESS, value).apply()

    var alarmSnoozeMinutes: Int
        get() = prefs.getInt(KEY_ALARM_SNOOZE_MINUTES, DEFAULT_ALARM_SNOOZE_MINUTES)
        set(value) = prefs.edit().putInt(KEY_ALARM_SNOOZE_MINUTES, value).apply()

    var alarmMaxVolumePct: Int
        get() = prefs.getInt(KEY_ALARM_MAX_VOLUME_PCT, DEFAULT_ALARM_MAX_VOLUME_PCT)
        set(value) = prefs.edit().putInt(KEY_ALARM_MAX_VOLUME_PCT, value).apply()

    var sunriseDurationMin: Int
        get() = prefs.getInt(KEY_SUNRISE_DURATION_MIN, DEFAULT_SUNRISE_DURATION_MIN)
        set(value) = prefs.edit().putInt(KEY_SUNRISE_DURATION_MIN, value).apply()

    // Cache orario sveglia locale calcolato
    var cachedNextAlarmTimestamp: Long
        get() = prefs.getLong(KEY_CACHED_NEXT_ALARM_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_CACHED_NEXT_ALARM_TS, value).apply()

    // Cache stati Home Assistant
    var extTemp: String
        get() = prefs.getString(KEY_CACHE_EXT_TEMP, "--") ?: "--"
        set(value) = prefs.edit().putString(KEY_CACHE_EXT_TEMP, value).apply()

    var alarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_ALARM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CACHE_ALARM_ENABLED, value).apply()

    var alarmTime: String
        get() = prefs.getString(KEY_CACHE_ALARM_TIME, "07:30:00") ?: "07:30:00"
        set(value) = prefs.edit().putString(KEY_CACHE_ALARM_TIME, value).apply()

    var weatherState: String
        get() = prefs.getString(KEY_CACHE_WEATHER_STATE, "sunny") ?: "sunny"
        set(value) = prefs.edit().putString(KEY_CACHE_WEATHER_STATE, value).apply()

    var isBedsideLightOn: Boolean
        get() = prefs.getBoolean(KEY_CACHE_BEDSIDE_LIGHT_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_CACHE_BEDSIDE_LIGHT_ON, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_CACHE_LAST_SYNC_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_CACHE_LAST_SYNC_TS, value).apply()

    fun isConfigured(): Boolean = haAccessToken.isNotBlank() && haBaseUrl.isNotBlank()

    fun getAllAsMap(): Map<String, Any> {
        return mapOf(
            KEY_HA_BASE_URL to haBaseUrl,
            KEY_HA_DASHBOARD_PATH to haDashboardPath,
            KEY_HA_ACCESS_TOKEN to haAccessToken,
            KEY_ENTITY_ALARM_ENABLED to entityAlarmEnabled,
            KEY_ENTITY_ALARM_TIME to entityAlarmTime,
            KEY_ENTITY_EXT_TEMP to entityExtTemp,
            KEY_ENTITY_WEATHER to entityWeather,
            KEY_ENTITY_BEDSIDE_LIGHT to entityBedsideLight,
            KEY_LUX_NIGHT_ENTER to luxNightEnter,
            KEY_LUX_NIGHT_EXIT to luxNightExit,
            KEY_NIGHT_PEEK_DURATION_SEC to nightPeekDurationSec,
            KEY_NIGHT_PEEK_BRIGHTNESS to nightPeekBrightness,
            KEY_NIGHT_BRIGHTNESS to nightBrightness,
            KEY_ALARM_SNOOZE_MINUTES to alarmSnoozeMinutes,
            KEY_ALARM_MAX_VOLUME_PCT to alarmMaxVolumePct,
            KEY_SUNRISE_DURATION_MIN to sunriseDurationMin
        )
    }

    companion object {
        const val PREFS_NAME = "auraclock_prefs"

        const val KEY_HA_BASE_URL = "ha_base_url"
        const val KEY_HA_DASHBOARD_PATH = "ha_dashboard_path"
        const val KEY_HA_ACCESS_TOKEN = "ha_access_token"
        const val KEY_ENTITY_ALARM_ENABLED = "entity_alarm_enabled"
        const val KEY_ENTITY_ALARM_TIME = "entity_alarm_time"
        const val KEY_ENTITY_EXT_TEMP = "entity_ext_temp"
        const val KEY_ENTITY_WEATHER = "entity_weather"
        const val KEY_ENTITY_BEDSIDE_LIGHT = "entity_bedside_light"
        const val KEY_LUX_NIGHT_ENTER = "lux_night_enter"
        const val KEY_LUX_NIGHT_EXIT = "lux_night_exit"
        const val KEY_NIGHT_PEEK_DURATION_SEC = "night_peek_duration_sec"
        const val KEY_NIGHT_PEEK_BRIGHTNESS = "night_peek_brightness"
        const val KEY_NIGHT_BRIGHTNESS = "night_brightness"
        const val KEY_ALARM_SNOOZE_MINUTES = "alarm_snooze_minutes"
        const val KEY_ALARM_MAX_VOLUME_PCT = "alarm_max_volume_pct"
        const val KEY_SUNRISE_DURATION_MIN = "sunrise_duration_min"
        const val KEY_CACHED_NEXT_ALARM_TS = "cached_next_alarm_ts"
        const val KEY_CACHE_EXT_TEMP = "cache_ext_temp"
        const val KEY_CACHE_ALARM_ENABLED = "cache_alarm_enabled"
        const val KEY_CACHE_ALARM_TIME = "cache_alarm_time"
        const val KEY_CACHE_WEATHER_STATE = "cache_weather_state"
        const val KEY_CACHE_BEDSIDE_LIGHT_ON = "cache_bedside_light_on"
        const val KEY_CACHE_LAST_SYNC_TS = "cache_last_sync_ts"

        const val DEFAULT_HA_BASE_URL = "http://192.168.1.100:8123"
        const val DEFAULT_HA_DASHBOARD_PATH = "/lovelace-comodino/0?kiosk"
        const val DEFAULT_ENTITY_ALARM_ENABLED = "input_boolean.sveglia_comodino_attiva"
        const val DEFAULT_ENTITY_ALARM_TIME = "input_datetime.sveglia_comodino_orario"
        const val DEFAULT_ENTITY_EXT_TEMP = "sensor.temperatura_esterna"
        const val DEFAULT_ENTITY_WEATHER = "weather.casa"
        const val DEFAULT_ENTITY_BEDSIDE_LIGHT = "light.luce_comodino"
        const val DEFAULT_LUX_NIGHT_ENTER = 2.0f
        const val DEFAULT_LUX_NIGHT_EXIT = 8.0f
        const val DEFAULT_NIGHT_PEEK_DURATION_SEC = 15
        const val DEFAULT_NIGHT_PEEK_BRIGHTNESS = 0.22f
        const val DEFAULT_NIGHT_BRIGHTNESS = 0.12f
        const val DEFAULT_ALARM_SNOOZE_MINUTES = 10
        const val DEFAULT_ALARM_MAX_VOLUME_PCT = 85
        const val DEFAULT_SUNRISE_DURATION_MIN = 10
    }
}
