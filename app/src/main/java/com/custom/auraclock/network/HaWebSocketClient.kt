package com.custom.auraclock.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.custom.auraclock.config.AppConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HaWebSocketClient(
    private val appConfig: AppConfig,
    private val stateCache: HaStateCache,
    private val listener: HaEventListener
) {

    interface HaEventListener {
        fun onConnectionStatusChanged(status: String)
        fun onAlarmStateChanged(enabled: Boolean, timeStr: String)
        fun onExternalTempChanged(tempStr: String)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val msgIdCounter = AtomicInteger(10)
    private val handler = Handler(Looper.getMainLooper())

    private var isConnected = false
    private var isClosedManually = false
    private var reconnectAttempt = 0
    private var pingRunnable: Runnable? = null

    fun connect() {
        isClosedManually = false
        val token = appConfig.haAccessToken
        val baseUrl = appConfig.haBaseUrl

        if (token.isBlank() || baseUrl.isBlank()) {
            listener.onConnectionStatusChanged("Configurazione mancante")
            return
        }

        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/api/websocket"

        listener.onConnectionStatusChanged("Connessione in corso...")
        Log.i(TAG, "Connessione WS verso: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Socket aperto, in attesa di auth_required")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Socket in chiusura: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                listener.onConnectionStatusChanged("Disconnesso")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Errore socket: ${t.message}")
                isConnected = false
                listener.onConnectionStatusChanged("Errore di rete")
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        isClosedManually = true
        cancelPing()
        try {
            webSocket?.close(1000, "App closing")
        } catch (e: Exception) {
            Log.w(TAG, "Errore chiusura socket: ${e.message}")
        }
        webSocket = null
        isConnected = false
    }

    private fun handleMessage(socket: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")

            when (type) {
                "auth_required" -> {
                    // Invia token
                    val authMsg = JSONObject().apply {
                        put("type", "auth")
                        put("access_token", appConfig.haAccessToken)
                    }
                    socket.send(authMsg.toString())
                }

                "auth_ok" -> {
                    Log.i(TAG, "Autenticazione HA riuscita!")
                    isConnected = true
                    reconnectAttempt = 0
                    handler.post { listener.onConnectionStatusChanged("Connesso") }

                    // 1. Fetch iniziale degli stati
                    val getStates = JSONObject().apply {
                        put("id", 1)
                        put("type", "get_states")
                    }
                    socket.send(getStates.toString())

                    // 2. Sottoscrizione eventi di cambio stato
                    val subscribe = JSONObject().apply {
                        put("id", 2)
                        put("type", "subscribe_events")
                        put("event_type", "state_changed")
                    }
                    socket.send(subscribe.toString())

                    startPingLoop()
                }

                "auth_invalid" -> {
                    Log.e(TAG, "Autenticazione HA fallita! Verificare il token.")
                    isConnected = false
                    handler.post { listener.onConnectionStatusChanged("Token non valido") }
                }

                "result" -> {
                    val id = json.optInt("id")
                    if (id == 1 && json.optBoolean("success", false)) {
                        parseInitialStates(json.optJSONArray("result"))
                    }
                }

                "event" -> {
                    val eventObj = json.optJSONObject("event")
                    if (eventObj?.optString("event_type") == "state_changed") {
                        val dataObj = eventObj.optJSONObject("data")
                        val entityId = dataObj?.optString("entity_id") ?: ""
                        val newState = dataObj?.optJSONObject("new_state")
                        if (newState != null) {
                            processEntityUpdate(entityId, newState)
                        }
                    }
                }

                "pong" -> {
                    // Risposta ping ricevuta
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore parsing messaggio WS: ${e.message}")
        }
    }

    private fun parseInitialStates(results: org.json.JSONArray?) {
        if (results == null) return
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val entityId = item.optString("entity_id")
            processEntityUpdate(entityId, item)
        }
    }

    private fun processEntityUpdate(entityId: String, stateObj: JSONObject) {
        val state = stateObj.optString("state")

        when (entityId) {
            appConfig.entityExtTemp -> {
                val formattedTemp = try {
                    "%.1f".format(state.toDouble()) + "°C"
                } catch (e: Exception) {
                    "$state°C"
                }
                stateCache.extTemp = formattedTemp
                stateCache.lastSyncTimestamp = System.currentTimeMillis()
                handler.post { listener.onExternalTempChanged(formattedTemp) }
            }

            appConfig.entityAlarmEnabled -> {
                val isEnabled = state.equals("on", ignoreCase = true)
                stateCache.alarmEnabled = isEnabled
                handler.post { listener.onAlarmStateChanged(isEnabled, stateCache.alarmTime) }
            }

            appConfig.entityAlarmTime -> {
                stateCache.alarmTime = state
                handler.post { listener.onAlarmStateChanged(stateCache.alarmEnabled, state) }
            }

            appConfig.entityWeather -> {
                stateCache.weatherState = state
            }

            appConfig.entityBedsideLight -> {
                stateCache.isBedsideLightOn = state.equals("on", ignoreCase = true)
            }
        }
    }

    fun toggleBedsideLight() {
        if (!isConnected) return
        try {
            val id = msgIdCounter.incrementAndGet()
            val msg = JSONObject().apply {
                put("id", id)
                put("type", "call_service")
                put("domain", "light")
                put("service", "toggle")
                put("target", JSONObject().put("entity_id", appConfig.entityBedsideLight))
            }
            webSocket?.send(msg.toString())
            Log.i(TAG, "Inviato toggle bedside light: ${appConfig.entityBedsideLight}")
        } catch (e: Exception) {
            Log.e(TAG, "Errore invio comando light.toggle: ${e.message}")
        }
    }

    fun notifyAlarmStopped() {
        if (!isConnected) return
        try {
            val id = msgIdCounter.incrementAndGet()
            val msg = JSONObject().apply {
                put("id", id)
                put("type", "fire_event")
                put("event_type", "miclock_alarm_stopped")
                put("event_data", JSONObject().put("device", "AuraClock"))
            }
            webSocket?.send(msg.toString())
            Log.i(TAG, "Inviato evento miclock_alarm_stopped ad HA")
        } catch (e: Exception) {
            Log.e(TAG, "Errore invio evento miclock_alarm_stopped: ${e.message}")
        }
    }

    private fun startPingLoop() {
        cancelPing()
        pingRunnable = object : Runnable {
            override fun run() {
                if (isConnected && webSocket != null) {
                    try {
                        val pingMsg = JSONObject().apply {
                            put("id", msgIdCounter.incrementAndGet())
                            put("type", "ping")
                        }
                        webSocket?.send(pingMsg.toString())
                    } catch (e: Exception) {
                        Log.w(TAG, "Errore invio ping: ${e.message}")
                    }
                    handler.postDelayed(this, 30000L)
                }
            }
        }
        handler.postDelayed(pingRunnable!!, 30000L)
    }

    private fun cancelPing() {
        pingRunnable?.let { handler.removeCallbacks(it) }
        pingRunnable = null
    }

    private fun scheduleReconnect() {
        if (isClosedManually) return
        cancelPing()

        reconnectAttempt++
        val delayMs = when (reconnectAttempt) {
            1 -> 2000L
            2 -> 5000L
            else -> 15000L
        }

        Log.i(TAG, "Pianificata riconnessione WS tra ${delayMs}ms (tentativo #$reconnectAttempt)")
        handler.postDelayed({
            if (!isConnected && !isClosedManually) {
                connect()
            }
        }, delayMs)
    }

    companion object {
        private const val TAG = "HaWebSocketClient"
    }
}
