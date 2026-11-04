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
import com.custom.auraclock.alarm.ReliableAlarmManager
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), LightSensorController.ModeChangeListener, HardwareKeyHandler.ActionListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appConfig: AppConfig

    private var coreService: AuraClockCoreService? = null
    private var isServiceBound = false

    private lateinit var lightSensorController: LightSensorController
    private lateinit var hardwareKeyHandler: HardwareKeyHandler

    private val handler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private var healthCheckJob: Job? = null

    // Gesto segreto: 5 tap in 2.5 secondi
    private var secretTapCount = 0
    private var lastTapTimestamp = 0L

    // Stato UI
    private var isAlarmRinging = false
    private var isNightMode = false
    private var isPeekActive = false
    private var isSettingsOpen = false
    private var isHaReachable = true
    private var tokenInjected = false

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

        hardwareKeyHandler = HardwareKeyHandler(this)
        lightSensorController = LightSensorController(this, appConfig, this)

        setupSecretTapTarget()
        setupSettingsOverlay()
        setupAlarmOverlay()
        setupWebView()
        startPixelShiftLoop()

        // Avvia e collega il Service in Foreground
        val serviceIntent = Intent(this, AuraClockCoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        handleIntent(intent)

        // Se primo avvio senza token, apri subito il Layer 4
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
        startHaHealthCheckLoop()
    }

    override fun onPause() {
        super.onPause()
        lightSensorController.stop()
        healthCheckJob?.cancel()
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
                    // Se siamo sulla root o pagina di login HA, inietta hassTokens in localStorage
                    if (!url.contains(dashboardPath)) {
                        injectHaTokens(webView, baseUrl, token, dashboardPath)
                    } else {
                        // Pagina dashboard caricata correttamente
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
        val baseUrl = appConfig.haBaseUrl
        val token = appConfig.haAccessToken
        val dashboardPath = appConfig.haDashboardPath

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
        // Nascondi immediatamente la WebView per evitare errori nativi di Chromium
        binding.webViewLivingHorizon.visibility = View.INVISIBLE
        binding.layerNightOverlay.visibility = View.VISIBLE
        binding.tvNightOfflineStatus.visibility = View.VISIBLE
    }

    private fun startHaHealthCheckLoop() {
        healthCheckJob?.cancel()
        healthCheckJob = mainScope.launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            while (isActive) {
                delay(10000L) // Verifica ogni 10 secondi
                val baseUrl = appConfig.haBaseUrl
                if (baseUrl.isNotBlank() && !isHaReachable) {
                    val reachable = withContext(Dispatchers.IO) {
                        try {
                            val request = Request.Builder()
                                .url("$baseUrl/api/")
                                .head()
                                .build()
                            client.newCall(request).execute().use { it.isSuccessful || it.code == 401 }
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (reachable) {
                        Log.i(TAG, "Home Assistant è tornato ONLINE! Ricarico la WebView...")
                        isHaReachable = true
                        loadHaDashboard()
                    }
                }
            }
        }
    }

    // =========================================================================
    // LAYER 2: Modalità Notte & Comfort Visivo Ambra
    // =========================================================================

    override fun onModeChanged(newMode: LightSensorController.Mode) {
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
            // Entrata in notte profonda: mostra layer 2 ambra
            binding.layerNightOverlay.alpha = 0f
            binding.layerNightOverlay.visibility = View.VISIBLE
            binding.layerNightOverlay.animate().alpha(1f).setDuration(500).start()
            binding.webViewLivingHorizon.visibility = View.INVISIBLE
            setWindowBrightness(0.01f) // 1% minimo hardware
        } else {
            // Ritorno in giorno: dissolvenza layer 2 e ripristino dashboard
            binding.layerNightOverlay.animate().alpha(0f).setDuration(500).withEndAction {
                binding.layerNightOverlay.visibility = View.GONE
                removeWarmFilterFromWebView()
                if (isHaReachable) {
                    binding.webViewLivingHorizon.visibility = View.VISIBLE
                }
            }.start()
        }
    }

    override fun onScreenBrightnessChanged(brightness: Float) {
        runOnUiThread {
            if (!isNightMode && !isPeekActive) {
                setWindowBrightness(brightness)
            }
            coreService?.updateCurrentLux(lightSensorController.getCurrentLux())
        }
    }

    override fun onLuxUpdated(lux: Float) {
        coreService?.updateCurrentLux(lux)
    }

    private fun setWindowBrightness(value: Float) {
        val clamped = value.coerceIn(0.01f, 1.0f)
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

        // 1. Applica filtro caldo anti-blu CSS alla WebView
        applyWarmFilterToWebView()

        // 2. Alza dolcemente la luminosità a nightPeekBrightness (12%)
        setWindowBrightness(appConfig.nightPeekBrightness)

        // 3. Mostra WebView e nascondi Layer 2
        binding.layerNightOverlay.visibility = View.GONE
        if (isHaReachable) {
            binding.webViewLivingHorizon.visibility = View.VISIBLE
        }

        // 4. Timer di inattività
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
            setWindowBrightness(0.01f)
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

    // --- Alba Artificiale (Sunrise) ---

    private fun triggerSunriseRamp() {
        runOnUiThread {
            Log.i(TAG, "Avvio Alba Artificiale (Sunrise)")
            // Rampa progressiva di luminosità da 0.01f a 0.80f su 10 minuti
            setWindowBrightness(0.35f)
            binding.layerNightOverlay.setBackgroundColor(Color.parseColor("#451A03")) // Oro/Ambra scuro
        }
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
                handler.postDelayed(this, 5 * 60 * 1000L) // Ogni 5 minuti
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
            setWindowBrightness(0.90f)

            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvAlarmRingingTime.text = sdf.format(Calendar.getInstance().time)

            binding.layerAlarmRinging.visibility = View.VISIBLE
            binding.layerNightOverlay.visibility = View.GONE
            binding.webViewLivingHorizon.visibility = View.GONE
        }
    }

    override fun onSnoozeTriggered() {
        runOnUiThread {
            Log.i(TAG, "Azione SNOOZE sveglia")
            isAlarmRinging = false
            binding.layerAlarmRinging.visibility = View.GONE

            // Ferma suoneria
            val stopIntent = Intent(this, AuraClockCoreService::class.java).apply {
                action = AuraClockCoreService.ACTION_STOP_ALARM_AUDIO
            }
            startService(stopIntent)

            // Schedula Snooze
            coreService?.alarmManager?.scheduleSnooze(appConfig.alarmSnoozeMinutes)
            updateAlarmBadge()

            // Ripristina UI giorno o notte
            if (isNightMode) {
                applyNightModeUi(true)
            } else {
                applyNightModeUi(false)
            }
            Toast.makeText(this, "Snooze attivato: +${appConfig.alarmSnoozeMinutes} min", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStopAlarmTriggered() {
        runOnUiThread {
            Log.i(TAG, "Azione STOP sveglia")
            isAlarmRinging = false
            binding.layerAlarmRinging.visibility = View.GONE

            // Ferma suoneria
            val stopIntent = Intent(this, AuraClockCoreService::class.java).apply {
                action = AuraClockCoreService.ACTION_STOP_ALARM_AUDIO
            }
            startService(stopIntent)

            // Rischedula per domani e notifica Home Assistant
            coreService?.alarmManager?.syncAlarmFromHa(true, appConfig.entityAlarmTime)
            coreService?.wsClient?.notifyAlarmStopped()
            updateAlarmBadge()

            if (isNightMode) {
                applyNightModeUi(true)
            } else {
                applyNightModeUi(false)
            }
            Toast.makeText(this, "Sveglia terminata", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPeekTriggered() {
        runOnUiThread {
            activateTapToPeek()
        }
    }

    override fun onAdjustBrightness(delta: Float) {
        runOnUiThread {
            val lp = window.attributes
            val current = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
            val newBri = (current + delta).coerceIn(0.10f, 1.0f)
            setWindowBrightness(newBri)
            Toast.makeText(this, "Luminosità: ${(newBri * 100).toInt()}%", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onToggleBedsideLight() {
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
        binding.tvSettingsWebServerInfo.text = "Web Configurator attivo su: http://$ip:8080"

        val lux = lightSensorController.getCurrentLux()
        val wsStatus = coreService?.getWebSocketStatus() ?: "Sconosciuto"
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
        coreService?.serviceListener = object : AuraClockCoreService.CoreServiceListener {
            override fun onWebSocketStatus(status: String) {
                runOnUiThread {
                    binding.tvSettingsDiagnostic.text = "WS: $status"
                }
            }

            override fun onExternalTempUpdated(temp: String) {
                runOnUiThread {
                    binding.tvNightExternalTemp.text = temp
                }
            }

            override fun onAlarmStateUpdated(enabled: Boolean, timeStr: String) {
                runOnUiThread {
                    updateAlarmBadge()
                }
            }

            override fun onSunriseTriggered() {
                triggerSunriseRamp()
            }
        }
    }

    private fun updateUiFromCache() {
        coreService?.stateCache?.let { cache ->
            binding.tvNightExternalTemp.text = cache.extTemp
            updateAlarmBadge()
        }
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
