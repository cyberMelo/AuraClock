package com.custom.auraclock.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

class AlarmAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var fadeRunnable: Runnable? = null

    fun startAlarmWithFadeIn(maxVolumePct: Int = 85, fadeDurationMs: Long = 30000L) {
        stop()

        try {
            val afd = context.assets.openFd("gentle_alarm.mp3")
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                // Inizia con volume al 5%
                setVolume(0.05f, 0.05f)
                prepare()
                start()
            }
            isPlaying = true
            startVolumeFadeIn(maxVolumePct.coerceIn(10, 100) / 100f, fadeDurationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Errore avvio suoneria sveglia: ${e.message}", e)
        }
    }

    private fun startVolumeFadeIn(targetVolume: Float, durationMs: Long) {
        val steps = 30
        val stepInterval = durationMs / steps
        val startVolume = 0.05f
        val volumeDelta = (targetVolume - startVolume) / steps

        var currentStep = 0

        fadeRunnable = object : Runnable {
            override fun run() {
                if (!isPlaying || mediaPlayer == null) return
                currentStep++
                val currentVol = (startVolume + (volumeDelta * currentStep)).coerceAtMost(targetVolume)
                try {
                    mediaPlayer?.setVolume(currentVol, currentVol)
                } catch (e: Exception) {
                    Log.w(TAG, "Errore impostazione volume fade: ${e.message}")
                }

                if (currentStep < steps && isPlaying) {
                    handler.postDelayed(this, stepInterval)
                }
            }
        }
        handler.postDelayed(fadeRunnable!!, stepInterval)
    }

    fun stop() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
        isPlaying = false

        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            } catch (e: Exception) {
                Log.w(TAG, "Errore rilascio MediaPlayer: ${e.message}")
            }
        }
        mediaPlayer = null
    }

    fun isRinging(): Boolean = isPlaying

    companion object {
        private const val TAG = "AlarmAudioPlayer"
    }
}
