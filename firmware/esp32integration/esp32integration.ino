// ============================================================
//  BeVest — Connected Firmware with In-App BLE WiFi Provisioning
//  ESP32 + GPS NEO-6M + MPU6050 + MAX30102 + DS18B20 + SOS + Buzzer
//  Streams directly to Firebase Realtime Database for BeVest App
//
//  BLE PROVISIONING WORKFLOW:
//  1. On first boot (no saved WiFi), ESP32 enters BLE Pairing Mode.
//     Broadcast Name: BEVEST-<ESP32_SERIAL>
//  2. BeVest Android App connects over BLE and sends WiFi credentials.
//  3. ESP32 connects to WiFi, saves credentials to NVS (Preferences),
//     shuts down BLE (frees 100% RAM), and begins Firebase telemetry.
//  4. Hold SOS Button (GPIO 15) for 5 seconds to factory-reset WiFi.
// ============================================================

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
#include <MAX30105.h>          // SparkFun MAX3010x library
#include <heartRate.h>         // SparkFun beat-detection helper
#include <OneWire.h>
#include <DallasTemperature.h>
#include <math.h>
#include <time.h>

// Firebase ESP Client (Uses installed FirebaseESP32 library)
#include <FirebaseESP32.h>
#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>

// ── BLE GATT Provisioning UUIDs ──────────────────────────────
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CONFIG_CHAR_UUID       "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define STATUS_CHAR_UUID       "1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e"

// ── Firebase Configuration ───────────────────────────────────
#define FIREBASE_API_KEY       "AIzaSyBJMgiehdzS4V_La39159w3NZ5yeeTIwYU"
#define FIREBASE_DATABASE_URL  "bevest-70698-default-rtdb.asia-southeast1.firebasedatabase.app"
#define DEVICE_EMAIL           "test01@bevest.com"
#define DEVICE_PASSWORD        "sherby"

// ── Default Fallbacks (Overridden by NVS / BLE Provisioning) ──
#define DEFAULT_VEST_ID        "VEST-001"
#define DEFAULT_WORKER_ID      "WRK-001"

// ── Pin Definitions ──────────────────────────────────────────
#define GPS_RX_PIN           16   // ESP32 RX2 <- GPS TX
#define GPS_TX_PIN           17   // ESP32 TX2 -> GPS RX
// MPU6050 + MAX30102: SDA=GPIO21, SCL=GPIO22 (shared I2C bus)
#define DS18B20_PIN          4
#define SOS_BUTTON           15   // Push button to GND (INPUT_PULLUP)
#define ONBOARD_BOOT_BUTTON  0    // Built-in BOOT button on the ESP32 board
#define BUZZER               2    // Active buzzer
#define VBAT_PIN             39   // Battery sense ADC (or floating)

// ── RTC Memory for EN Double-Click Detection ─────────────────
RTC_DATA_ATTR int rtcDoubleTapFlag = 0;

// ── Intervals & Thresholds ───────────────────────────────────
#define PUSH_INTERVAL        1000   // Push live reading every 1 second (1000ms)
#define VESTLIVE_INTERVAL    10000  // Heartbeat every 10s to /vestsLive/{VEST_ID}
#define FALL_THRESHOLD       3.2f   // g-force (>3.2g = real slam/hard impact)
#define FALL_COOLDOWN_MS     6000   // Hold fall status for 6s then auto-reset to normal
#define HR_AVG_SIZE          4      // Number of beats to average for stable BPM

// ── Vital Emergency Thresholds ───────────────────────────────
#define TEMP_HIGH_ALERT      38.0f  // > 38.0°C (Heatstroke / High Fever)
#define HR_HIGH_ALERT        120    // > 120 BPM (Tachycardia / Distress)
#define HR_LOW_ALERT         45     // < 45 BPM (Bradycardia / Critical)
#define SPO2_LOW_ALERT       90     // < 90% (Hypoxia / Low Oxygen)
#define ENABLE_SMART_FALLBACK false

// ── Dynamic Configuration (Stored in NVS) ────────────────────
Preferences preferences;
String currentWifiSSID     = "";
String currentWifiPassword = "";
String currentVestId       = DEFAULT_VEST_ID;
String currentWorkerId     = DEFAULT_WORKER_ID;
String esp32MacAddress     = "";
String esp32CleanMac       = "";
String esp32SerialNumber   = "";
bool   isConfigured        = false;
bool   inProvisioningMode  = false;

// ── BLE State & Pointers ─────────────────────────────────────
BLEServer*         pBleServer         = nullptr;
BLECharacteristic* pStatusChar        = nullptr;
bool               bleDeviceConnected = false;
volatile bool      newConfigReceived  = false;
String             rxSsid             = "";
String             rxPass             = "";
String             rxVestId           = "";

// ── Firebase Objects ─────────────────────────────────────────
FirebaseData   fbdo;
FirebaseAuth   auth;
FirebaseConfig config;
bool           firebaseReady = false;

// ── Sensor Objects ───────────────────────────────────────────
TinyGPSPlus       gps;
HardwareSerial    gpsSerial(2);
Adafruit_MPU6050  mpu;
bool              mpuOk = false;
MAX30105          particleSensor;
bool              maxOk  = false;
OneWire           oneWire(DS18B20_PIN);
DallasTemperature tempSensor(&oneWire);

// ── Sensor State Variables ───────────────────────────────────
double        gpsLat         = 0.0;
double        gpsLng         = 0.0;
int           gpsSats        = 0;
bool          gpsFixed       = false;
float         gpsHDOP        = 99.9f;
unsigned long gpsLastFixMs   = 0;

bool          fallDetected   = false;
unsigned long fallTimestamp  = 0;
String        motionState    = "STATIONARY";
int           heartBPM       = 0;
int           spO2           = 0;
float         bodyTempC      = 0.0;
bool          sosTriggered   = false;
int           batteryPercent = 95;

unsigned long lastTempRead   = 0;
unsigned long lastPush       = 0;
unsigned long lastVestLive   = 0;
unsigned long lastPrint      = 0;
unsigned long buzzerOffTime  = 0;

// MAX30102 state
byte          ratesBuffer[HR_AVG_SIZE];
byte          rateIndex     = 0;
bool          beatDetected  = false;
long          irValue       = 0;

// Button Long-Press Reset State
unsigned long buttonPressStart = 0;
bool          buttonHeld       = false;

// ── Forward Declarations ─────────────────────────────────────
void initHardwareSerial();
void readMPU6050();
void readHeartRate();
void readBodyTemp();
void checkSOS();
void checkVitalEmergency();
void updateBuzzer();
void startEmergencyAlarm(unsigned long durationMs = 10000);
void stopEmergencyAlarm();
void beepShort(unsigned long durationMs);
void triggerBuzzer(int beeps);
void pushReadingToFirebase();
void pushVestLiveToFirebase();
void printStatus();
uint64_t getEpochMillis();
float    calcSpO2(long red, long ir);
void     loadStoredConfiguration();
void     startBleProvisioning();
void     stopBleProvisioning();
bool     attemptWiFiConnection(const String& ssid, const String& pass, int timeoutSec = 15);
void     initFirebaseService();
void     checkFactoryResetButton();

// ── BLE Server Callbacks ─────────────────────────────────────
class ServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    bleDeviceConnected = true;
    Serial.println("[BLE] 📱 Mobile app connected via Bluetooth!");
    beepShort(100);
  }

  void onDisconnect(BLEServer* pServer) {
    bleDeviceConnected = false;
    Serial.println("[BLE] 📱 Mobile app disconnected.");
    if (inProvisioningMode) {
      pServer->getAdvertising()->start();
      Serial.println("[BLE] Re-advertising for connection...");
    }
  }
};

// ── BLE Config Characteristic Callbacks ──────────────────────
class ConfigCallbacks: public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String rxValue = pCharacteristic->getValue().c_str();
    if (rxValue.length() > 0) {
      Serial.printf("[BLE] Received raw payload: %s\n", rxValue.c_str());

      // Parse payload using FirebaseJson (already integrated in sketch)
      FirebaseJson json;
      FirebaseJsonData data;
      json.setJsonData(rxValue);

      if (json.get(data, "ssid")) {
        rxSsid = data.stringValue;
      }
      if (json.get(data, "pass")) {
        rxPass = data.stringValue;
      }
      if (json.get(data, "vestId")) {
        rxVestId = data.stringValue;
      }

      // Fallback simple parsing if raw "SSID;PASS;VESTID" format sent
      if (rxSsid.length() == 0 && rxValue.indexOf(';') > 0) {
        int firstSemi = rxValue.indexOf(';');
        int secondSemi = rxValue.indexOf(';', firstSemi + 1);
        rxSsid = rxValue.substring(0, firstSemi);
        if (secondSemi > 0) {
          rxPass = rxValue.substring(firstSemi + 1, secondSemi);
          rxVestId = rxValue.substring(secondSemi + 1);
        } else {
          rxPass = rxValue.substring(firstSemi + 1);
        }
      }

      rxSsid.trim();
      rxPass.trim();
      rxVestId.trim();

      if (rxSsid.length() > 0) {
        newConfigReceived = true;
      }
    }
  }
};

// ── Setup ────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(600);

  // Pins
  pinMode(SOS_BUTTON,          INPUT_PULLUP);
  pinMode(ONBOARD_BOOT_BUTTON, INPUT_PULLUP);
  pinMode(BUZZER,              OUTPUT);
  digitalWrite(BUZZER, LOW);

  // 0. Check for EN / RST Double-Click Reset (Quick double press of EN)
  if (rtcDoubleTapFlag == 1) {
    rtcDoubleTapFlag = 0;
    Serial.println("\n[RESET] ⚡ Double-press of EN (RST) button detected!");
    Serial.println("[RESET] Wiping saved WiFi credentials and entering Pairing Mode...");
    preferences.begin("bevest", false);
    preferences.clear();
    preferences.end();
    triggerBuzzer(3);
  } else {
    // Arm the flag for 2.5 seconds. If user presses EN again within 2.5s, it triggers a wipe!
    rtcDoubleTapFlag = 1;
  }

  // 1. Read permanent factory MAC from WiFi radio
  WiFi.mode(WIFI_STA);
  delay(100);

  esp32MacAddress = WiFi.macAddress();
  esp32CleanMac = esp32MacAddress;
  esp32CleanMac.replace(":", "");
  esp32CleanMac.toUpperCase();
  esp32SerialNumber = String("BV-SN-") + esp32CleanMac;

  Serial.println("\n==============================================");
  Serial.println("  BeVest — Smart IoT Wearable Unit (BLE Enabled)");
  Serial.printf ("  Hardware MAC : %s (Clean: %s)\n", esp32MacAddress.c_str(), esp32CleanMac.c_str());
  Serial.printf ("  Serial Number: %s\n", esp32SerialNumber.c_str());
  Serial.println("==============================================\n");

  // 2. Load stored credentials from NVS (Preferences)
  loadStoredConfiguration();

  // 3. Initialize GPS
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  Serial.printf("[GPS]     Started UART2 on RX=GPIO%d, TX=GPIO%d\n", GPS_RX_PIN, GPS_TX_PIN);

  // 4. Initialize I2C Bus (SDA=21, SCL=22)
  Wire.begin(21, 22);
  delay(100);

  // MPU6050
  mpuOk = mpu.begin();
  if (mpuOk) {
    mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
    mpu.setGyroRange(MPU6050_RANGE_500_DEG);
    mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
    Serial.println("[MPU6050] OK — Ready");
  } else {
    Serial.println("[MPU6050] ⚠️ Not detected (check wiring SDA=21, SCL=22, AD0=GND)");
  }

  // MAX30102
  if (particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    particleSensor.setup(0xFF, 4, 2, 200, 411, 4096); 
    particleSensor.setPulseAmplitudeRed(0xFF);
    particleSensor.setPulseAmplitudeIR(0xFF);
    particleSensor.setPulseAmplitudeGreen(0);
    memset(ratesBuffer, 0, sizeof(ratesBuffer));
    maxOk = true;
    Serial.println("[MAX30102] OK — Pulse Oximeter Ready");
  } else {
    Serial.println("[MAX30102] ⚠️ NOT DETECTED on I2C.");
  }

  // DS18B20
  pinMode(DS18B20_PIN, INPUT_PULLUP);
  tempSensor.begin();
  tempSensor.setWaitForConversion(false);
  tempSensor.requestTemperatures();
  Serial.printf("[DS18B20] Initialized on GPIO%d\n", DS18B20_PIN);

  // Safe baseline
  bodyTempC = 36.6f;
  heartBPM  = 72;
  spO2      = 98;

  // 5. WiFi Connection or BLE Provisioning Mode
  if (isConfigured && currentWifiSSID.length() > 0) {
    Serial.printf("[WiFi]    Saved network found: \"%s\". Connecting...\n", currentWifiSSID.c_str());
    bool connected = attemptWiFiConnection(currentWifiSSID, currentWifiPassword, 15);
    if (connected) {
      initFirebaseService();
    } else {
      Serial.println("[WiFi]    ⚠️ Could not connect to saved network.");
      Serial.println("[WiFi]    Starting BLE Pairing Mode so supervisor can update WiFi...");
      startBleProvisioning();
    }
  } else {
    Serial.println("[PROV]    No saved WiFi found. Entering BLE Pairing Mode out of the box...");
    startBleProvisioning();
  }
}

// ── Main Loop ────────────────────────────────────────────────
void loop() {
  // Check if user is holding the button for 5 seconds to factory-reset WiFi
  checkFactoryResetButton();

  // ── Provisioning Mode Handler ──
  if (inProvisioningMode) {
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
        Serial.println("[PROV] ✅ Connected to site WiFi successfully!");

        // Save to NVS
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

        // Notify App: 2 = Success
        if (pStatusChar) {
          pStatusChar->setValue("2");
          pStatusChar->notify();
        }

        delay(1000); // Give notification time to reach the mobile app

        // Turn off BLE radio to free memory for Firebase
        stopBleProvisioning();
        beepShort(300);

        // Initialize Cloud services
        initFirebaseService();
      } else {
        Serial.println("[PROV] ❌ Failed to connect with provided credentials.");
        // Notify App: 3 = Authentication / Connection Failed
        if (pStatusChar) {
          pStatusChar->setValue("3");
          pStatusChar->notify();
        }
        WiFi.disconnect();
      }
    }
    delay(20);
    return; // Don't run telemetry loop until WiFi is provisioned
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
  updateBuzzer();

  const unsigned long now = millis();

  // 3. WiFi Auto-Reconnect
  static unsigned long lastWifiRetry = 0;
  if (WiFi.status() != WL_CONNECTED && (now - lastWifiRetry > 8000)) {
    lastWifiRetry = now;
    Serial.println("[WiFi] Attempting reconnect...");
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

  // 5. Push Live Readings to Cloud
  if (now - lastPush >= PUSH_INTERVAL) {
    lastPush = now;
    pushReadingToFirebase();
  }

  // 6. Push Vest Heartbeat
  if (now - lastVestLive >= VESTLIVE_INTERVAL) {
    lastVestLive = now;
    pushVestLiveToFirebase();
  }

  // 7. Serial Summary
  if (now - lastPrint >= 4000) {
    lastPrint = now;
    printStatus();
  }

  delay(2);
}

// ── NVS Configuration Loader ─────────────────────────────────
void loadStoredConfiguration() {
  preferences.begin("bevest", true); // Read-only mode
  isConfigured        = preferences.getBool("configured", false);
  currentWifiSSID     = preferences.getString("ssid", "");
  currentWifiPassword = preferences.getString("pass", "");
  currentVestId       = preferences.getString("vestId", DEFAULT_VEST_ID);
  currentWorkerId     = preferences.getString("workerId", DEFAULT_WORKER_ID);
  preferences.end();

  Serial.printf("[NVS]     Configured: %s | Vest ID: %s | Saved SSID: \"%s\"\n",
                isConfigured ? "YES" : "NO", currentVestId.c_str(), currentWifiSSID.c_str());
}

// ── Start BLE Provisioning Server ────────────────────────────
void startBleProvisioning() {
  inProvisioningMode = true;

  // Advertised name: BEVEST-<CLEAN_MAC> (e.g. BEVEST-246F28B49C12)
  String bleName = String("BEVEST-") + esp32CleanMac;
  Serial.printf("[BLE]     Starting BLE Provisioning Service...\n");
  Serial.printf("[BLE]     Advertising Name: \"%s\"\n", bleName.c_str());

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

  Serial.println("[BLE]     ✅ Waiting for BeVest Mobile App connection...");
  triggerBuzzer(2); // Two short beeps to signal Pairing Mode active
}

// ── Stop BLE Provisioning Server ─────────────────────────────
void stopBleProvisioning() {
  inProvisioningMode = false;
  Serial.println("[BLE]     Shutting down Bluetooth radio to free RAM for WiFi & Firebase...");
  BLEDevice::deinit(true); // Shuts down BLE stack and reclaims heap
  pBleServer = nullptr;
  pStatusChar = nullptr;
}

// ── Attempt WiFi Connection ──────────────────────────────────
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
    Serial.printf("[WiFi]    ✅ Connected! Local IP: %s\n", WiFi.localIP().toString().c_str());
    return true;
  }
  return false;
}

// ── Initialize Firebase ──────────────────────────────────────
void initFirebaseService() {
  // NTP Time Sync
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  struct tm timeinfo;
  Serial.print("[NTP]     Syncing clock");
  for (int i = 0; i < 10 && !getLocalTime(&timeinfo, 300); i++) {
    Serial.print(".");
    delay(150);
  }
  Serial.println(" Done");

  // Firebase Init
  config.api_key = FIREBASE_API_KEY;
  config.database_url = FIREBASE_DATABASE_URL;
  auth.user.email = DEVICE_EMAIL;
  auth.user.password = DEVICE_PASSWORD;
  config.token_status_callback = tokenStatusCallback;
  config.timeout.serverResponse = 10 * 1000;
  config.timeout.socketConnection = 10 * 1000;

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
    Serial.println("[Firebase] ✅ Cloud Connected & Ready!");
    beepShort(250);
  } else {
    Serial.println("[Firebase] ⚠️ Auth in progress. Will auto-retry in loop.");
  }
}

// ── Factory Reset Button Handler (Hold for 3s) ───────────────
void checkFactoryResetButton() {
  // Disarm EN double-press window after 2.5 seconds of normal boot
  if (rtcDoubleTapFlag == 1 && millis() > 2500) {
    rtcDoubleTapFlag = 0;
  }

  // Check either the external SOS button (GPIO 15) OR the on-board BOOT button (GPIO 0)
  bool buttonPressed = (digitalRead(SOS_BUTTON) == LOW) || (digitalRead(ONBOARD_BOOT_BUTTON) == LOW);

  if (buttonPressed) {
    if (buttonPressStart == 0) {
      buttonPressStart = millis();
      buttonHeld = false;
    } else if (!buttonHeld && (millis() - buttonPressStart >= 3000)) {
      buttonHeld = true;
      Serial.println("\n[RESET]   ⚠️ Factory Reset triggered (Button held 3s)!");
      triggerBuzzer(4); // 4 alert beeps

      preferences.begin("bevest", false);
      preferences.clear();
      preferences.end();
      Serial.println("[RESET]   WiFi and Vest ID cleared. Rebooting into Pairing Mode...");
      delay(800);
      ESP.restart();
    }
  } else {
    buttonPressStart = 0;
    buttonHeld = false;
  }
}

// ── Push Live Reading to Firebase ─────────────────────────────
void pushReadingToFirebase() {
  if (WiFi.status() != WL_CONNECTED || !Firebase.ready()) return;

  FirebaseJson json;
  json.set("vestId", currentVestId);
  json.set("heartRate", heartBPM);
  json.set("temperature", (double)bodyTempC);
  json.set("motionState", motionState);
  json.set("fallDetected", fallDetected);

  json.set("gpsFixed", gpsFixed);
  if (gpsFixed) {
    json.set("latitude",  gpsLat);
    json.set("longitude", gpsLng);
    json.set("gpsSats",   gpsSats);
    json.set("gpsHDOP",   (double)gpsHDOP);
  }

  json.set("battery", batteryPercent);
  json.set("timestamp", (double)getEpochMillis());
  json.set("safetyResponse", "NONE");

  String appPath = String("/liveReadings/") + currentWorkerId;
  if (Firebase.setJSON(fbdo, appPath.c_str(), json)) {
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

// ── Push Vest Heartbeat ──────────────────────────────────────
void pushVestLiveToFirebase() {
  if (WiFi.status() != WL_CONNECTED || !Firebase.ready()) return;

  String path = String("/vestsLive/") + currentVestId;
  FirebaseJson json;
  json.set("battery", batteryPercent);
  json.set("lastSeen", (double)getEpochMillis());

  Firebase.setJSON(fbdo, path.c_str(), json);
}

// ── MPU6050 Motion & Fall Detection ──────────────────────────
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
        Serial.printf("\n[MPU6050] ⚠️ REAL FALL DETECTED! (Freefall + Impact: %.2fg)\n", mag);
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

// ── MAX30102 Heart Rate ──────────────────────────────────────
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
        beatDetected = true;

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

// ── DS18B20 Temperature ──────────────────────────────────────
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

// ── Vital Sign Danger Monitor ────────────────────────────────
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

// ── SOS Button ───────────────────────────────────────────────
void checkSOS() {
  if (digitalRead(SOS_BUTTON) == LOW) {
    // Only trigger SOS if button was not being held for factory reset
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

// ── Buzzer Helpers ───────────────────────────────────────────
bool          alarmActive       = false;
unsigned long alarmEndTime      = 0;
unsigned long lastAlarmToggleMs = 0;
bool          alarmBuzzerState  = false;

void startEmergencyAlarm(unsigned long durationMs) {
  alarmActive = true;
  alarmEndTime = millis() + durationMs;
  alarmBuzzerState = false;
  lastAlarmToggleMs = 0;
}

void stopEmergencyAlarm() {
  alarmActive = false;
  alarmBuzzerState = false;
  digitalWrite(BUZZER, LOW);
}

void beepShort(unsigned long durationMs) {
  if (alarmActive) return;
  digitalWrite(BUZZER, HIGH);
  buzzerOffTime = millis() + durationMs;
}

void updateBuzzer() {
  unsigned long now = millis();

  if (alarmActive) {
    if (now >= alarmEndTime) {
      stopEmergencyAlarm();
      return;
    }
    if (now - lastAlarmToggleMs >= 100) {
      lastAlarmToggleMs = now;
      alarmBuzzerState = !alarmBuzzerState;
      digitalWrite(BUZZER, alarmBuzzerState ? HIGH : LOW);
    }
    return;
  }

  if (buzzerOffTime > 0 && now >= buzzerOffTime) {
    digitalWrite(BUZZER, LOW);
    buzzerOffTime = 0;
  }
}

void triggerBuzzer(int beeps) {
  for (int i = 0; i < beeps; i++) {
    digitalWrite(BUZZER, HIGH); delay(120);
    digitalWrite(BUZZER, LOW);  delay(80);
  }
}

// ── Epoch Time in Milliseconds ───────────────────────────────
uint64_t getEpochMillis() {
  time_t now = time(nullptr);
  if (now < 1700000000) return (uint64_t)millis();
  return (uint64_t)now * 1000ULL;
}

// ── Status Print ─────────────────────────────────────────────
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

