package com.custom.auraclock.config

import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class WebConfigServer(
    port: Int = 8080,
    private val appConfig: AppConfig,
    private val statusProvider: StatusProvider,
    private val onConfigUpdated: () -> Unit
) : NanoHTTPD(port) {

    interface StatusProvider {
        fun getCurrentLux(): Float
        fun getWebSocketStatus(): String
        fun getNextAlarmFormatted(): String
        fun getDeviceIp(): String
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (method == Method.POST && uri == "/save") {
            return handleSave(session)
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=UTF-8",
            renderHtmlPage()
        )
    }

    private fun handleSave(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
            val params = session.parameters

            params["ha_base_url"]?.firstOrNull()?.let { appConfig.haBaseUrl = it }
            params["ha_dashboard_path"]?.firstOrNull()?.let { appConfig.haDashboardPath = it }
            params["ha_access_token"]?.firstOrNull()?.let { appConfig.haAccessToken = it }
            params["entity_alarm_enabled"]?.firstOrNull()?.let { appConfig.entityAlarmEnabled = it }
            params["entity_alarm_time"]?.firstOrNull()?.let { appConfig.entityAlarmTime = it }
            params["entity_ext_temp"]?.firstOrNull()?.let { appConfig.entityExtTemp = it }
            params["entity_weather"]?.firstOrNull()?.let { appConfig.entityWeather = it }
            params["entity_bedside_light"]?.firstOrNull()?.let { appConfig.entityBedsideLight = it }

            params["lux_night_enter"]?.firstOrNull()?.toFloatOrNull()?.let { appConfig.luxNightEnter = it }
            params["lux_night_exit"]?.firstOrNull()?.toFloatOrNull()?.let { appConfig.luxNightExit = it }
            params["night_peek_duration_sec"]?.firstOrNull()?.toIntOrNull()?.let { appConfig.nightPeekDurationSec = it }
            params["night_peek_brightness"]?.firstOrNull()?.toFloatOrNull()?.let { appConfig.nightPeekBrightness = it }
            params["alarm_snooze_minutes"]?.firstOrNull()?.toIntOrNull()?.let { appConfig.alarmSnoozeMinutes = it }
            params["alarm_max_volume_pct"]?.firstOrNull()?.toIntOrNull()?.let { appConfig.alarmMaxVolumePct = it }
            params["sunrise_duration_min"]?.firstOrNull()?.toIntOrNull()?.let { appConfig.sunriseDurationMin = it }

            onConfigUpdated()

            val successHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Salvato con Successo</title>
                    <meta http-equiv="refresh" content="2;url=/">
                    <style>
                        body { background: #0f172a; color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                        .card { background: #1e293b; padding: 32px 48px; border-radius: 16px; border: 1px solid #10b981; text-align: center; }
                        h2 { color: #10b981; margin-top: 0; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>✓ Configurazione Salvata</h2>
                        <p>Parametri aggiornati e applicati al dispositivo.</p>
                        <p style="color: #94a3b8; font-size: 14px;">Reindirizzamento in corso...</p>
                    </div>
                </body>
                </html>
            """.trimIndent()

            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", successHtml)
        } catch (e: Exception) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Errore nel salvataggio: ${e.message}"
            )
        }
    }

    private fun renderHtmlPage(): String {
        val lux = statusProvider.getCurrentLux()
        val wsStatus = statusProvider.getWebSocketStatus()
        val nextAlarm = statusProvider.getNextAlarmFormatted()
        val deviceIp = statusProvider.getDeviceIp()

        return """
            <!DOCTYPE html>
            <html lang="it">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Aura Clock • Control Panel</title>
                <style>
                    :root {
                        --bg: #090d16;
                        --card: #131c2e;
                        --border: #1e293b;
                        --accent: #38bdf8;
                        --amber: #f59e0b;
                        --success: #10b981;
                        --text: #f8fafc;
                        --muted: #94a3b8;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        background: var(--bg);
                        color: var(--text);
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        padding: 24px 16px;
                    }
                    .container { max-width: 760px; margin: 0 auto; }
                    header { margin-bottom: 24px; text-align: center; }
                    header h1 { font-size: 28px; font-weight: 800; color: #fff; letter-spacing: -0.5px; }
                    header p { color: var(--muted); margin-top: 4px; font-size: 14px; }
                    .status-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
                        gap: 12px;
                        margin-bottom: 24px;
                    }
                    .status-card {
                        background: var(--card);
                        padding: 14px 16px;
                        border-radius: 12px;
                        border: 1px solid var(--border);
                    }
                    .status-label { font-size: 11px; text-transform: uppercase; color: var(--muted); font-weight: 700; letter-spacing: 0.5px; }
                    .status-val { font-size: 17px; font-weight: 700; color: var(--accent); margin-top: 4px; }
                    form {
                        background: var(--card);
                        padding: 24px;
                        border-radius: 16px;
                        border: 1px solid var(--border);
                    }
                    .section-title { font-size: 16px; font-weight: 700; color: var(--amber); margin: 20px 0 12px 0; border-bottom: 1px solid var(--border); padding-bottom: 6px; }
                    .section-title:first-of-type { margin-top: 0; }
                    .form-group { margin-bottom: 14px; }
                    label { display: block; font-size: 13px; font-weight: 600; color: var(--muted); margin-bottom: 6px; }
                    input[type="text"], input[type="password"], input[type="number"] {
                        width: 100%;
                        background: #0b1120;
                        border: 1px solid var(--border);
                        border-radius: 8px;
                        padding: 10px 14px;
                        font-size: 14px;
                        color: #fff;
                        outline: none;
                        transition: border-color 0.2s;
                    }
                    input:focus { border-color: var(--accent); }
                    .row { display: flex; gap: 12px; }
                    .row .form-group { flex: 1; }
                    .btn-save {
                        width: 100%;
                        background: linear-gradient(135deg, #10b981, #059669);
                        color: #fff;
                        border: none;
                        padding: 14px;
                        border-radius: 10px;
                        font-size: 16px;
                        font-weight: 800;
                        cursor: pointer;
                        margin-top: 18px;
                        box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
                    }
                    .btn-save:hover { opacity: 0.95; }
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>✨ Aura Clock</h1>
                        <p>Pannello di Controllo Locale • Xiaomi Mi Smart Clock</p>
                    </header>

                    <div class="status-grid">
                        <div class="status-card">
                            <div class="status-label">IP Dispositivo</div>
                            <div class="status-val">$deviceIp</div>
                        </div>
                        <div class="status-card">
                            <div class="status-label">Sensore Luce</div>
                            <div class="status-val">${"%.1f".format(lux)} lux</div>
                        </div>
                        <div class="status-card">
                            <div class="status-label">WebSocket HA</div>
                            <div class="status-val" style="color: ${if (wsStatus.contains("Connesso", true)) "var(--success)" else "var(--amber)"};">$wsStatus</div>
                        </div>
                        <div class="status-card">
                            <div class="status-label">Prossima Sveglia</div>
                            <div class="status-val" style="color: var(--amber);">$nextAlarm</div>
                        </div>
                    </div>

                    <form action="/save" method="post">
                        <div class="section-title">Home Assistant & Dashboard</div>

                        <div class="form-group">
                            <label>URL Base Home Assistant (senza slash finale)</label>
                            <input type="text" name="ha_base_url" value="${escapeHtml(appConfig.haBaseUrl)}" placeholder="http://192.168.1.100:8123" required>
                        </div>

                        <div class="form-group">
                            <label>Path Vista Lovelace (es. /lovelace-comodino/0?kiosk)</label>
                            <input type="text" name="ha_dashboard_path" value="${escapeHtml(appConfig.haDashboardPath)}" required>
                        </div>

                        <div class="form-group">
                            <label>Long-Lived Access Token</label>
                            <input type="password" name="ha_access_token" value="${escapeHtml(appConfig.haAccessToken)}" placeholder="Incolla il token HA comodino">
                        </div>

                        <div class="section-title">Entità Sincronizzate</div>

                        <div class="row">
                            <div class="form-group">
                                <label>Entità Stato Sveglia (input_boolean)</label>
                                <input type="text" name="entity_alarm_enabled" value="${escapeHtml(appConfig.entityAlarmEnabled)}">
                            </div>
                            <div class="form-group">
                                <label>Entità Orario Sveglia (input_datetime)</label>
                                <input type="text" name="entity_alarm_time" value="${escapeHtml(appConfig.entityAlarmTime)}">
                            </div>
                        </div>

                        <div class="row">
                            <div class="form-group">
                                <label>Entità Temperatura Esterna (sensor)</label>
                                <input type="text" name="entity_ext_temp" value="${escapeHtml(appConfig.entityExtTemp)}">
                            </div>
                            <div class="form-group">
                                <label>Entità Meteo (weather)</label>
                                <input type="text" name="entity_weather" value="${escapeHtml(appConfig.entityWeather)}">
                            </div>
                        </div>

                        <div class="form-group">
                            <label>Entità Luce Comodino (Tasto Centrale MUTE)</label>
                            <input type="text" name="entity_bedside_light" value="${escapeHtml(appConfig.entityBedsideLight)}">
                        </div>

                        <div class="section-title">Sensibilità Notte & Sveglia</div>

                        <div class="row">
                            <div class="form-group">
                                <label>Soglia Lux Entrata Notte (&lt; Lux)</label>
                                <input type="number" step="0.5" name="lux_night_enter" value="${appConfig.luxNightEnter}">
                            </div>
                            <div class="form-group">
                                <label>Soglia Lux Uscita Giorno (&gt; Lux)</label>
                                <input type="number" step="0.5" name="lux_night_exit" value="${appConfig.luxNightExit}">
                            </div>
                        </div>

                        <div class="row">
                            <div class="form-group">
                                <label>Durata Tap-to-Peek (secondi)</label>
                                <input type="number" name="night_peek_duration_sec" value="${appConfig.nightPeekDurationSec}">
                            </div>
                            <div class="form-group">
                                <label>Luminosità Tap-to-Peek (0.01 - 1.0)</label>
                                <input type="number" step="0.01" min="0.01" max="1.0" name="night_peek_brightness" value="${appConfig.nightPeekBrightness}">
                            </div>
                        </div>

                        <div class="row">
                            <div class="form-group">
                                <label>Minuti Snooze</label>
                                <input type="number" name="alarm_snooze_minutes" value="${appConfig.alarmSnoozeMinutes}">
                            </div>
                            <div class="form-group">
                                <label>Volume Massimo Sveglia (%)</label>
                                <input type="number" min="10" max="100" name="alarm_max_volume_pct" value="${appConfig.alarmMaxVolumePct}">
                            </div>
                            <div class="form-group">
                                <label>Minuti Alba Artificiale (Sunrise)</label>
                                <input type="number" min="0" max="30" name="sunrise_duration_min" value="${appConfig.sunriseDurationMin}">
                            </div>
                        </div>

                        <button type="submit" class="btn-save">💾 SALVA CONFIGURAZIONE</button>
                    </form>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
