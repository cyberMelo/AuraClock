# Integrazione Home Assistant per Aura Clock

Questa cartella contiene le configurazioni pronte per Home Assistant dedicate ad **Aura Clock** (Xiaomi Mi Smart Clock X04G).

---

## 1. Prerequisiti HACS
Assicurarsi di aver installato da HACS le seguenti card/plugin:
- **`button-card`** (`custom:button-card`)
- **`layout-card`** (`custom:layout-card`)
- **`kiosk-mode`**

---

## 2. Configurazione Backend (`configuration.yaml`)
Copiare le voci definite in `configuration.yaml`:
1. `sensor: - platform: time_date` per garantire l'aggiornamento minuto per minuto.
2. `input_boolean.sveglia_comodino_attiva`
3. `input_datetime.sveglia_comodino_orario`
4. `script.sveglia_comodino_cambia_ora`
5. `template` sensor `Meteo Comodino MinMax`

Riavviare Home Assistant dopo aver salvato.

---

## 3. Creazione Dashboard Lovelace
1. Andare in **Impostazioni** -> **Dashboard** -> **Aggiungi Dashboard**.
2. Titolo: `Aura Comodino`
3. Icona: `mdi:clock-digital`
4. URL: `lovelace-comodino`
5. Modalità: Scegliere **YAML** o passare all'Editor di Codice Raw.
6. Incollare il contenuto integrale di `dashboard_living_weather_horizon.yaml`.

---

## 4. Generazione Token Utente Comodino
1. Creare un utente dedicato su Home Assistant (es. `utente_comodino`).
2. Generare un **Long-Lived Access Token** dal profilo utente (durata fino a 10 anni).
3. Inserire il token e l'URL di Home Assistant in Aura Clock tramite:
   - Browser PC all'indirizzo `http://<IP_OROLOGIO>:8080`
   - Oppure toccando 5 volte l'angolo in alto a sinistra sullo schermo dell'orologio.
