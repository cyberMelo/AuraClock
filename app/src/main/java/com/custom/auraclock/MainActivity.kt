package com.custom.auraclock

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.custom.auraclock.config.AppConfig
import com.custom.auraclock.databinding.ActivityMainBinding
import com.custom.auraclock.hardware.HardwareKeyHandler
import com.custom.auraclock.hardware.LightSensorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appConfig: AppConfig

    private var coreService: AuraClockCoreService? = null
    private var isServiceBound = false

    private lateinit var lightSensorController: LightSensorController
    private lateinit var hardwareKeyHandler: HardwareKeyHandler

    private val handler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    // Gesto segreto: 5 tap in 2.5 secondi
    private var secretTapCount = 0
    private var lastTapTimestamp = 0L

    // Stato UI
    private var isAlarmRinging = false
    private var isNightMode = false
    private var isPeekActive = false
    private var isSettingsOpen = false
    private var isHaReachable = true

    // Anti-burn-in Pixel Shift
    private var pixelShiftIndex = 0
    private val pixelShiftOffsets = arrayOf(
        Pair(0f, 0f), Pair(5f, 4f), Pair(-5f, 3f), Pair(4f, -5f), Pair(-4f, -4f)
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AuraClockCoreService.LocalBinder
            coreService = localBinder?.getService()
            isServiceBound = true
            Log.i(TAG, "Service connesso a MainActivity")

            setupServiceListener()
            updateUiFromCache()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            coreService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appConfig = AppConfig(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setImmersiveStickyMode()

        hardwareKeyHandler = HardwareKeyHandler(
            onSnooze = { onSnoozeTriggered() },
            onStopAlarm = { onStopAlarmTriggered() },
            onPeek = { onPeekTriggered() },
            onAdjustBrightness = { delta -> onAdjustBrightness(delta) },
            onToggleBedsideLight = { onToggleBedsideLight() }
        )

        lightSensorController = LightSensorController(
            context = this,
            appConfig = appConfig,
            onModeChanged = { mode -> onModeChanged(mode) },
            onBrightnessChanged = { b -> onScreenBrightnessChanged(b) },
            onLuxUpdated = { lux -> onLuxUpdated(lux) }
        )

        setupSecretTapTarget()
        setupSettingsOverlay()
        setupAlarmOverlay()
        setupWebView()
        startPixelShiftLoop()

        val serviceIntent = Intent(this, AuraClockCoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        handleIntent(intent)

        if (!appConfig.isConfigured()) {
            openSettingsOverlay()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ALARM_TRIGGER, false) == true) {
            triggerAlarmRingingUi()
        }
    }

    override fun onResume() {
        super.onResume()
        setImmersiveStickyMode()
        lightSensorController.start()
    }

    override fun onPause() {
        super.onPause()
        lightSensorController.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        binding.webViewLivingHorizon.destroy()
    }

    private fun setImmersiveStickyMode() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setImmersiveStickyMode()
        }
    }

    // =========================================================================
    // LAYER 1: WebView & Silent Auto-Login
    // =========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webViewLivingHorizon

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setBackgroundColor(Color.BLACK)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(TAG, "WebView onPageFinished: $url")

                val baseUrl = appConfig.haBaseUrl
                val token = appConfig.haAccessToken
                val dashboardPath = appConfig.haDashboardPath

                if (token.isNotBlank() && url != null && url.startsWith(baseUrl)) {
                    if (!url.contains(dashboardPath)) {
                        injectHaTokens(webView, baseUrl, token, dashboardPath)
                    } else {
                        isHaReachable = true
                        binding.tvNightOfflineStatus.visibility = View.GONE
                        if (!isNightMode && !isAlarmRinging && !isSettingsOpen) {
                            webView.visibility = View.VISIBLE
                        }
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.w(TAG, "Errore connessione WebView: ${error?.description}")
                    handleHaOffline()
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 500) {
                    Log.w(TAG, "Errore HTTP WebView: ${errorResponse?.statusCode}")
                    handleHaOffline()
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                Log.e(TAG, "RenderProcessGone in WebView, riavvio...")
                handleHaOffline()
                view?.destroy()
                setupWebView()
                loadHaDashboard()
                return true
            }
        }

        loadHaDashboard()
    }

    private fun loadHaDashboard() {
        val baseUrl = appConfig.haBaseUrl.trimEnd('/')
        val token = appConfig.haAccessToken
        val rawPath = appConfig.haDashboardPath.trim()
        val dashboardPath = if (rawPath.startsWith("/")) rawPath else "/$rawPath"

        if (baseUrl.isNotBlank() && token.isNotBlank()) {
            val targetUrl = "$baseUrl$dashboardPath"
            Log.i(TAG, "Caricamento URL dashboard: $targetUrl")
            binding.webViewLivingHorizon.loadUrl(targetUrl)
        }
    }

    private fun injectHaTokens(webView: WebView, baseUrl: String, token: String, dashboardPath: String) {
        val js = """
            javascript:(function() {
                try {
                    const tokenObj = {
                        hassUrl: "$baseUrl",
                        access_token: "$token",
                        token_type: "Bearer",
                        expires_in: 315360000,
                        expires: 9999999999999
                    };
                    localStorage.setItem("hassTokens", JSON.stringify(tokenObj));
                    window.location.replace("$dashboardPath");
                } catch(e) {
                    console.error("Token injection error: " + e);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun handleHaOffline() {
        isHaReachable = false
        binding.webViewLivingHorizon.visibility = View.INVISIBLE
        binding.layerNightOverlay.visibility = View.VISIBLE
        binding.tvNightOfflineStatus.visibility = View.VISIBLE
    }

    // =========================================================================
    // LAYER 2: Modalità Notte & Comfort Visivo Ambra
    // =========================================================================

    private fun onModeChanged(newMode: LightSensorController.Mode) {
        runOnUiThread {
            when (newMode) {
                LightSensorController.Mode.NIGHT_MODE -> {
                    isNightMode = true
                    applyNightModeUi(true)
                }
                LightSensorController.Mode.DAY_MODE -> {
                    isNightMode = false
                    applyNightModeUi(false)
                }
            }
        }
    }

    private fun applyNightModeUi(night: Boolean) {
        if (isAlarmRinging || isSettingsOpen) return

        if (night) {
            binding.layerNightOverlay.alpha = 0f
            binding.layerNightOverlay.visibility = View.VISIBLE
            binding.layerNightOverlay.animate().alpha(1f).setDuration(500).start()
            binding.webViewLivingHorizon.visibility = View.INVISIBLE
            setWindowBrightness(appConfig.nightBrightness)
        } else {
            if (isHaReachable) {
                binding.webViewLivingHorizon.visibility = View.VISIBLE
            }
            binding.layerNightOverlay.animate().alpha(0f).setDuration(400).withEndAction {
                binding.layerNightOverlay.visibility = View.GONE
                removeWarmFilterFromWebView()
            }.start()
        }
    }

    private fun onScreenBrightnessChanged(brightness: Float) {
        runOnUiThread {
            if (!isNightMode && !isPeekActive) {
                setWindowBrightness(brightness)
            }
            coreService?.updateCurrentLux(lightSensorController.getCurrentLux())
        }
    }

    private fun onLuxUpdated(lux: Float) {
        coreService?.updateCurrentLux(lux)
    }

    private fun setWindowBrightness(value: Float) {
        val clamped = value.coerceIn(0.05f, 1.0f)
        val lp = window.attributes
        lp.screenBrightness = clamped
        window.attributes = lp
    }

    // --- Tap-to-Peek Notturno (15s a tonalità calda) ---

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            if (isNightMode && !isAlarmRinging && !isSettingsOpen) {
                activateTapToPeek()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun activateTapToPeek() {
        isPeekActive = true
        Log.i(TAG, "Attivato Tap-to-Peek notturno a tonalità calda")

        applyWarmFilterToWebView()
        setWindowBrightness(appConfig.nightPeekBrightness)

        binding.layerNightOverlay.visibility = View.GONE
        if (isHaReachable) {
            binding.webViewLivingHorizon.visibility = View.VISIBLE
        }

        handler.removeCallbacks(peekTimeoutRunnable)
        handler.postDelayed(peekTimeoutRunnable, appConfig.nightPeekDurationSec * 1000L)
    }

    private val peekTimeoutRunnable = Runnable {
        if (isNightMode && !isAlarmRinging && !isSettingsOpen) {
            Log.i(TAG, "Timeout Tap-to-Peek: ritorno a Modalità Notte Ambra")
            isPeekActive = false
            removeWarmFilterFromWebView()
            binding.webViewLivingHorizon.visibility = View.INVISIBLE
            binding.layerNightOverlay.visibility = View.VISIBLE
            setWindowBrightness(appConfig.nightBrightness)
        }
    }

    private fun applyWarmFilterToWebView() {
        val js = """
            javascript:(function() {
                document.body.style.filter = "sepia(45%) hue-rotate(-15deg) brightness(85%)";
            })();
        """.trimIndent()
        binding.webViewLivingHorizon.evaluateJavascript(js, null)
    }

    private fun removeWarmFilterFromWebView() {
        val js = """
            javascript:(function() {
                document.body.style.filter = "none";
            })();
        """.trimIndent()
        binding.webViewLivingHorizon.evaluateJavascript(js, null)
    }

    // --- Alba Artificiale (Sunrise Pre-Sveglia) ---

    private var sunriseJob: Job? = null

    private fun triggerSunriseRamp() {
        runOnUiThread {
            Log.i(TAG, "Avvio Alba Artificiale progressiva (Sunrise)")
            stopSunriseRamp()

            val durationMin = appConfig.sunriseDurationMin
            if (durationMin <= 0) return@runOnUiThread

            val totalDurationMs = durationMin * 60 * 1000L
            val startTime = System.currentTimeMillis()

            sunriseJob = mainScope.launch {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / totalDurationMs).coerceIn(0f, 1f)

                    val startBri = appConfig.nightBrightness
                    val currentBrightness = startBri + progress * (0.80f - startBri)
                    setWindowBrightness(currentBrightness)

                    val blendedColor = androidx.core.graphics.ColorUtils.blendARGB(
                        Color.BLACK,
                        Color.parseColor("#78350F"),
                        progress
                    )
                    binding.layerNightOverlay.setBackgroundColor(blendedColor)

                    if (progress >= 1f) break
                    delay(3000L)
                }
            }
        }
    }

    private fun stopSunriseRamp() {
        sunriseJob?.cancel()
        sunriseJob = null
        binding.layerNightOverlay.setBackgroundColor(Color.BLACK)
    }

    // --- Anti-burn-in Pixel Shift ---

    private fun startPixelShiftLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isNightMode) {
                    pixelShiftIndex = (pixelShiftIndex + 1) % pixelShiftOffsets.size
                    val offset = pixelShiftOffsets[pixelShiftIndex]
                    binding.nightContentContainer.translationX = offset.first
                    binding.nightContentContainer.translationY = offset.second
                }
                handler.postDelayed(this, 5 * 60 * 1000L)
            }
        }, 5 * 60 * 1000L)
    }

    // =========================================================================
    // LAYER 3: Allarme Attivo (Snooze & Stop)
    // =========================================================================

    private fun setupAlarmOverlay() {
        binding.btnAlarmSnooze.setOnClickListener {
            onSnoozeTriggered()
        }
        binding.btnAlarmStop.setOnClickListener {
            onStopAlarmTriggered()
        }
    }

    private fun triggerAlarmRingingUi() {
        runOnUiThread {
            isAlarmRinging = true
            stopSunriseRamp()
            setWindowBrightness(0.90f)

            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvAlarmRingingTime.text = sdf.format(Calendar.getInstance().time)

            val extTemp = appConfig.extTemp
            binding.tvAlarmExtTemp.text = if (extTemp.contains("🌡️")) extTemp else "🌡️ $extTemp"

            binding.layerAlarmRinging.visibility = View.VISIBLE
            binding.layerNightOverlay.visibility = View.GONE
            binding.webViewLivingHorizon.visibility = View.GONE
        }
    }

    private fun onSnoozeTriggered() {
        runOnUiThread {
            Log.i(TAG, "Azione SNOOZE sveglia")
            isAlarmRinging = false
            stopSunriseRamp()
            binding.layerAlarmRinging.visibility = View.GONE

            val stopIntent = Intent(this, AuraClockCoreService::class.java).apply {
                action = AuraClockCoreService.ACTION_STOP_ALARM_AUDIO
            }
            startService(stopIntent)

            coreService?.alarmManager?.scheduleSnooze(appConfig.alarmSnoozeMinutes)
            updateAlarmBadge()

            applyNightModeUi(isNightMode)
            Toast.makeText(this, "Snooze attivato: +${appConfig.alarmSnoozeMinutes} min", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onStopAlarmTriggered() {
        runOnUiThread {
            Log.i(TAG, "Azione STOP sveglia")
            isAlarmRinging = false
            stopSunriseRamp()
            binding.layerAlarmRinging.visibility = View.GONE

            val stopIntent = Intent(this, AuraClockCoreService::class.java).apply {
                action = AuraClockCoreService.ACTION_STOP_ALARM_AUDIO
            }
            startService(stopIntent)

            coreService?.alarmManager?.syncAlarmFromHa(true, appConfig.entityAlarmTime)
            coreService?.wsClient?.notifyAlarmStopped()
            updateAlarmBadge()

            applyNightModeUi(isNightMode)
            Toast.makeText(this, "Sveglia terminata", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPeekTriggered() {
        runOnUiThread {
            activateTapToPeek()
        }
    }

    private fun onAdjustBrightness(delta: Float) {
        runOnUiThread {
            val lp = window.attributes
            val current = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
            val newBri = (current + delta).coerceIn(0.10f, 1.0f)
            setWindowBrightness(newBri)
            Toast.makeText(this, "Luminosità: ${(newBri * 100).toInt()}%", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onToggleBedsideLight() {
        coreService?.wsClient?.toggleBedsideLight()
    }

    // =========================================================================
    // LAYER 4: Settings & Setup Overlay
    // =========================================================================

    private fun setupSecretTapTarget() {
        binding.viewSecretTapTarget.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTimestamp < 2500L) {
                secretTapCount++
                if (secretTapCount >= 5) {
                    secretTapCount = 0
                    openSettingsOverlay()
                }
            } else {
                secretTapCount = 1
            }
            lastTapTimestamp = now
        }
    }

    private fun setupSettingsOverlay() {
        binding.btnSaveSettings.setOnClickListener {
            val newUrl = binding.etHaUrl.text.toString().trim()
            val newToken = binding.etHaToken.text.toString().trim()

            if (newUrl.isNotBlank()) appConfig.haBaseUrl = newUrl
            if (newToken.isNotBlank()) appConfig.haAccessToken = newToken

            coreService?.reloadConfiguration()
            loadHaDashboard()
            closeSettingsOverlay()
            Toast.makeText(this, "Configurazione salvata!", Toast.LENGTH_SHORT).show()
        }

        binding.btnReloadWebview.setOnClickListener {
            loadHaDashboard()
            closeSettingsOverlay()
        }

        binding.btnOpenWifiSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Impossibile aprire impostazioni Wi-Fi", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCloseSettings.setOnClickListener {
            closeSettingsOverlay()
        }
    }

    private fun openSettingsOverlay() {
        isSettingsOpen = true
        binding.etHaUrl.setText(appConfig.haBaseUrl)
        binding.etHaToken.setText(appConfig.haAccessToken)

        val ip = coreService?.getDeviceIp() ?: "0.0.0.0"
        binding.tvSettingsWebServerInfo.text = "IP Dispositivo: $ip"

        val lux = lightSensorController.getCurrentLux()
        val wsStatus = coreService?.wsConnectionStatus ?: "Sconosciuto"
        val alarmStr = coreService?.getNextAlarmFormatted() ?: "--"
        binding.tvSettingsDiagnostic.text = "IP: $ip • Lux: ${"%.1f".format(lux)} • WS: $wsStatus • Prossima Sveglia: $alarmStr"

        binding.layerSettings.visibility = View.VISIBLE
    }

    private fun closeSettingsOverlay() {
        isSettingsOpen = false
        binding.layerSettings.visibility = View.GONE
        setImmersiveStickyMode()
    }

    // =========================================================================
    // Mappatura Tasti Fisici e Dispatch KeyEvent
    // =========================================================================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val currentContext = when {
            isAlarmRinging -> HardwareKeyHandler.UIContext.ALARM_RINGING
            isNightMode && !isPeekActive -> HardwareKeyHandler.UIContext.NIGHT_ACTIVE
            else -> HardwareKeyHandler.UIContext.DAY_OR_PEEK
        }

        if (hardwareKeyHandler.handleKeyEvent(event, currentContext)) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    // =========================================================================
    // Core Service Communication
    // =========================================================================

    private fun setupServiceListener() {
        coreService?.let { service ->
            service.onWebSocketStatus = { status ->
                runOnUiThread { binding.tvSettingsDiagnostic.text = "WS: $status" }
            }
            service.onExternalTempUpdated = { temp ->
                runOnUiThread { binding.tvNightExternalTemp.text = temp }
            }
            service.onAlarmStateUpdated = { _, _ ->
                runOnUiThread { updateAlarmBadge() }
            }
            service.onSunriseTriggered = {
                triggerSunriseRamp()
            }
            service.onHaReconnected = {
                runOnUiThread {
                    if (!isHaReachable) {
                        Log.i(TAG, "HA ricollegato via WebSocket, ricarico la dashboard...")
                        isHaReachable = true
                        loadHaDashboard()
                    }
                }
            }
        }
    }

    private fun updateUiFromCache() {
        binding.tvNightExternalTemp.text = appConfig.extTemp
        updateAlarmBadge()
    }

    private fun updateAlarmBadge() {
        val nextAlarmFormatted = coreService?.alarmManager?.getNextAlarmFormatted() ?: "Disattivata"
        if (nextAlarmFormatted == "Disattivata") {
            binding.tvNightAlarmBadge.visibility = View.GONE
        } else {
            binding.tvNightAlarmBadge.visibility = View.VISIBLE
            binding.tvNightAlarmBadge.text = "🔔 $nextAlarmFormatted"
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_ALARM_TRIGGER = "extra_alarm_trigger"
    }
}
