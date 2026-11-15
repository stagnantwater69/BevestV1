// =============================================================================
//  BeVest — Isolated NEO-M8N Multi-GNSS Diagnostic & Test Sketch
//  Use this sketch to test, configure, and inspect the u-blox NEO-M8N GNSS module.
//
//  HARDWARE WIRING (ESP32 <-> NEO-M8N):
//  -----------------------------------------------------------------------------
//  ESP32 GPIO 16 (RX2)  <----  NEO-M8N TX  (Receives NMEA data from module)
//  ESP32 GPIO 17 (TX2)  ---->  NEO-M8N RX  (Sends UBX configuration commands)
//  ESP32 5V (or VIN)    ---->  NEO-M8N VCC (Powers the module & active antenna)
//  ESP32 GND            ---->  NEO-M8N GND (Common Ground)
//
//  SERIAL MONITOR SETTINGS:
//  - Baud rate: 115200 baud
//  - Line ending: Both NL & CR
//
//  KEY COMMANDS (Type into Serial Monitor and hit Enter):
//  - 'r' : Toggle Raw NMEA output ON/OFF
//  - 'c' : Re-send UBX Multi-GNSS configuration commands
//  - 'h' : Show help menu
// =============================================================================

#include <HardwareSerial.h>
#include <TinyGPSPlus.h>

#define GNSS_RX_PIN   16    // ESP32 RX2 <- NEO-M8N TX
#define GNSS_TX_PIN   17    // ESP32 TX2 -> NEO-M8N RX
#define GNSS_BAUD     9600  // Default u-blox NEO-M8N baud rate

HardwareSerial gnssSerial(2);
TinyGPSPlus    gps;

// ── UBX Multi-GNSS Binary Packets ─────────────────────────────────────────────
// UBX-CFG-GNSS: Enables concurrent GPS + GLONASS + Galileo + SBAS (32 channels)
const uint8_t ubxEnableAllGNSS[] = {
  0xB5, 0x62,       // Sync chars
  0x06, 0x3E,       // Class: CFG (0x06), ID: GNSS (0x3E)
  0x2C, 0x00,       // Payload length: 44 bytes
  0x00,             // msgVer: 0
  0x00, 0x20,       // numTrkChHw (0), numTrkChUse (32 channels)
  0x05,             // numConfigBlocks: 5 blocks

  // Block 1: GPS (GNSS ID 0)
  0x00, 0x08, 0x10, 0x00, 0x01, 0x00, 0x01, 0x01, // Enabled, min 8, max 16 ch, L1C/A

  // Block 2: SBAS (GNSS ID 1)
  0x01, 0x01, 0x03, 0x00, 0x01, 0x00, 0x01, 0x01, // Enabled, min 1, max 3 ch, L1C/A

  // Block 3: Galileo (GNSS ID 2)
  0x02, 0x04, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01, // Enabled, min 4, max 8 ch, E1

  // Block 4: BeiDou (GNSS ID 3)
  0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, // Disabled in favor of GLONASS

  // Block 5: GLONASS (GNSS ID 6)
  0x06, 0x08, 0x0E, 0x00, 0x01, 0x00, 0x01, 0x01  // Enabled, min 8, max 14 ch, L1OF
};

// UBX-CFG-RATE: 1 Hz Navigation Rate (1000 ms measurement cycle)
const uint8_t ubxRate1Hz[] = {
  0xB5, 0x62,
  0x06, 0x08,
  0x06, 0x00,
  0xE8, 0x03,       // 1000 ms
  0x01, 0x00,
  0x01, 0x00
};

// UBX-CFG-CFG: Commit settings to Flash, EEPROM, and BBR memory
const uint8_t ubxSaveConfig[] = {
  0xB5, 0x62,
  0x06, 0x09,
  0x0D, 0x00,
  0x00, 0x00, 0x00, 0x00,
  0xFF, 0xFF, 0x00, 0x00, // Save all sections
  0x00, 0x00, 0x00, 0x00,
  0x07                    // devBBR | devFlash | devEEPROM
};

// ── State Tracking Variables ──────────────────────────────────────────────────
bool          showRawNMEA       = false;
bool          multiGnssDetected = false;
unsigned long lastStatusPrint   = 0;
unsigned long rawBytesReceived  = 0;
unsigned long lastByteArrivalMs = 0;

// Constellation detection counts
unsigned long countGPS     = 0; // $GP...
unsigned long countGLONASS = 0; // $GL...
unsigned long countGalileo = 0; // $GA...
unsigned long countMulti   = 0; // $GN...

// ── Send UBX Frame with 16-bit Fletcher Checksum (RFC 1145) ───────────────────
void sendUBX(const uint8_t *msg, uint16_t len) {
  uint8_t ck_a = 0, ck_b = 0;
  for (uint16_t i = 2; i < len; i++) {
    ck_a += msg[i];
    ck_b += ck_a;
  }
  gnssSerial.write(msg, len);
  gnssSerial.write(ck_a);
  gnssSerial.write(ck_b);
  gnssSerial.flush();
}

void configureNEOM8N() {
  Serial.println("[SETUP] 📡 Transmitting UBX Multi-GNSS configuration to NEO-M8N...");
  delay(100);
  sendUBX(ubxEnableAllGNSS, sizeof(ubxEnableAllGNSS));
  delay(120);
  sendUBX(ubxRate1Hz, sizeof(ubxRate1Hz));
  delay(120);
  sendUBX(ubxSaveConfig, sizeof(ubxSaveConfig));
  delay(100);
  Serial.println("[SETUP] ✅ Sent UBX-CFG-GNSS (GPS + GLONASS + Galileo + SBAS enabled).");
  Serial.println("[SETUP] ✅ Committed configuration to NEO-M8N non-volatile memory.\n");
}

void setup() {
  Serial.begin(115200);
  while (!Serial && millis() < 3000); // Allow USB Serial to enumerate

  Serial.println("\n=======================================================");
  Serial.println("  BeVest — NEO-M8N Multi-GNSS Diagnostic Tester");
  Serial.println("=======================================================");
  Serial.printf("  Hardware: ESP32 UART2 (RX=GPIO%d, TX=GPIO%d) @ %d baud\n", GNSS_RX_PIN, GNSS_TX_PIN, GNSS_BAUD);
  Serial.println("  Target  : Concurrent Multi-GNSS (GPS, GLONASS, Galileo)");
  Serial.println("-------------------------------------------------------");
  Serial.println("  TIP: Take your antenna OUTSIDE with a clear view of sky.");
  Serial.println("  Type 'r' in Serial Monitor to toggle Raw NMEA stream.");
  Serial.println("  Type 'c' to re-send UBX configuration commands.");
  Serial.println("=======================================================\n");

  // Allocate 2048-byte buffer to prevent NMEA buffer drops with multiple constellations
  gnssSerial.setRxBufferSize(2048);
  gnssSerial.begin(GNSS_BAUD, SERIAL_8N1, GNSS_RX_PIN, GNSS_TX_PIN);

  // Send Multi-GNSS setup packets
  configureNEOM8N();
}

void printGNSSStatus() {
  Serial.println("\n----------------- [GNSS STATUS REPORT] -----------------");

  // 1. Connection Health
  if (rawBytesReceived == 0) {
    Serial.println("❌ STATUS: NO DATA RECEIVED FROM MODULE!");
    Serial.println("   -> Check wiring:");
    Serial.println("      ESP32 GPIO 16 (RX2) <--- NEO-M8N TX");
    Serial.println("      ESP32 GPIO 17 (TX2) ---> NEO-M8N RX");
    Serial.println("      ESP32 5V/VIN        ---> NEO-M8N VCC");
    Serial.println("      ESP32 GND           ---> NEO-M8N GND");
    return;
  }

  // 2. Multi-GNSS Mode Verification
  Serial.printf("• Constellation Talkers Detected:\n");
  Serial.printf("  - Combined GNSS ($GN...) : %lu sentences %s\n", countMulti, countMulti > 0 ? "✅ [ACTIVE]" : "");
  Serial.printf("  - US GPS        ($GP...) : %lu sentences\n", countGPS);
  Serial.printf("  - Russian GLONASS ($GL...): %lu sentences\n", countGLONASS);
  Serial.printf("  - European Galileo($GA...): %lu sentences\n", countGalileo);

  // 3. Fix & Satellites
  if (gps.location.isValid() && (fabs(gps.location.lat()) > 0.0001 || fabs(gps.location.lng()) > 0.0001)) {
    Serial.println("\n🟢 STATUS: 🛰️ SATELLITE 3D FIX LOCKED!");
    Serial.printf("  • Latitude   : %.6f°\n", gps.location.lat());
    Serial.printf("  • Longitude  : %.6f°\n", gps.location.lng());
    Serial.printf("  • Satellites : %d in fix\n", gps.satellites.value());
    Serial.printf("  • HDOP       : %.2f (Precision: %s)\n", gps.hdop.hdop(),
                  gps.hdop.hdop() <= 1.5 ? "Excellent" : (gps.hdop.hdop() <= 2.5 ? "Good" : "Moderate"));
    Serial.printf("  • Altitude   : %.1f meters (%.1f ft)\n", gps.altitude.meters(), gps.altitude.feet());
    Serial.printf("  • Speed      : %.2f km/h (%.2f mph)\n", gps.speed.kmph(), gps.speed.mph());

    if (gps.date.isValid() && gps.time.isValid()) {
      Serial.printf("  • UTC Time   : %04d-%02d-%02d %02d:%02d:%02d UTC\n",
                    gps.date.year(), gps.date.month(), gps.date.day(),
                    gps.time.hour(), gps.time.minute(), gps.time.second());
    }
    Serial.printf("  • Google Maps: https://www.google.com/maps?q=%.6f,%.6f\n",
                  gps.location.lat(), gps.location.lng());
  } else {
    Serial.println("\n🟡 STATUS: 📡 SEARCHING SATELLITES (NO FIX YET)");
    Serial.printf("  • RX Chars   : %lu bytes\n", gps.charsProcessed());
    Serial.printf("  • Valid NMEA : %lu sentences (Checksum errors: %lu)\n",
                  gps.passedChecksum(), gps.failedChecksum());
    Serial.printf("  • Sats in Fix: %d\n", gps.satellites.isValid() ? (int)gps.satellites.value() : 0);
    Serial.println("  • Diagnosis  : Module is communicating! It needs line-of-sight to the open sky.");
    Serial.println("                 Cold start usually takes 30-90 seconds outdoors.");
  }
  Serial.println("--------------------------------------------------------");
}

void loop() {
  // Read incoming characters from NEO-M8N
  while (gnssSerial.available() > 0) {
    char c = gnssSerial.read();
    rawBytesReceived++;
    lastByteArrivalMs = millis();

    // Stream raw characters if toggled by user
    if (showRawNMEA) {
      Serial.write(c);
    }

    // Inspect talker prefix on each sentence
    static char prefix[6];
    static int pIdx = 0;
    if (c == '$') {
      pIdx = 0;
    }
    if (pIdx < 5) {
      prefix[pIdx++] = c;
    }
    if (pIdx == 5) {
      prefix[5] = '\0';
      if (strncmp(prefix, "$GN", 3) == 0) countMulti++;
      else if (strncmp(prefix, "$GP", 3) == 0) countGPS++;
      else if (strncmp(prefix, "$GL", 3) == 0) countGLONASS++;
      else if (strncmp(prefix, "$GA", 3) == 0) countGalileo++;
    }

    // Feed TinyGPSPlus
    gps.encode(c);
  }

  // Periodic Status Report every 3 seconds
  if (!showRawNMEA && millis() - lastStatusPrint >= 3000) {
    lastStatusPrint = millis();
    printGNSSStatus();
  }

  // Check for interactive user commands from Serial Monitor
  if (Serial.available() > 0) {
    char cmd = Serial.read();
    if (cmd == 'r' || cmd == 'R') {
      showRawNMEA = !showRawNMEA;
      Serial.printf("\n[USER] Raw NMEA Stream: %s\n", showRawNMEA ? "ENABLED (Live feed)" : "DISABLED (Formatted Summary)");
    } else if (cmd == 'c' || cmd == 'C') {
      Serial.println("\n[USER] Re-transmitting UBX Configuration...");
      configureNEOM8N();
    } else if (cmd == 'h' || cmd == 'H') {
      Serial.println("\n--- [HELP MENU] ---");
      Serial.println("  r : Toggle live raw NMEA stream ON/OFF");
      Serial.println("  c : Re-transmit UBX Multi-GNSS configuration");
      Serial.println("  h : Display this help menu");
      Serial.println("--------------------");
    }
  }
}

