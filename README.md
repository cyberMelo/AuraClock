# Aura Clock • Smart Bedside Kiosk

**Aura Clock** è un'applicazione Android nativa per **Xiaomi Mi Smart Clock** (MT8167S, 1 GB RAM, Display 4" 800x480) basata su un'architettura ibrida a 4 layer sovrapposti, progettata per offrire massima affidabilità (*100% Offline-Safe*) e comfort visivo notturno (*Zero Luce Blu*).

---

## Caratteristiche Principali

- **Affidabilità 100% Offline-Safe**: Sveglia gestita tramite `AlarmManager.setAlarmClock()`, con suoneria MP3 locale progressiva (Fade-In 30s) funzionante anche a router spento o server Home Assistant offline.
- **Visual Design Diurno "Living Weather Horizon"**: Cielo animato a 60 fps dinamico in base al meteo reale, con orologio gigante centrale e Dock satinata con temperature e controllo sveglia.
- **Comfort Visivo Notturno Assoluto**: Schermo nero puro (`#000000`) con caratteri ambra caldo (`#B45309`), luminosità minima hardware all'1% e anti burn-in a pixel shift ciclico.
- **Hysteresis & Anti-Accecamento**: Debounce di 5 secondi per entrare in modalità notte e 8 secondi per uscirne, evitando abbagliamenti da flash o smartphone al buio.
- **Funzione "Tap-to-Peek" Caldo**: Tocco sullo schermo notturno per sbirciare o modificare la sveglia con filtro caldo CSS e luminosità al 12% per 15 secondi.
- **Configuratore Web Integrato**: Micro web server NanoHTTPD sulla porta `:8080` per impostare tutti i parametri da PC senza cavo o ADB.
- **Integrazione Tasti Fisici**: Mappatura contestuale per VOL+, VOL- e tasto centrale MUTE (Snooze, Stop sveglia, Toggle luce comodino).

---

## Architettura a 4 Layer

1. **Layer 1**: Android WebView con accelerazione hardware (Dashboard Lovelace).
2. **Layer 2**: Native Night & Offline Overlay (Nero puro + Ambra caldo).
3. **Layer 3**: Alarm Ringing UI (Snooze gigante + Stop corallo).
4. **Layer 4**: Settings / Provisioning Overlay (5-tap segreto o primo avvio).

---

## Struttura del Progetto

```
C:\Workspace\AuraClock/
├── app/
│   ├── src/main/
│   │   ├── java/com/custom/auraclock/
│   │   │   ├── MainActivity.kt
│   │   │   ├── AuraClockCoreService.kt
│   │   │   ├── BootReceiver.kt
│   │   │   ├── config/ (AppConfig, WebConfigServer)
│   │   │   ├── network/ (HaWebSocketClient, HaStateCache)
│   │   │   ├── hardware/ (LightSensorController, HardwareKeyHandler)
│   │   │   └── alarm/ (ReliableAlarmManager, AlarmReceiver, AlarmAudioPlayer)
│   │   ├── res/ (layout, values, drawables)
│   │   └── assets/ (gentle_alarm.mp3)
├── homeassistant/
│   ├── configuration.yaml
│   ├── dashboard_living_weather_horizon.yaml
│   └── README.md
```
