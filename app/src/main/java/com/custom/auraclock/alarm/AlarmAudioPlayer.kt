package com.custom.auraclock.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

class AlarmAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var volumeAnimator: ValueAnimator? = null
    private var isPlaying = false

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
                setVolume(0.05f, 0.05f)
                prepare()
                start()
            }
            isPlaying = true

            val targetVol = maxVolumePct.coerceIn(10, 100) / 100f
            volumeAnimator = ValueAnimator.ofFloat(0.05f, targetVol).apply {
                duration = fadeDurationMs
                addUpdateListener { anim ->
                    val vol = anim.animatedValue as Float
                    mediaPlayer?.setVolume(vol, vol)
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore avvio suoneria sveglia: ${e.message}", e)
        }
    }

    fun stop() {
        volumeAnimator?.cancel()
        volumeAnimator = null
        isPlaying = false

        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
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
