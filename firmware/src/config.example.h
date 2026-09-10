#pragma once
// ─────────────────────────────────────────────────────────────────────────────
// Copy this file to  firmware/src/config.h  and fill in real values.
// config.h is gitignored — never commit credentials.
// ─────────────────────────────────────────────────────────────────────────────

// WiFi — 2.4 GHz only (the ESP32 has no 5 GHz radio).
// For the Wokwi simulator use:  SSID "Wokwi-GUEST", password "" (empty).
#define WIFI_SSID            "CHANGE_ME"
#define WIFI_PASSWORD        "CHANGE_ME"

// Firebase — project bevest-70698
#define FIREBASE_API_KEY       "CHANGE_ME"   // Web API key: Firebase console -> Project settings
#define FIREBASE_DATABASE_URL  "https://bevest-70698-default-rtdb.asia-southeast1.firebasedatabase.app/"

// Device account — a dedicated Firebase Auth (email/password) user carrying the
// custom claim  { "device": true }.  See firmware/README.md -> "Device auth".
#define DEVICE_EMAIL         "vest-001@device.bevest"
#define DEVICE_PASSWORD      "CHANGE_ME"

// Identity
#define VEST_ID             "VEST-001"
#define WORKER_ID           "CHANGE_ME"   // Firestore workers/{id} this vest is assigned to.
                                          // TODO(option B): resolve at boot from
                                          // vests/{VEST_ID}.assignedWorkerId instead of hard-coding.

// Cadence
#define READING_INTERVAL_MS   4000
#define VESTLIVE_INTERVAL_MS  15000
#define RESPONSE_TIMEOUT_S    15     // keep in sync with Firestore settings/thresholds.responseTimeoutSeconds

// ── Pin map — ESP32 DevKitC / WROOM-32. FINALISE against the assembled vest. ──
// I2C — MPU6050
#define PIN_I2C_SDA   21
#define PIN_I2C_SCL   22
// UART2 — NEO-6M   (GPS module TX -> ESP32 RX)
#define PIN_GPS_RX    16
#define PIN_GPS_TX    17
// OneWire — DS18B20 (4.7k pull-up from data line to 3V3)
#define PIN_DS18B20    4
// AD8232 — analog OUT MUST be on ADC1 (GPIO 32-39); LO+/LO- are digital
#define PIN_ECG_OUT   34
#define PIN_ECG_LOp   35
#define PIN_ECG_LOn   32
// Battery sense — resistor-divider node, ADC1
#define PIN_VBAT      39
#define VBAT_DIVIDER  2.0f   // (R1 + R2) / R2 ; 2.0 for two equal resistors
// Alert components
#define PIN_BUZZER    25
#define PIN_BUTTON    26     // wired to GND, uses INPUT_PULLUP

// Set to 1 to run with NO sensors attached (milestone 1: pipeline + safety handshake).
// Set to 0 once the real breakout boards are wired and their drivers verified.
#define SENSORS_SIMULATED 1
