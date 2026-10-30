// =============================================================================
//  BeVest — Standalone BLE WiFi Provisioning & Pairing Firmware
//  Target: Any Standard ESP32 Dev Board (No external sensors or wiring required)
//
//  LIBRARIES REQUIRED: NONE (All headers are built into the ESP32 Arduino Core)
//
//  HOW IT WORKS:
//  1. On first boot, reads hardware MAC and checks flash memory (NVS Preferences).
//  2. If no WiFi is saved, it starts BLE advertising:
//     Device Name: BEVEST-<CLEAN_MAC> (e.g. BEVEST-246F28B49C12)
//  3. Mobile App (or nRF Connect) connects to BLE and sends WiFi credentials:
//     JSON: {"ssid": "YourWiFi", "pass": "YourPassword", "vestId": "VEST-1001"}
//     OR Plain: YourWiFi;YourPassword;VEST-1001
//  4. ESP32 connects to WiFi, saves credentials permanently to flash,
//     reports SUCCESS back to the app, and shuts down BLE to save power.
//  5. Onboard LED (GPIO 2):
//     - BLINKING: In BLE Pairing Mode (Ready for setup)
//     - SOLID ON: Connected to WiFi & Online!
//
//  HOW TO RESET / RE-PAIR (No external button required):
//  - Option A: Hold the onboard "BOOT" button (GPIO 0) for 3 seconds.
//  - Option B: Quickly double-press the "EN" (RST) button like double-clicking.
// =============================================================================

#include <WiFi.h>
#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ── BLE GATT Provisioning UUIDs (Matches Bevest App Specification) ───────────
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CONFIG_CHAR_UUID       "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define STATUS_CHAR_UUID       "1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e"

// ── On-Board Pins ────────────────────────────────────────────────────────────
#define ONBOARD_LED            2    // Built-in blue LED on most ESP32 boards
#define ONBOARD_BOOT_BUTTON    0    // Built-in BOOT button (GPIO 0)

// ── RTC Memory for EN Double-Click Detection ─────────────────────────────────
RTC_DATA_ATTR int rtcDoubleTapFlag = 0;

// ── Global State ─────────────────────────────────────────────────────────────
Preferences        preferences;
String             currentSsid       = "";
String             currentPass       = "";
String             currentVestId     = "VEST-001";
String             hardwareMac       = "";
String             cleanMac          = "";
String             bevestSerialNo    = "";
bool               isConfigured      = false;
bool               inPairingMode     = false;

// BLE Server pointers & buffers
BLEServer*         pBleServer        = nullptr;
BLECharacteristic* pStatusChar       = nullptr;
bool               bleClientConnected= false;
volatile bool      newConfigReceived = false;
String             rxSsid            = "";
String             rxPass            = "";
String             rxVestId          = "";

unsigned long      bootButtonPressStart = 0;
bool               bootButtonHeld       = false;

// ── Helper: Extract Value from JSON string without external libraries ────────
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

// ── BLE Server Callbacks ─────────────────────────────────────────────────────
class BleServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    bleClientConnected = true;
    Serial.println("\n[BLE] >>> Mobile App connected over Bluetooth! <<<");
  }

  void onDisconnect(BLEServer* pServer) {
    bleClientConnected = false;
    Serial.println("[BLE] Mobile App disconnected.");
    if (inPairingMode && pServer) {
      pServer->getAdvertising()->start();
      Serial.println("[BLE] Re-advertising for BeVest App...");
    }
  }
};

// ── BLE Characteristic Write Callbacks ───────────────────────────────────────
class BleConfigCallbacks: public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String rxValue = pCharacteristic->getValue().c_str();
    if (rxValue.length() > 0) {
      Serial.printf("\n[BLE] Received Configuration Payload:\n%s\n", rxValue.c_str());

      // 1. Try JSON parsing (Standard format)
      rxSsid   = extractJsonValue(rxValue, "ssid");
      rxPass   = extractJsonValue(rxValue, "pass");
      rxVestId = extractJsonValue(rxValue, "vestId");

      // 2. Try simple semicolon format fallback: "SSID;PASSWORD;VEST_ID"
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
        Serial.println("[BLE] ⚠️ Payload missing valid 'ssid' field!");
      }
    }
  }
};

// ── Forward Declarations ─────────────────────────────────────────────────────
void startBlePairing();
void stopBlePairing();
bool attemptWiFiConnection(const String& ssid, const String& pass, int timeoutSec = 15);
void loadPreferences();
void checkResetTriggers();

// ── Setup ────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(800);

  // Initialize On-board hardware
  pinMode(ONBOARD_LED,         OUTPUT);
  pinMode(ONBOARD_BOOT_BUTTON, INPUT_PULLUP);
  digitalWrite(ONBOARD_LED,    LOW);

  // 1. Check for EN / RST Double-Click Reset (Quick double press of EN)
  if (rtcDoubleTapFlag == 1) {
    rtcDoubleTapFlag = 0;
    Serial.println("\n[RESET] ==============================================");
    Serial.println("[RESET]  ⚡ DOUBLE-PRESS OF EN (RST) DETECTED!       ");
    Serial.println("[RESET]  Wiping saved WiFi credentials from flash...  ");
    Serial.println("[RESET] ==============================================\n");
    preferences.begin("bevest", false);
    preferences.clear();
    preferences.end();
  } else {
    // Arm the flag for 2.5 seconds. If user taps EN again within 2.5s, it triggers wipe
    rtcDoubleTapFlag = 1;
  }

  // 2. Read hardware IEEE MAC Address from WiFi radio
  WiFi.mode(WIFI_STA);
  delay(150);

  hardwareMac = WiFi.macAddress();
  cleanMac    = hardwareMac;
  cleanMac.replace(":", "");
  cleanMac.toUpperCase();
  bevestSerialNo = String("BV-SN-") + cleanMac;

  Serial.println("\n=======================================================");
  Serial.println("  BeVest — Smart Wearable BLE Provisioning Firmware   ");
  Serial.printf ("  Hardware MAC : %s\n", hardwareMac.c_str());
  Serial.printf ("  Clean MAC    : %s\n", cleanMac.c_str());
  Serial.printf ("  Serial Number: %s\n", bevestSerialNo.c_str());
  Serial.println("=======================================================\n");

  // 3. Load saved WiFi credentials from NVS Flash (Preferences)
  loadPreferences();

  // 4. Decide: Connect to saved WiFi or enter BLE Pairing Mode
  if (isConfigured && currentSsid.length() > 0) {
    Serial.printf("[WiFi] Found saved network: \"%s\". Attempting connection...\n", currentSsid.c_str());
    bool connected = attemptWiFiConnection(currentSsid, currentPass, 15);

    if (connected) {
      digitalWrite(ONBOARD_LED, HIGH); // Solid ON = Connected
      Serial.println("\n[SYSTEM] ✅ Vest is ONLINE and ready!");
      Serial.printf ("[SYSTEM] IP Address: %s\n", WiFi.localIP().toString().c_str());
      Serial.printf ("[SYSTEM] Vest ID   : %s\n", currentVestId.c_str());
      Serial.println("[SYSTEM] (Hold BOOT button for 3s to wipe WiFi and re-pair)\n");
    } else {
      Serial.println("[WiFi] ⚠️ Could not connect to saved network.");
      Serial.println("[WiFi] Starting BLE Pairing Mode so Bevest App can update credentials...\n");
      startBlePairing();
    }
  } else {
    Serial.println("[PROV] No saved WiFi found (New Vest). Entering BLE Pairing Mode...\n");
    startBlePairing();
  }
}

// ── Main Loop ────────────────────────────────────────────────────────────────
void loop() {
  // Check physical reset buttons (BOOT hold 3s, or disarm EN double-tap)
  checkResetTriggers();

  // ── PAIRING MODE ACTIVE ──
  if (inPairingMode) {
    // Blink LED to indicate Pairing Mode is active (150ms ON / 150ms OFF)
    static unsigned long lastBlinkMs = 0;
    if (millis() - lastBlinkMs >= 150) {
      lastBlinkMs = millis();
      digitalWrite(ONBOARD_LED, !digitalRead(ONBOARD_LED));
    }

    // Check if new credentials arrived over BLE
    if (newConfigReceived) {
      newConfigReceived = false;
      Serial.printf("\n[PROV] Attempting connection to \"%s\"...\n", rxSsid.c_str());

      // Send status: 1 = Connecting
      if (pStatusChar) {
        pStatusChar->setValue("1");
        pStatusChar->notify();
      }

      bool success = attemptWiFiConnection(rxSsid, rxPass, 20);

      if (success) {
        Serial.println("[PROV] ✅ SUCCESS: Connected to site WiFi!");
        Serial.printf ("[PROV] Assigned IP: %s\n", WiFi.localIP().toString().c_str());

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

        currentSsid  = rxSsid;
        currentPass  = rxPass;
        isConfigured = true;

        // Send status: 2 = Connected Successfully
        if (pStatusChar) {
          pStatusChar->setValue("2");
          pStatusChar->notify();
        }

        delay(1200); // Allow notification time to reach the mobile app

        // Shut down BLE radio to save power and free 100% of RAM
        stopBlePairing();
        digitalWrite(ONBOARD_LED, HIGH); // Solid ON = Connected

        Serial.println("\n[SYSTEM] Provisioning complete! Vest is now Online.");
      } else {
        Serial.println("[PROV] ❌ FAILED: Could not connect to network (Check password/range).");
        // Send status: 3 = Connection / Authentication Failed
        if (pStatusChar) {
          pStatusChar->setValue("3");
          pStatusChar->notify();
        }
        WiFi.disconnect();
      }
    }

    delay(20);
    return;
  }

  // ── NORMAL ONLINE OPERATION ──
  // Keep LED solid ON
  digitalWrite(ONBOARD_LED, HIGH);

  // Periodic heartbeat log (every 5 seconds)
  static unsigned long lastLog = 0;
  if (millis() - lastLog >= 5000) {
    lastLog = millis();
    Serial.printf("[ONLINE] Vest: %s | WiFi: %s | IP: %s | RSSI: %d dBm\n",
                  currentVestId.c_str(), currentSsid.c_str(),
                  WiFi.localIP().toString().c_str(), WiFi.RSSI());
  }

  // Auto-reconnect if WiFi drops
  static unsigned long lastReconnectAttempt = 0;
  if (WiFi.status() != WL_CONNECTED && (millis() - lastReconnectAttempt >= 10000)) {
    lastReconnectAttempt = millis();
    Serial.println("[WiFi] Lost connection. Reconnecting...");
    WiFi.disconnect();
    WiFi.begin(currentSsid.c_str(), currentPass.c_str());
  }

  delay(10);
}

// ── NVS Flash Loader ─────────────────────────────────────────────────────────
void loadPreferences() {
  preferences.begin("bevest", true); // Read-only
  isConfigured  = preferences.getBool("configured", false);
  currentSsid   = preferences.getString("ssid", "");
  currentPass   = preferences.getString("pass", "");
  currentVestId = preferences.getString("vestId", "VEST-001");
  preferences.end();

  Serial.printf("[NVS] Configured: %s | Vest ID: %s | Saved SSID: \"%s\"\n",
                isConfigured ? "YES" : "NO", currentVestId.c_str(),
                currentSsid.length() > 0 ? currentSsid.c_str() : "(empty)");
}

// ── Start BLE Pairing Mode ───────────────────────────────────────────────────
void startBlePairing() {
  inPairingMode = true;

  // Advertised Name: BEVEST-<CLEAN_MAC> (e.g. BEVEST-246F28B49C12)
  String bleDeviceName = String("BEVEST-") + cleanMac;

  Serial.println("-------------------------------------------------------");
  Serial.println("  >>> BLUETOOTH PAIRING MODE ACTIVE <<<               ");
  Serial.printf ("  BLE Broadcast Name : %s\n", bleDeviceName.c_str());
  Serial.printf ("  Target Service UUID: %s\n", SERVICE_UUID);
  Serial.println("  Connect via BeVest Mobile App or nRF Connect to setup");
  Serial.println("-------------------------------------------------------\n");

  BLEDevice::init(bleDeviceName.c_str());
  pBleServer = BLEDevice::createServer();
  pBleServer->setCallbacks(new BleServerCallbacks());

  BLEService *pService = pBleServer->createService(SERVICE_UUID);

  // Config Characteristic (Write): Receives WiFi credentials
  BLECharacteristic *pConfigChar = pService->createCharacteristic(
                                     CONFIG_CHAR_UUID,
                                     BLECharacteristic::PROPERTY_WRITE
                                   );
  pConfigChar->setCallbacks(new BleConfigCallbacks());

  // Status Characteristic (Read / Notify): Reports connection status
  pStatusChar = pService->createCharacteristic(
                  STATUS_CHAR_UUID,
                  BLECharacteristic::PROPERTY_READ |
                  BLECharacteristic::PROPERTY_NOTIFY
                );
  pStatusChar->addDescriptor(new BLE2902());
  pStatusChar->setValue("0"); // 0 = Waiting for credentials

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Advertising started. Waiting for connection...\n");
}

// ── Stop BLE Pairing Mode ────────────────────────────────────────────────────
void stopBlePairing() {
  inPairingMode = false;
  Serial.println("[BLE] Shutting down Bluetooth radio to free RAM and save power...");
  BLEDevice::deinit(true);
  pBleServer  = nullptr;
  pStatusChar = nullptr;
}

// ── Attempt WiFi Connection ──────────────────────────────────────────────────
bool attemptWiFiConnection(const String& ssid, const String& pass, int timeoutSec) {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(ssid.c_str(), pass.c_str());

  Serial.printf("[WiFi] Connecting to \"%s\"", ssid.c_str());
  int elapsed = 0;
  while (WiFi.status() != WL_CONNECTED && elapsed < (timeoutSec * 2)) {
    delay(500);
    Serial.print(".");
    elapsed++;
  }
  Serial.println();

  return (WiFi.status() == WL_CONNECTED);
}

// ── Check Reset Triggers (On-Board Buttons) ──────────────────────────────────
void checkResetTriggers() {
  // Disarm EN double-tap flag after 2.5 seconds of normal running
  if (rtcDoubleTapFlag == 1 && millis() > 2500) {
    rtcDoubleTapFlag = 0;
  }

  // Check the physical on-board BOOT button (GPIO 0)
  if (digitalRead(ONBOARD_BOOT_BUTTON) == LOW) {
    if (bootButtonPressStart == 0) {
      bootButtonPressStart = millis();
      bootButtonHeld       = false;
    } else if (!bootButtonHeld && (millis() - bootButtonPressStart >= 3000)) {
      bootButtonHeld = true;
      Serial.println("\n[RESET] ==============================================");
      Serial.println("[RESET]  ⚠️ ON-BOARD BOOT BUTTON HELD FOR 3 SECONDS!  ");
      Serial.println("[RESET]  Wiping saved WiFi and entering Pairing Mode... ");
      Serial.println("[RESET] ==============================================\n");

      preferences.begin("bevest", false);
      preferences.clear();
      preferences.end();

      delay(600);
      ESP.restart();
    }
  } else {
    bootButtonPressStart = 0;
    bootButtonHeld       = false;
  }
}

