// =============================================================================
//  BeVest — Complete Smart Wearable IoT Vest Firmware
//  Combines Full Sensor Telemetry + In-App BLE Wi-Fi Provisioning
//
//  HARDWARE INTEGRATION:
//  - ESP32 Development Board (ESP32-WROOM-32 / DevKit v1)
//  - GPS NEO-6M (HardwareSerial2: RX=GPIO16, TX=GPIO17)
//  - MPU6050 6-DoF IMU (I2C: SDA=GPIO21, SCL=GPIO22) -> Fall & Motion Detection
//  - MAX30102 Pulse Oximeter (I2C: SDA=GPIO21, SCL=GPIO22) -> Heart Rate & SpO2
//  - DS18B20 Digital Thermometer (OneWire: GPIO4) -> Body Temperature
//  - SOS Emergency Button (GPIO15 with INPUT_PULLUP)
//  - On-Board BOOT Button (GPIO0 with INPUT_PULLUP) -> 3-second Reset
//  - Active Buzzer (GPIO25 or GPIO2) -> Siren & Auditory Feedback
//  - On-Board LED (GPIO2) -> BLE Pairing Blinker / Online Indicator
//
//  CLOUD & APP WORKFLOW:
//  1. On first boot (or after reset), ESP32 enters BLE Pairing Mode.
//     Advertised BLE Name: BEVEST-<CLEAN_MAC> (e.g. BEVEST-582ABD717D42)
//  2. Bevest Mobile App connects over BLE, scans vest QR, and sends Wi-Fi credentials.
//  3. ESP32 connects to Wi-Fi, saves credentials to NVS Flash (Preferences),
//     reports status code 2 (SUCCESS) back to the app, shuts down BLE to reclaim 100%
//     of RAM, and begins real-time telemetry streaming to Firebase Realtime Database.
//  4. Stream target:
//     - Live Readings: /liveReadings/{workerId} (1s interval)
//     - Vest Status:   /vestsLive/{vestId}     (10s interval)
//
//  =============================================================================
//  ⚠️ IMPORTANT ARDUINO IDE COMPILATION SETTINGS:
//  Because this sketch combines both the Bluetooth LE stack and Firebase/SSL,
//  you MUST select a partition scheme with at least 1.9MB app space:
//
//    Tools -> Board            -> "ESP32 Dev Module" (or your specific ESP32)
//    Tools -> Partition Scheme -> "Huge APP (3MB No OTA/1MB SPIFFS)"
//                                 OR "Minimal SPIFFS (1.9MB APP with OTA/190KB SPIFFS)"
//
//  If left at default (1.2MB), Arduino IDE will show:
//  "text section exceeds available space in board"
//  =============================================================================
//  REQUIRED LIBRARIES (Install via Arduino IDE Library Manager):
//  1. Firebase ESP32 Client (by Mobizt)
//  2. TinyGPSPlus (by Mikal Hart)
//  3. Adafruit MPU6050 + Adafruit Sensor
//  4. SparkFun MAX3010x Pulse and Proximity Sensor Library
//  5. DallasTemperature + OneWire (by Miles Burton / Paul Stoffregen)
//  =============================================================================

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <TinyGPSPlus.h>
#include <HardwareSerial.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>
#include <MAX30105.h>
#include <heartRate.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <math.h>
#include <time.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"
#include "rom/rtc.h"

// Firebase ESP Client (Uses installed FirebaseESP32 library)
#include <FirebaseESP32.h>
#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>

// ── BLE GATT Provisioning UUIDs (Matches Bevest Mobile App) ───────────────────
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CONFIG_CHAR_UUID       "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define STATUS_CHAR_UUID       "1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e"

// ── Firebase Configuration ───────────────────────────────────────────────────
#define FIREBASE_API_KEY       "AIzaSyBJMgiehdzS4V_La39159w3NZ5yeeTIwYU"
#define FIREBASE_DATABASE_URL  "bevest-70698-default-rtdb.asia-southeast1.firebasedatabase.app"

// Approach A: Firebase Anonymous Authentication (Zero hardcoded credentials!)
// If Anonymous auth is enabled in Firebase Console, the ESP32 signs in automatically
// without requiring any email or password.
// Optional fallback credentials (leave empty "" to use pure anonymous auth):
#define DEVICE_EMAIL           ""
#define DEVICE_PASSWORD        ""

// ── Default Fallbacks (Overridden by NVS / In-App Provisioning) ───────────────
#define DEFAULT_VEST_ID        "VEST-001"
#define DEFAULT_WORKER_ID      "WRK-001"

// ── Pin Definitions ──────────────────────────────────────────────────────────
#define GPS_RX_PIN             16   // ESP32 RX2 <- GPS TX
#define GPS_TX_PIN             17   // ESP32 TX2 -> GPS RX
// I2C Bus: SDA = GPIO 21, SCL = GPIO 22 (shared by MPU6050 and MAX30102)
#define I2C_SDA_PIN            21
#define I2C_SCL_PIN            22
#define DS18B20_PIN            4    // OneWire Temperature Data
#define SOS_BUTTON_PIN         15   // External SOS push button to GND (INPUT_PULLUP)
#define ONBOARD_BOOT_BUTTON    0    // Built-in BOOT button on ESP32 board
#define ONBOARD_LED_PIN        2    // Built-in Blue LED on ESP32 board
#define BUZZER_PIN             25   // Active Buzzer (change to 2 if buzzer is on GPIO2)

// ── RTC Memory for EN Double-Click Detection ─────────────────────────────────
RTC_DATA_ATTR int rtcDoubleTapFlag = 0;

// ── Timing & Intervals ───────────────────────────────────────────────────────
#define PUSH_INTERVAL_MS       1000   // Telemetry push to Firebase every 1s
#define VESTLIVE_INTERVAL_MS   10000  // Heartbeat to /vestsLive every 10s
#define FALL_COOLDOWN_MS       6000   // Hold fall status for 6s before resolving
#define HR_AVG_SIZE            4      // Moving average window for heart rate

// ── Vital Safety Thresholds ──────────────────────────────────────────────────
#define TEMP_HIGH_ALERT        38.0f  // Heatstroke threshold (°C)
#define HR_HIGH_ALERT          120    // Tachycardia threshold (BPM)
#define HR_LOW_ALERT           45     // Bradycardia threshold (BPM)
#define SPO2_LOW_ALERT         90     // Hypoxia threshold (%)
#define ENABLE_SMART_FALLBACK  false  // Set true to generate realistic demo vitals if sensors unplugged

// ── Blue LED Indication Patterns ─────────────────────────────────────────────
enum LedPattern {
  LED_OFF,
  LED_SLOW_BLINK,           // 1. Slow Blinking (500ms ON / 500ms OFF) -> BLE Broadcast / Pairing Mode
  LED_FAST_BLINK,           // 2. Fast Blinking (120ms ON / 120ms OFF) -> Connected to Wi-Fi & Normal Telemetry
  LED_ERR_WIFI_2_BLINKS,    // 3. 2 blinks per 2 sec -> Issues connecting to Wi-Fi using saved credentials
  LED_ERR_FIREBASE_3_BLINKS,// 4. 3 blinks per 2 sec -> Connected to Wi-Fi, but cannot connect to Firebase
  LED_ERR_SENSOR_4_BLINKS,  // 5. 4 blinks per 2 sec -> MPU6050 Gyro/Sensor hardware error (I2C disconnected)
  LED_ERR_POWER_5_BLINKS,   // 6. 5 blinks per 2 sec -> Powerbank / Low Voltage warning (< 3.0V logic)
  LED_SOLID_ON,             // 7. Solid ON -> Mobile Phone actively connected over BLE GATT
  LED_EMERGENCY_STROBE      // 8. Rapid Double-Strobe -> Fall Detected or SOS Active
};

LedPattern currentLedPattern = LED_SLOW_BLINK;
unsigned long lastWifiRetryMs = 0;

// ── Global Variables & State ─────────────────────────────────────────────────
Preferences        preferences;
String             currentWifiSSID     = "";
String             currentWifiPassword = "";
String             currentVestId       = DEFAULT_VEST_ID;
String             currentWorkerId     = DEFAULT_WORKER_ID;
String             esp32MacAddress     = "";
String             esp32CleanMac       = "";
String             esp32SerialNumber   = "";
bool               isConfigured        = false;
bool               inProvisioningMode  = false;

// BLE Server pointers & buffers
BLEServer*         pBleServer          = nullptr;
BLECharacteristic* pStatusChar         = nullptr;
bool               bleDeviceConnected  = false;
volatile bool      newConfigReceived   = false;
String             rxSsid              = "";
String             rxPass              = "";
String             rxVestId            = "";

// Button hold tracking
unsigned long      buttonPressStart    = 0;
bool               buttonHeld          = false;

// Firebase objects
FirebaseData       fbdo;
FirebaseAuth       auth;
FirebaseConfig     config;
bool               firebaseReady       = false;

// Sensor instances
TinyGPSPlus        gps;
HardwareSerial     gpsSerial(2);
Adafruit_MPU6050   mpu;
bool               mpuOk               = false;
MAX30105           particleSensor;
bool               maxOk               = false;
OneWire            oneWire(DS18B20_PIN);
DallasTemperature  tempSensor(&oneWire);

// Sensor readings state
double             gpsLat              = 0.0;
double             gpsLng              = 0.0;
int                gpsSats             = 0;
bool               gpsFixed            = false;
float              gpsHDOP             = 99.9f;
unsigned long      gpsLastFixMs        = 0;

bool               fallDetected        = false;
unsigned long      fallTimestamp       = 0;
String             motionState         = "STATIONARY";
int                heartBPM            = 0;
int                spO2                = 0;
float              bodyTempC           = 36.6f;
bool               sosTriggered        = false;
int                batteryPercent      = 95;

unsigned long      lastTempRead        = 0;
unsigned long      lastPush            = 0;
unsigned long      lastVestLive        = 0;
unsigned long      lastPrint           = 0;
unsigned long      buzzerOffTime       = 0;

// Pulse oximeter buffer
byte               ratesBuffer[HR_AVG_SIZE];
byte               rateIndex           = 0;
long               irValue             = 0;

// Buzzer & Alarm State
bool               alarmActive         = false;
unsigned long      alarmEndTime        = 0;
unsigned long      lastAlarmToggleMs   = 0;
bool               alarmBuzzerState    = false;

// ── Forward Declarations ─────────────────────────────────────────────────────
void startBleProvisioning();
void stopBleProvisioning();
bool attemptWiFiConnection(const String& ssid, const String& pass, int timeoutSec = 15);
void initFirebaseService();
void loadStoredConfiguration();
void checkResetTriggers();
void readMPU6050();
void readHeartRate();
void readBodyTemp();
void checkSOS();
void checkVitalEmergency();
void updateBuzzerAndLeds();
void startEmergencyAlarm(unsigned long durationMs = 10000);
void stopEmergencyAlarm();
void beepShort(unsigned long durationMs);
void triggerBuzzer(int beeps);
void pushReadingToFirebase();
void pushVestLiveToFirebase();
void syncOnlinePresenceToFirestore(bool isOnline = true);
void printStatus();
uint64_t getEpochMillis();
float calcSpO2(long red, long ir);
String extractJsonValue(const String& json, const String& key);

// ── Lightweight JSON Extractor (Zero External Dependencies) ───────────────────
String extractJsonValue(const String& json, const String& key) {
  int keyIndex = json.indexOf("\"" + key + "\"");
  if (keyIndex == -1) return "";

  int colonIndex = json.indexOf(':', keyIndex);
  if (colonIndex == -1) return "";

  int firstQuote = json.indexOf('"', colonIndex);
  if (firstQuote == -1) return "";

  int secondQuote = json.indexOf('"', firstQuote + 1);
  if (secondQuote == -1) return "";

  return json.substring(firstQuote + 1, secondQuote);
}

// ── Non-Blocking Blue LED Indication Controller ──────────────────────────────
void setLedPattern(LedPattern pattern) {
  currentLedPattern = pattern;
}

void updateLedIndicator() {
  unsigned long now = millis();
  bool ledState = false;

  // Emergency takes highest priority (rapid emergency strobe)
  if (fallDetected || sosTriggered || alarmActive) {
    unsigned long phase = now % 500;
    ledState = (phase < 60) || (phase >= 120 && phase < 180);
    digitalWrite(ONBOARD_LED_PIN, ledState ? HIGH : LOW);
    return;
  }

  // Active BLE GATT connection from phone
  if (bleDeviceConnected && inProvisioningMode) {
    digitalWrite(ONBOARD_LED_PIN, HIGH); // Solid ON when mobile app is actively connected
    return;
  }

  switch (currentLedPattern) {
    case LED_SOLID_ON:
      ledState = true;
      break;

    case LED_SLOW_BLINK:
      // Slow Blinking: 500ms ON / 500ms OFF (1 Hz) -> BLE Broadcast / Pairing Mode
      ledState = (now / 500) % 2 == 0;
      break;

    case LED_FAST_BLINK:
      // Fast Blinking: 120ms ON / 120ms OFF -> Connected to Wi-Fi & streaming
      ledState = (now / 120) % 2 == 0;
      break;

    case LED_ERR_WIFI_2_BLINKS: {
      // 2 blinks per 2 seconds (Wi-Fi connection error / router unreachable)
      unsigned long phase = now % 2000;
      ledState = (phase < 150) || (phase >= 350 && phase < 500);
      break;
    }

    case LED_ERR_FIREBASE_3_BLINKS: {
      // 3 blinks per 2 seconds (Wi-Fi connected, but Firebase connection/auth failed)
      unsigned long phase = now % 2000;
      ledState = (phase < 150) || (phase >= 300 && phase < 450) || (phase >= 600 && phase < 750);
      break;
    }

    case LED_ERR_SENSOR_4_BLINKS: {
      // 4 blinks per 2 seconds (MPU6050 Gyro/Sensor I2C error / unplugged)
      unsigned long phase = now % 2000;
      ledState = (phase < 120) || (phase >= 240 && phase < 360) || (phase >= 480 && phase < 600) || (phase >= 720 && phase < 840);
      break;
    }

    case LED_ERR_POWER_5_BLINKS: {
      // 5 blinks per 2 seconds (Brownout / Powerbank voltage sag warning)
      unsigned long phase = now % 2000;
      ledState = (phase < 100) || (phase >= 200 && phase < 300) || (phase >= 400 && phase < 500) || (phase >= 600 && phase < 700) || (phase >= 800 && phase < 900);
      break;
    }

    case LED_OFF:
    default:
      ledState = false;
      break;
  }

  digitalWrite(ONBOARD_LED_PIN, ledState ? HIGH : LOW);
}

// ── BLE Server Callbacks ─────────────────────────────────────────────────────
class ServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    bleDeviceConnected = true;
    Serial.println("\n[BLE] >>> Mobile App connected over Bluetooth! <<<");
    beepShort(120);
  }

  void onDisconnect(BLEServer* pServer) {
    bleDeviceConnected = false;
    Serial.println("[BLE] Mobile App disconnected.");
    if (inProvisioningMode && pServer) {
      delay(100);
      BLEDevice::startAdvertising();
      Serial.println("[BLE] Re-advertising for BeVest App connection...");
    }
  }
};

// ── BLE Config Characteristic Callbacks ──────────────────────────────────────
class ConfigCallbacks: public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String rxValue = pCharacteristic->getValue().c_str();
    if (rxValue.length() > 0) {
      Serial.printf("\n[BLE] Received Configuration Payload:\n%s\n", rxValue.c_str());

      // 1. Try JSON format: {"ssid":"...","pass":"...","vestId":"..."}
      rxSsid   = extractJsonValue(rxValue, "ssid");
      rxPass   = extractJsonValue(rxValue, "pass");
      rxVestId = extractJsonValue(rxValue, "vestId");

      // 2. Semicolon fallback: "SSID;PASSWORD;VEST_ID"
      if (rxSsid.length() == 0 && rxValue.indexOf(';') != -1) {
        int firstSemi  = rxValue.indexOf(';');
        int secondSemi = rxValue.indexOf(';', firstSemi + 1);
        rxSsid = rxValue.substring(0, firstSemi);
        if (secondSemi != -1) {
          rxPass   = rxValue.substring(firstSemi + 1, secondSemi);
          rxVestId = rxValue.substring(secondSemi + 1);
        } else {
          rxPass   = rxValue.substring(firstSemi + 1);
        }
      }

      rxSsid.trim();
      rxPass.trim();
      rxVestId.trim();

      if (rxSsid.length() > 0) {
        newConfigReceived = true;
      } else {
        Serial.println("[BLE] ⚠️ Received payload without valid 'ssid'!");
      }
    }
  }
};

// ── Setup ────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(600);

  // Configure IO pins
  pinMode(SOS_BUTTON_PIN,      INPUT_PULLUP);
  pinMode(ONBOARD_BOOT_BUTTON, INPUT_PULLUP);
  pinMode(ONBOARD_LED_PIN,     OUTPUT);
  pinMode(BUZZER_PIN,          OUTPUT);
  digitalWrite(ONBOARD_LED_PIN, LOW);
  digitalWrite(BUZZER_PIN,      LOW);

  // 0. Disable brownout reset during powerbank inrush & protect credentials
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  esp_reset_reason_t rstReason = esp_reset_reason();
  if (rstReason == ESP_RST_POWERON || rstReason == ESP_RST_BROWNOUT) {
    // Powerbank connection or power-up inrush: NEVER wipe credentials!
    rtcDoubleTapFlag = 0;
  } else if (rtcDoubleTapFlag == 1) {
    rtcDoubleTapFlag = 0;
    Serial.println("\n[RESET] ⚡ Double-press of EN (RST) button detected!");
    Serial.println("[RESET] Wiping saved Wi-Fi credentials and entering Pairing Mode...");
    preferences.begin("bevest", false);
    preferences.clear();
    preferences.end();
    triggerBuzzer(3);
  } else {
    // Arm double-tap window for 2.5 seconds
    rtcDoubleTapFlag = 1;
  }

  // 1. Read hardware MAC address from WiFi radio & set power-safe TX level
  WiFi.mode(WIFI_STA);
  WiFi.setTxPower(WIFI_POWER_15dBm); // Prevents powerbank 5V voltage sag
  delay(120);

  esp32MacAddress   = WiFi.macAddress();
  esp32CleanMac     = esp32MacAddress;
  esp32CleanMac.replace(":", "");
  esp32CleanMac.toUpperCase();
  esp32SerialNumber = String("BV-SN-") + esp32CleanMac;

  Serial.println("\n=======================================================");
  Serial.println("  BeVest — Smart Wearable IoT Vest System");
  Serial.printf ("  Hardware MAC : %s (Clean: %s)\n", esp32MacAddress.c_str(), esp32CleanMac.c_str());
  Serial.printf ("  Serial Number: %s\n", esp32SerialNumber.c_str());
  Serial.println("=======================================================\n");

  // 2. Load stored credentials from NVS (Preferences)
  loadStoredConfiguration();

  // 3. Initialize GPS (HardwareSerial 2)
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  Serial.printf("[GPS]     Started UART2 on RX=GPIO%d, TX=GPIO%d\n", GPS_RX_PIN, GPS_TX_PIN);

  // 4. Initialize I2C Bus for MPU6050 & MAX30102
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  delay(100);

  // MPU6050 IMU
  mpuOk = mpu.begin();
  if (mpuOk) {
    mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
    mpu.setGyroRange(MPU6050_RANGE_500_DEG);
    mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
    Serial.println("[MPU6050] OK — Motion & Fall Detector Ready");
  } else {
    Serial.println("[MPU6050] ⚠️ Not detected (check SDA=21, SCL=22)");
    setLedPattern(LED_ERR_SENSOR_4_BLINKS);
  }

  // MAX30102 Pulse Oximeter
  if (particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    particleSensor.setup(0xFF, 4, 2, 200, 411, 4096);
    particleSensor.setPulseAmplitudeRed(0xFF);
    particleSensor.setPulseAmplitudeIR(0xFF);
    particleSensor.setPulseAmplitudeGreen(0);
    memset(ratesBuffer, 0, sizeof(ratesBuffer));
    maxOk = true;
    Serial.println("[MAX30102] OK — Pulse Oximeter Ready");
  } else {
    Serial.println("[MAX30102] ⚠️ Not detected on I2C bus");
  }

  // DS18B20 Body Temperature Sensor
  pinMode(DS18B20_PIN, INPUT_PULLUP);
  tempSensor.begin();
  tempSensor.setWaitForConversion(false);
  tempSensor.requestTemperatures();
  Serial.printf("[DS18B20] Initialized on OneWire GPIO%d\n", DS18B20_PIN);

  // 5. Connect to Saved Wi-Fi or Start In-App BLE Pairing Mode
  if (isConfigured && currentWifiSSID.length() > 0) {
    Serial.printf("[WiFi]    Saved network found: \"%s\". Connecting...\n", currentWifiSSID.c_str());
    bool connected = attemptWiFiConnection(currentWifiSSID, currentWifiPassword, 12);
    if (connected) {
      setLedPattern(LED_FAST_BLINK); // Fast Blinking = Connected to Wi-Fi
      initFirebaseService();
    } else {
      Serial.println("[WiFi]    ⚠️ Could not connect to saved network on power-up.");
      Serial.println("[WiFi]    Broadcasting BLE & retrying saved Wi-Fi in background...");
      setLedPattern(LED_ERR_WIFI_2_BLINKS); // 2 blinks per 2 sec = Wi-Fi issue
      startBleProvisioning();
    }
  } else {
    Serial.println("[PROV]    No saved Wi-Fi found. Entering BLE Pairing Mode out of the box...");
    setLedPattern(LED_SLOW_BLINK); // Slow Blinking = BLE Broadcast
    startBleProvisioning();
  }
}

// ── Main Loop ────────────────────────────────────────────────────────────────
void loop() {
  // Check for button reset triggers (BOOT button or SOS button held for 3s)
  checkResetTriggers();

  // Update non-blocking Blue LED indicator pattern
  updateLedIndicator();

  // ── Provisioning Mode Handler ──
  if (inProvisioningMode) {
    // If vest was previously configured with saved Wi-Fi, retry connecting in background every 8 seconds
    if (isConfigured && currentWifiSSID.length() > 0 && !bleDeviceConnected) {
      if (millis() - lastWifiRetryMs > 8000) {
        lastWifiRetryMs = millis();
        Serial.printf("[WiFi] Background retry connecting to: \"%s\"...\n", currentWifiSSID.c_str());
        if (attemptWiFiConnection(currentWifiSSID, currentWifiPassword, 6)) {
          Serial.println("[WiFi] ✅ Reconnected to site Wi-Fi!");
          stopBleProvisioning();
          setLedPattern(LED_FAST_BLINK);
          beepShort(200);
          initFirebaseService();
          return;
        } else {
          setLedPattern(LED_ERR_WIFI_2_BLINKS); // 2 blinks per 2 sec
        }
      }
    }

    if (newConfigReceived) {
      newConfigReceived = false;
      Serial.printf("[PROV] Attempting connection to \"%s\"...\n", rxSsid.c_str());

      // Notify App: 1 = Connecting
      if (pStatusChar) {
        pStatusChar->setValue("1");
        pStatusChar->notify();
      }

      bool success = attemptWiFiConnection(rxSsid, rxPass, 20);

      if (success) {
        Serial.println("[PROV] ✅ Connected to site Wi-Fi successfully!");

        // Save credentials permanently to NVS
        preferences.begin("bevest", false);
        preferences.putString("ssid", rxSsid);
        preferences.putString("pass", rxPass);
        if (rxVestId.length() > 0) {
          preferences.putString("vestId", rxVestId);
          currentVestId = rxVestId;
        }
        preferences.putBool("configured", true);
        preferences.end();

        currentWifiSSID = rxSsid;
        currentWifiPassword = rxPass;
        isConfigured = true;

        // Notify App: 2 = Success (Confirmed)
        if (pStatusChar) {
          pStatusChar->setValue("2");
          pStatusChar->notify();
        }

        delay(1000); // Allow BLE notification packet to be transmitted

        // Shut down Bluetooth radio to release heap memory for Firebase TLS
        stopBleProvisioning();
        setLedPattern(LED_FAST_BLINK);
        beepShort(300);

        // Start Firebase Telemetry Service
        initFirebaseService();
      } else {
        Serial.println("[PROV] ❌ Failed to connect with provided credentials.");
        setLedPattern(LED_ERR_WIFI_2_BLINKS);
        // Notify App: 3 = Authentication Failed
        if (pStatusChar) {
          pStatusChar->setValue("3");
          pStatusChar->notify();
        }
        WiFi.disconnect();
      }
    }
    delay(10);
    return; // Do not execute telemetry loop while in provisioning mode
  }

  // ── Normal Telemetry Operation ──
  // 1. Process GPS stream
  while (gpsSerial.available() > 0) {
    if (gps.encode(gpsSerial.read())) {
      if (gps.satellites.isValid()) gpsSats = (int)gps.satellites.value();
      if (gps.hdop.isValid())       gpsHDOP = (float)gps.hdop.hdop();

      if (gps.location.isValid() && gps.location.age() < 2000 && gpsHDOP < 5.0f) {
        gpsLat       = gps.location.lat();
        gpsLng       = gps.location.lng();
        gpsFixed     = true;
        gpsLastFixMs = millis();
      }
    }
  }

  if (gpsFixed && (millis() - gpsLastFixMs > 15000)) {
    gpsFixed = false;
    gpsLat   = 0.0;
    gpsLng   = 0.0;
  }

  // 2. Read Physical Sensors
  readMPU6050();
  readHeartRate();
  readBodyTemp();
  checkSOS();
  checkVitalEmergency();
  updateBuzzerAndLeds();

  const unsigned long now = millis();

  // 3. Wi-Fi Auto-Reconnect
  static unsigned long lastWifiRetry = 0;
  if (WiFi.status() != WL_CONNECTED && (now - lastWifiRetry > 8000)) {
    lastWifiRetry = now;
    Serial.println("[WiFi] Reconnecting to Wi-Fi...");
    WiFi.disconnect();
    WiFi.begin(currentWifiSSID.c_str(), currentWifiPassword.c_str());
  }

  // 4. Firebase Auto-Reconnect
  static unsigned long lastFbRetry = 0;
  if (WiFi.status() == WL_CONNECTED && !Firebase.ready() && (now - lastFbRetry > 8000)) {
    lastFbRetry = now;
    Serial.println("[Firebase] Re-authenticating session...");
    Firebase.begin(&config, &auth);
  }

  // 5. Push Live Readings to Firebase
  if (now - lastPush >= PUSH_INTERVAL_MS) {
    lastPush = now;
    pushReadingToFirebase();
  }

  // 6. Push Vest Heartbeat to Firebase
  if (now - lastVestLive >= VESTLIVE_INTERVAL_MS) {
    lastVestLive = now;
    pushVestLiveToFirebase();
  }

  // 7. Periodic Serial Monitor Summary
  if (now - lastPrint >= 4000) {
    lastPrint = now;
    printStatus();
  }

  // 8. Monitor Wi-Fi & Firebase connection health for Blue LED diagnostics
  if (WiFi.status() != WL_CONNECTED) {
    setLedPattern(LED_ERR_WIFI_2_BLINKS); // 2 blinks per 2 sec
    if (now - lastWifiRetryMs > 8000) {
      lastWifiRetryMs = now;
      Serial.println("[WiFi] Reconnecting to Wi-Fi...");
      WiFi.reconnect();
    }
  } else if (!Firebase.ready()) {
    setLedPattern(LED_ERR_FIREBASE_3_BLINKS); // 3 blinks per 2 sec
  } else if (!mpuOk) {
    setLedPattern(LED_ERR_SENSOR_4_BLINKS); // 4 blinks per 2 sec
  } else {
    setLedPattern(LED_FAST_BLINK); // Fast Blinking = Connected to Wi-Fi & Streaming
  }

  delay(2);
}

// ── NVS Configuration Loader ─────────────────────────────────────────────────
void loadStoredConfiguration() {
  preferences.begin("bevest", true); // Read-only
  isConfigured        = preferences.getBool("configured", false);
  currentWifiSSID     = preferences.getString("ssid", "");
  currentWifiPassword = preferences.getString("pass", "");
  currentVestId       = preferences.getString("vestId", DEFAULT_VEST_ID);
  currentWorkerId     = preferences.getString("workerId", DEFAULT_WORKER_ID);
  preferences.end();

  Serial.printf("[NVS]     Configured: %s | Vest ID: %s | Saved SSID: \"%s\"\n",
                isConfigured ? "YES" : "NO", currentVestId.c_str(), currentWifiSSID.c_str());
}

// ── Start BLE Provisioning Server ────────────────────────────────────────────
void startBleProvisioning() {
  inProvisioningMode = true;

  // Advertised Name: BEVEST-<CLEAN_MAC> (e.g. BEVEST-246F28B49C12)
  String bleName = String("BEVEST-") + esp32CleanMac;
  Serial.printf("[BLE]     Starting BLE Provisioning Service...\n");
  Serial.printf("[BLE]     Broadcasting Name: \"%s\"\n", bleName.c_str());

  BLEDevice::init(bleName.c_str());
  pBleServer = BLEDevice::createServer();
  pBleServer->setCallbacks(new ServerCallbacks());

  BLEService *pService = pBleServer->createService(SERVICE_UUID);

  // Config Characteristic (Write)
  BLECharacteristic *pConfigChar = pService->createCharacteristic(
                                     CONFIG_CHAR_UUID,
                                     BLECharacteristic::PROPERTY_WRITE
                                   );
  pConfigChar->setCallbacks(new ConfigCallbacks());

  // Status Characteristic (Read / Notify)
  pStatusChar = pService->createCharacteristic(
                  STATUS_CHAR_UUID,
                  BLECharacteristic::PROPERTY_READ |
                  BLECharacteristic::PROPERTY_NOTIFY
                );
  pStatusChar->addDescriptor(new BLE2902());
  pStatusChar->setValue("0"); // 0 = Idle / Waiting

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE]     ✅ Waiting for BeVest Mobile App to pair...");
  triggerBuzzer(2); // Two beeps to signal Pairing Mode active
}

// ── Stop BLE Provisioning Server ─────────────────────────────────────────────
void stopBleProvisioning() {
  inProvisioningMode = false;
  Serial.println("[BLE]     De-initializing BLE radio to reclaim heap memory...");
  BLEDevice::deinit(true); // Reclaims ~60KB of heap
  pBleServer = nullptr;
  pStatusChar = nullptr;
}

// ── Attempt Wi-Fi Connection ─────────────────────────────────────────────────
bool attemptWiFiConnection(const String& ssid, const String& pass, int timeoutSec) {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(ssid.c_str(), pass.c_str());

  Serial.printf("[WiFi]    Connecting to \"%s\"", ssid.c_str());
  int waited = 0;
  while (WiFi.status() != WL_CONNECTED && waited < (timeoutSec * 2)) {
    delay(500);
    Serial.print(".");
    waited++;
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("[WiFi]    ✅ Connected! IP: %s\n", WiFi.localIP().toString().c_str());
    return true;
  }
  return false;
}

// ── Initialize Firebase Realtime Database ─────────────────────────────────────
void initFirebaseService() {
  // Sync Time via NTP
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  struct tm timeinfo;
  Serial.print("[NTP]     Syncing UTC time");
  for (int i = 0; i < 10 && !getLocalTime(&timeinfo, 300); i++) {
    Serial.print(".");
    delay(150);
  }
  Serial.println(" Done");

  // Configure Firebase Client
  config.api_key = FIREBASE_API_KEY;
  config.database_url = FIREBASE_DATABASE_URL;
  config.token_status_callback = tokenStatusCallback;
  config.timeout.serverResponse = 10 * 1000;
  config.timeout.socketConnection = 10 * 1000;

  // Approach A: Firebase Anonymous Authentication (Zero hardcoded credentials!)
  Serial.println("[Firebase] 🔐 Requesting Anonymous Device Session from Cloud Auth...");
  bool anonSuccess = Firebase.signUp(&config, &auth, "", "");
  if (anonSuccess) {
    Serial.println("[Firebase] 🟢 Anonymous Device Authentication granted by Cloud Auth!");
  } else {
    Serial.printf("[Firebase] ℹ️ Anonymous signup status: %s\n", config.signer.signupError.message.c_str());
    if (strlen(DEVICE_EMAIL) > 0 && strlen(DEVICE_PASSWORD) > 0) {
      Serial.println("[Firebase] 🔄 Falling back to configured device credentials...");
      auth.user.email = DEVICE_EMAIL;
      auth.user.password = DEVICE_PASSWORD;
    } else {
      Serial.println("[Firebase] ⚠️ If authentication fails, ensure 'Anonymous' is enabled in Firebase Console → Authentication → Sign-in method.");
    }
  }

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  fbdo.setBSSLBufferSize(4096, 1024);
  fbdo.setResponseSize(2048);

  Serial.print("[Firebase] Authenticating");
  unsigned long t0 = millis();
  while (!Firebase.ready() && millis() - t0 < 12000) {
    delay(300);
    Serial.print(".");
  }
  Serial.println();

  if (Firebase.ready()) {
    firebaseReady = true;
    Serial.println("[Firebase] ✅ Cloud Connected & Ready to Stream!");
    beepShort(250);

    // Auto-detect vest ID mapped to this ESP32 hardware MAC address
    String macMappingPath = String("/macToVest/") + esp32CleanMac;
    if (Firebase.getString(fbdo, macMappingPath.c_str())) {
      String mappedId = fbdo.stringData();
      if (mappedId.length() > 0 && mappedId != "null" && mappedId != currentVestId) {
        currentVestId = mappedId;
        Serial.printf("[SYNC] 🎯 Auto-detected Vest ID from Hardware MAC: %s\n", currentVestId.c_str());
        preferences.begin("bevest", false);
        preferences.putString("vestId", currentVestId);
        preferences.end();
      }
    }

    // Immediately sync assigned worker from Firebase Realtime Database
    String path = String("/vestsLive/") + currentVestId;
    String assigned = "";
    if (Firebase.getString(fbdo, path + "/assignedWorkerId")) {
      assigned = fbdo.stringData();
    }
    if ((assigned.length() == 0 || assigned == "null") && Firebase.getString(fbdo, (String("/vestsLiveByMac/") + esp32CleanMac + "/assignedWorkerId").c_str())) {
      assigned = fbdo.stringData();
    }
    if (assigned.length() > 0 && assigned != "null" && assigned != currentWorkerId) {
      currentWorkerId = assigned;
      Serial.printf("[SYNC] ✅ Assigned to worker: %s\n", currentWorkerId.c_str());
      preferences.begin("bevest", false);
      preferences.putString("workerId", currentWorkerId);
      preferences.end();
    }
    pushVestLiveToFirebase();
    syncOnlinePresenceToFirestore(true);
    Serial.printf("[PRESENCE] 🟢 %s is ONLINE in Realtime DB & Cloud Firestore\n", currentVestId.c_str());
  } else {
    Serial.println("[Firebase] ⚠️ Auth in progress. Will retry automatically.");
  }
}

// ── Reset Triggers (BOOT Button or SOS Button held 3s) ────────────────────────
void checkResetTriggers() {
  // Disarm EN double-press window after 2.5 seconds of normal boot
  if (rtcDoubleTapFlag == 1 && millis() > 2500) {
    rtcDoubleTapFlag = 0;
  }

  // Check either external SOS button (GPIO 15) OR on-board BOOT button (GPIO 0)
  bool buttonPressed = (digitalRead(SOS_BUTTON_PIN) == LOW) || (digitalRead(ONBOARD_BOOT_BUTTON) == LOW);

  if (buttonPressed) {
    if (buttonPressStart == 0) {
      buttonPressStart = millis();
      buttonHeld = false;
    } else if (!buttonHeld && (millis() - buttonPressStart >= 3000)) {
      buttonHeld = true;
      Serial.println("\n[RESET]   ⚠️ Factory Reset Triggered (Button held 3s)!");
      triggerBuzzer(4);

      syncOnlinePresenceToFirestore(false);
      Firebase.setBool(fbdo, String("/vestsLive/") + currentVestId + "/online", false);

      preferences.begin("bevest", false);
      preferences.clear();
      preferences.end();
      Serial.println("[RESET]   Wi-Fi credentials cleared. Restarting into Pairing Mode...");
      delay(800);
      ESP.restart();
    }
  } else {
    buttonPressStart = 0;
    buttonHeld = false;
  }
}

// ── Push Live Telemetry to Firebase ───────────────────────────────────────────
void pushReadingToFirebase() {
  if (WiFi.status() != WL_CONNECTED || !Firebase.ready()) return;

  FirebaseJson json;
  json.set("vestId", currentVestId);
  json.set("workerId", currentWorkerId);
  json.set("mac", esp32CleanMac);
  json.set("macAddress", esp32MacAddress);
  json.set("serialNumber", esp32SerialNumber);
  json.set("heartRate", heartBPM);
  json.set("temperature", (double)bodyTempC);
  json.set("motionState", motionState);
  json.set("fallDetected", fallDetected);
  json.set("online", true);

  json.set("gpsFixed", gpsFixed);
  if (gpsFixed) {
    json.set("latitude",  gpsLat);
    json.set("longitude", gpsLng);
    json.set("gpsSats",   gpsSats);
    json.set("gpsHDOP",   (double)gpsHDOP);
  }

  json.set("battery", batteryPercent);
  json.set("timestamp", (double)getEpochMillis());
  json.set("timestampServer/.sv", "timestamp");
  json.set("safetyResponse", "NONE");

  // Push to worker path
  String appPath = String("/liveReadings/") + currentWorkerId;
  bool okWorker = Firebase.setJSON(fbdo, appPath.c_str(), json);

  // Push to vest path as well so fallback listener catches it
  String vestPath = String("/liveReadingsByVest/") + currentVestId;
  bool okVest = Firebase.setJSON(fbdo, vestPath.c_str(), json);

  // Push to MAC path so app catches it regardless of vest ID
  String macReadingPath = String("/liveReadingsByMac/") + esp32CleanMac;
  Firebase.setJSON(fbdo, macReadingPath.c_str(), json);

  // If a fall is detected, immediately update fall alert flag on vest
  if (fallDetected) {
    Firebase.setBool(fbdo, String("/vestsLive/") + currentVestId + "/fallDetected", true);
  }

  if (okWorker || okVest) {
    if (gpsFixed) {
      Serial.printf("[PUSH] HR:%d | Temp:%.1f°C | %s | Fall:%d | 🛰️ GPS: Lat=%.6f Lng=%.6f Sats=%d\n",
                    heartBPM, bodyTempC, motionState.c_str(), fallDetected, gpsLat, gpsLng, gpsSats);
    } else {
      Serial.printf("[PUSH] HR:%d | Temp:%.1f°C | %s | Fall:%d | 📡 No GPS fix yet\n",
                    heartBPM, bodyTempC, motionState.c_str(), fallDetected);
    }
  } else {
    Serial.printf("[PUSH ERROR] %s\n", fbdo.errorReason().c_str());
  }
}

// ── Push Vest Heartbeat to Firebase ──────────────────────────────────────────
void pushVestLiveToFirebase() {
  if (WiFi.status() != WL_CONNECTED || !Firebase.ready()) return;

  // Check if Cloud mapped this ESP32 hardware MAC to a specific Vest ID (e.g. VEST-2026-XXXX)
  String macMappingPath = String("/macToVest/") + esp32CleanMac;
  if (Firebase.getString(fbdo, macMappingPath.c_str())) {
    String mappedId = fbdo.stringData();
    if (mappedId.length() > 0 && mappedId != "null" && mappedId != currentVestId) {
      currentVestId = mappedId;
      Serial.printf("[SYNC] 🎯 Cloud mapped Vest ID to: %s\n", currentVestId.c_str());
      preferences.begin("bevest", false);
      preferences.putString("vestId", currentVestId);
      preferences.end();
    }
  }

  String path = String("/vestsLive/") + currentVestId;
  FirebaseJson json;
  json.set("vestId", currentVestId);
  json.set("mac", esp32CleanMac);
  json.set("macAddress", esp32MacAddress);
  json.set("serialNumber", esp32SerialNumber);
  json.set("battery", batteryPercent);
  json.set("online", true);
  json.set("fallDetected", fallDetected);
  json.set("lastSeen", (double)getEpochMillis());
  json.set("lastSeenServer/.sv", "timestamp");

  // Push to /vestsLive/{vestId}
  if (Firebase.setJSON(fbdo, path.c_str(), json)) {
    Serial.printf("[RTDB] 🟢 Vest %s marked ONLINE in /vestsLive\n", currentVestId.c_str());
  } else {
    Serial.printf("[RTDB ERROR] /vestsLive: %s\n", fbdo.errorReason().c_str());
  }

  // Push to /vestsLiveByMac/{cleanMac}
  String macPath = String("/vestsLiveByMac/") + esp32CleanMac;
  Firebase.setJSON(fbdo, macPath.c_str(), json);

  // Sync online presence directly to Cloud Firestore
  syncOnlinePresenceToFirestore(true);

  // Sync assigned worker from Firebase Realtime Database (via vest path or mac path)
  String workerFound = "";
  if (Firebase.getString(fbdo, (path + "/assignedWorkerId").c_str())) {
    workerFound = fbdo.stringData();
  }
  if ((workerFound.length() == 0 || workerFound == "null") && Firebase.getString(fbdo, (macPath + "/assignedWorkerId").c_str())) {
    workerFound = fbdo.stringData();
  }

  if (workerFound.length() > 0 && workerFound != "null" && workerFound != currentWorkerId) {
    currentWorkerId = workerFound;
    Serial.printf("[SYNC] ✅ Synced assigned worker to: %s\n", currentWorkerId.c_str());
    preferences.begin("bevest", false);
    preferences.putString("workerId", currentWorkerId);
    preferences.end();
  }
}

// ── Cloud Firestore Presence Sync ────────────────────────────────────────────
void syncOnlinePresenceToFirestore(bool isOnline) {
  if (WiFi.status() != WL_CONNECTED) return;

  WiFiClientSecure client;
  client.setInsecure(); // No certificate verification for Google Cloud REST
  HTTPClient http;

  String url = String("https://firestore.googleapis.com/v1/projects/bevest-70698/databases/(default)/documents/vests/") 
               + currentVestId + "?updateMask.fieldPaths=online&updateMask.fieldPaths=lastSeen&key=" + FIREBASE_API_KEY;

  if (http.begin(client, url)) {
    http.addHeader("Content-Type", "application/json");
    const char* tok = Firebase.getToken();
    if (tok != nullptr && strlen(tok) > 0) {
      http.addHeader("Authorization", String("Bearer ") + tok);
    } else if (config.signer.tokens.jwt.length() > 0) {
      http.addHeader("Authorization", String("Bearer ") + config.signer.tokens.jwt.c_str());
    }

    uint64_t epochMs = getEpochMillis();
    char epochBuf[24];
    snprintf(epochBuf, sizeof(epochBuf), "%llu", (unsigned long long)(epochMs > 0 ? epochMs : millis()));
    String body = String("{\"fields\":{\"online\":{\"booleanValue\":") + (isOnline ? "true" : "false")
                + "},\"lastSeen\":{\"integerValue\":\"" + String(epochBuf) + "\"}}}";

    int httpCode = http.PATCH(body);
    if (httpCode == 200 || httpCode == 204) {
      Serial.printf("[FIRESTORE] 🟢 Vest %s marked online=%s in Cloud Firestore!\n",
                    currentVestId.c_str(), isOnline ? "true" : "false");
    } else {
      Serial.printf("[FIRESTORE] ℹ️ Cloud Firestore sync code: %d\n", httpCode);
    }
    http.end();
  }
}

// ── MPU6050 Motion & Fall Detection ──────────────────────────────────────────
void readMPU6050() {
  if (!mpuOk) {
    motionState = "STATIONARY";
    return;
  }

  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  float ax  = a.acceleration.x / 9.80665f;
  float ay  = a.acceleration.y / 9.80665f;
  float az  = a.acceleration.z / 9.80665f;
  float mag = sqrtf(ax * ax + ay * ay + az * az);

  unsigned long now = millis();

  if (fallDetected) {
    if (now - fallTimestamp >= FALL_COOLDOWN_MS) {
      fallDetected = false;
      motionState = "STATIONARY";
      stopEmergencyAlarm();
      Serial.println("[MPU6050] Fall alert resolved. Normal state resumed.");
      Firebase.setBool(fbdo, String("/vestsLive/") + currentVestId + "/fallDetected", false);
      pushReadingToFirebase();
    }
    return;
  }

  if (isnan(mag) || mag < 0.05f) return;

  static unsigned long freefallStartTime = 0;
  static bool freefallDetected = false;

  if (mag < 0.55f) {
    freefallDetected = true;
    freefallStartTime = now;
  }

  if (freefallDetected) {
    if (now - freefallStartTime <= 1200) {
      if (mag > 4.5f) {
        fallDetected = true;
        fallTimestamp = now;
        freefallDetected = false;
        motionState = "FALL_DETECTED";
        Serial.printf("\n[MPU6050] ⚠️ REAL FALL DETECTED! (Impact: %.2fg)\n", mag);
        startEmergencyAlarm(FALL_COOLDOWN_MS);
        pushReadingToFirebase();
        return;
      }
    } else {
      freefallDetected = false;
    }
  }

  if (fabs(mag - 1.0f) > 0.18f) {
    motionState = "MOVING";
  } else {
    motionState = "STATIONARY";
  }
}

// ── MAX30102 Heart Rate & SpO2 ───────────────────────────────────────────────
void readHeartRate() {
  if (!maxOk) {
    if (ENABLE_SMART_FALLBACK) {
      heartBPM = 72 + (millis() / 5000) % 5;
      spO2     = 98;
    }
    return;
  }

  irValue = particleSensor.getIR();

  if (irValue > 4500) {
    if (checkForBeat(irValue)) {
      static unsigned long lastBeat = 0;
      unsigned long now = millis();
      long delta = now - lastBeat;
      lastBeat = now;

      float bpm = 60000.0f / (float)delta;
      if (bpm >= 45 && bpm <= 190) {
        ratesBuffer[rateIndex] = (byte)bpm;
        rateIndex = (rateIndex + 1) % HR_AVG_SIZE;

        long sum = 0;
        for (byte i = 0; i < HR_AVG_SIZE; i++) sum += ratesBuffer[i];
        heartBPM = sum / HR_AVG_SIZE;
      }
    }

    long redValue = particleSensor.getRed();
    if (redValue > 1000) {
      spO2 = (int)calcSpO2(redValue, irValue);
      spO2 = constrain(spO2, 85, 100);
    }
  } else {
    if (ENABLE_SMART_FALLBACK) {
      heartBPM = 72 + ((millis() / 4000) % 5);
      spO2     = 98;
    } else {
      heartBPM = 0;
      spO2     = 0;
    }
  }
}

float calcSpO2(long red, long ir) {
  float ratio = (float)red / (float)ir;
  return 110.0f - 25.0f * ratio;
}

// ── DS18B20 Temperature ──────────────────────────────────────────────────────
void readBodyTemp() {
  if (millis() - lastTempRead >= 2000) {
    lastTempRead = millis();
    float t = tempSensor.getTempCByIndex(0);

    if (t != DEVICE_DISCONNECTED_C && t > 20.0f && t < 48.0f) {
      bodyTempC = t;
    } else {
      if (ENABLE_SMART_FALLBACK) {
        bodyTempC = 36.6f + ((float)((millis() / 8000) % 4) * 0.1f);
      }
    }
    tempSensor.requestTemperatures();
  }
}

// ── Vital Sign Danger Monitor ────────────────────────────────────────────────
unsigned long lastVitalAlertMs = 0;

void checkVitalEmergency() {
  unsigned long now = millis();
  if (now - lastVitalAlertMs < 6000) return;

  bool vitalAlert = false;
  String alertMsg = "";

  if (bodyTempC >= TEMP_HIGH_ALERT && bodyTempC < 48.0f) {
    vitalAlert = true;
    alertMsg += String("HIGH TEMP (") + String(bodyTempC, 1) + "°C) ";
  }

  if (heartBPM > 0) {
    if (heartBPM >= HR_HIGH_ALERT) {
      vitalAlert = true;
      alertMsg += String("HIGH HR (") + String(heartBPM) + " BPM) ";
    } else if (heartBPM <= HR_LOW_ALERT) {
      vitalAlert = true;
      alertMsg += String("LOW HR (") + String(heartBPM) + " BPM) ";
    }
  }

  if (spO2 > 50 && spO2 < SPO2_LOW_ALERT) {
    vitalAlert = true;
    alertMsg += String("LOW SpO2 (") + String(spO2) + "%) ";
  }

  if (vitalAlert) {
    lastVitalAlertMs = now;
    Serial.printf("\n[VITAL ALERT] 🚨 CRITICAL VITALS: %s- Activating Alarm!\n", alertMsg.c_str());
    startEmergencyAlarm(5000);
    pushReadingToFirebase();
  }
}

// ── SOS Button ───────────────────────────────────────────────────────────────
void checkSOS() {
  if (digitalRead(SOS_BUTTON_PIN) == LOW) {
    if (!sosTriggered && (millis() - buttonPressStart < 4000)) {
      sosTriggered = true;
      Serial.println("\n[SOS] 🚨 SOS EMERGENCY PRESSED! Activating Siren!");
      startEmergencyAlarm(10000);
      pushReadingToFirebase();
    }
  } else {
    sosTriggered = false;
  }
}

// ── Buzzer & LED Helpers ─────────────────────────────────────────────────────
void startEmergencyAlarm(unsigned long durationMs) {
  alarmActive = true;
  alarmEndTime = millis() + durationMs;
  alarmBuzzerState = false;
  lastAlarmToggleMs = 0;
}

void stopEmergencyAlarm() {
  alarmActive = false;
  alarmBuzzerState = false;
  digitalWrite(BUZZER_PIN, LOW);
}

void beepShort(unsigned long durationMs) {
  if (alarmActive) return;
  digitalWrite(BUZZER_PIN, HIGH);
  buzzerOffTime = millis() + durationMs;
}

void updateBuzzerAndLeds() {
  unsigned long now = millis();

  if (alarmActive) {
    if (now >= alarmEndTime) {
      stopEmergencyAlarm();
      return;
    }
    if (now - lastAlarmToggleMs >= 100) {
      lastAlarmToggleMs = now;
      alarmBuzzerState = !alarmBuzzerState;
      digitalWrite(BUZZER_PIN, alarmBuzzerState ? HIGH : LOW);
    }
    return;
  }

  if (buzzerOffTime > 0 && now >= buzzerOffTime) {
    digitalWrite(BUZZER_PIN, LOW);
    buzzerOffTime = 0;
  }
}

void triggerBuzzer(int beeps) {
  for (int i = 0; i < beeps; i++) {
    digitalWrite(BUZZER_PIN, HIGH); delay(120);
    digitalWrite(BUZZER_PIN, LOW);  delay(80);
  }
}

// ── Epoch Time in Milliseconds ───────────────────────────────────────────────
uint64_t getEpochMillis() {
  time_t now = time(nullptr);
  if (now < 1700000000) return (uint64_t)millis();
  return (uint64_t)now * 1000ULL;
}

// ── Periodic Status Print ────────────────────────────────────────────────────
void printStatus() {
  Serial.printf("[STATUS] Vest: %s | HR: %d BPM | Temp: %.1f C | Motion: %s | Sats: %d | WiFi: %s | RTDB: %s\n",
                currentVestId.c_str(), heartBPM, bodyTempC, motionState.c_str(), gpsSats,
                WiFi.status() == WL_CONNECTED ? "OK" : "NO",
                Firebase.ready() ? "READY" : "OFFLINE");
  if (gpsFixed) {
    Serial.printf("[GPS]    🛰️  LOCKED — Lat: %.6f | Lng: %.6f | HDOP: %.1f\n", gpsLat, gpsLng, gpsHDOP);
  } else {
    Serial.printf("[GPS]    📡 No fix yet — Sentences: %lu | Sats visible: %d\n",
                  gps.passedChecksum(), gpsSats);
  }
}

