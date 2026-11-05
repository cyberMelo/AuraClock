package com.custom.auraclock.hardware

import android.view.KeyEvent

class HardwareKeyHandler(
    var onSnooze: (() -> Unit)? = null,
    var onStopAlarm: (() -> Unit)? = null,
    var onPeek: (() -> Unit)? = null,
    var onAdjustBrightness: ((Float) -> Unit)? = null,
    var onToggleBedsideLight: (() -> Unit)? = null
) {

    enum class UIContext {
        ALARM_RINGING,
        NIGHT_ACTIVE,
        DAY_OR_PEEK
    }

    fun handleKeyEvent(event: KeyEvent, currentContext: UIContext): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return isRecognizedKey(event.keyCode)
        }

        val keyCode = event.keyCode

        when (currentContext) {
            UIContext.ALARM_RINGING -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        onSnooze?.invoke()
                        return true
                    }
                    KeyEvent.KEYCODE_MUTE,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    91, 131, 164 -> {
                        onStopAlarm?.invoke()
                        return true
                    }
                }
            }

            UIContext.NIGHT_ACTIVE -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        onPeek?.invoke()
                        return true
                    }
                    KeyEvent.KEYCODE_MUTE,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    91, 131, 164 -> {
                        onToggleBedsideLight?.invoke()
                        return true
                    }
                }
            }

            UIContext.DAY_OR_PEEK -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        onAdjustBrightness?.invoke(+0.15f)
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        onAdjustBrightness?.invoke(-0.15f)
                        return true
                    }
                    KeyEvent.KEYCODE_MUTE,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    91, 131, 164 -> {
                        onToggleBedsideLight?.invoke()
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun isRecognizedKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_MUTE ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
                keyCode == 91 || keyCode == 131 || keyCode == 164
    }
}
