package com.custom.auraclock.hardware

import android.view.KeyEvent

class HardwareKeyHandler(private val actionListener: ActionListener) {

    interface ActionListener {
        fun onSnoozeTriggered()
        fun onStopAlarmTriggered()
        fun onPeekTriggered()
        fun onAdjustBrightness(delta: Float)
        fun onToggleBedsideLight()
    }

    enum class UIContext {
        ALARM_RINGING,
        NIGHT_ACTIVE,
        DAY_OR_PEEK
    }

    fun handleKeyEvent(event: KeyEvent, currentContext: UIContext): Boolean {
        // Intercetta solo alla pressione iniziale (ACTION_DOWN)
        if (event.action != KeyEvent.ACTION_DOWN) {
            return isRecognizedKey(event.keyCode)
        }

        val keyCode = event.keyCode

        when (currentContext) {
            UIContext.ALARM_RINGING -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        actionListener.onSnoozeTriggered()
                        return true
                    }
                    KeyEvent.KEYCODE_MUTE,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    91, 131, 164 -> {
                        actionListener.onStopAlarmTriggered()
                        return true
                    }
                }
            }

            UIContext.NIGHT_ACTIVE -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        actionListener.onPeekTriggered()
                        return true
                    }
                    KeyEvent.KEYCODE_MUTE,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    91, 131, 164 -> {
                        actionListener.onToggleBedsideLight()
                        return true
                    }
                }
            }

            UIContext.DAY_OR_PEEK -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        actionListener.onAdjustBrightness(+0.15f)
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        actionListener.onAdjustBrightness(-0.15f)
                        return true
                    }
                    KeyEvent.KEYCODE_MUTE,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    91, 131, 164 -> {
                        actionListener.onToggleBedsideLight()
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
