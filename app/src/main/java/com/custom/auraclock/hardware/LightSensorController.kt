package com.custom.auraclock.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.custom.auraclock.config.AppConfig

class LightSensorController(
    context: Context,
    private val appConfig: AppConfig,
    private val listener: ModeChangeListener
) : SensorEventListener {

    enum class Mode {
        DAY_MODE,
        NIGHT_MODE
    }

    interface ModeChangeListener {
        fun onModeChanged(newMode: Mode)
        fun onScreenBrightnessChanged(brightness: Float)
        fun onLuxUpdated(lux: Float)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val handler = Handler(Looper.getMainLooper())

    private var currentMode = Mode.DAY_MODE
    private var currentLux = 50.0f
    private var enterNightRunnable: Runnable? = null
    private var exitNightRunnable: Runnable? = null

    fun start() {
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i(TAG, "Sensore luce registrato con successo")
        } else {
            Log.w(TAG, "Sensore luce (TYPE_LIGHT) non rilevato sul dispositivo!")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        cancelTimers()
    }

    fun getCurrentLux(): Float = currentLux

    fun getCurrentMode(): Mode = currentMode

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LIGHT) return
        val lux = event.values[0]
        currentLux = lux
        listener.onLuxUpdated(lux)

        val enterThreshold = appConfig.luxNightEnter
        val exitThreshold = appConfig.luxNightExit

        if (currentMode == Mode.DAY_MODE) {
            if (lux < enterThreshold) {
                // Avvia o mantiene il timer per entrare in NIGHT_MODE
                if (enterNightRunnable == null) {
                    enterNightRunnable = Runnable {
                        Log.i(TAG, "Trigger Notte: lux < $enterThreshold per 5000ms -> Passo a NIGHT_MODE")
                        currentMode = Mode.NIGHT_MODE
                        listener.onModeChanged(Mode.NIGHT_MODE)
                        listener.onScreenBrightnessChanged(0.01f) // 1% minimo hardware
                        enterNightRunnable = null
                    }
                    handler.postDelayed(enterNightRunnable!!, 5000L)
                }
            } else {
                // Luce tornata sufficiente prima dei 5s: annulla timer transizione notte
                cancelEnterNightTimer()
                // Calcola luminosità diurna dinamica proporzionale (0.25f - 0.90f)
                val targetBrightness = calculateDayBrightness(lux)
                listener.onScreenBrightnessChanged(targetBrightness)
            }
        } else { // NIGHT_MODE
            if (lux > exitThreshold) {
                // Avvia o mantiene il timer per uscire da NIGHT_MODE (8000ms anti-accecamento)
                if (exitNightRunnable == null) {
                    exitNightRunnable = Runnable {
                        Log.i(TAG, "Trigger Giorno: lux > $exitThreshold per 8000ms -> Passo a DAY_MODE")
                        currentMode = Mode.DAY_MODE
                        listener.onModeChanged(Mode.DAY_MODE)
                        listener.onScreenBrightnessChanged(calculateDayBrightness(currentLux))
                        exitNightRunnable = null
                    }
                    handler.postDelayed(exitNightRunnable!!, 8000L)
                }
            } else {
                // Buio ripristinato prima degli 8s: annulla timer transizione giorno
                cancelExitNightTimer()
            }
        }
    }

    private fun calculateDayBrightness(lux: Float): Float {
        // Mappa lux da 10 lux (0.25f) a 300+ lux (0.90f)
        val clampedLux = lux.coerceIn(10.0f, 300.0f)
        val ratio = (clampedLux - 10.0f) / (300.0f - 10.0f)
        return 0.25f + (ratio * (0.90f - 0.25f))
    }

    private fun cancelEnterNightTimer() {
        enterNightRunnable?.let { handler.removeCallbacks(it) }
        enterNightRunnable = null
    }

    private fun cancelExitNightTimer() {
        exitNightRunnable?.let { handler.removeCallbacks(it) }
        exitNightRunnable = null
    }

    private fun cancelTimers() {
        cancelEnterNightTimer()
        cancelExitNightTimer()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "LightSensorController"
    }
}
