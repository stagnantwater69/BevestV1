#include <WiFi.h>

void setup() {
  Serial.begin(115200);
  delay(1000);

  // 1. Initialize WiFi station mode so the radio loads the base MAC
  WiFi.mode(WIFI_STA);
  delay(200);

  uint8_t wifiMac[6];
  WiFi.macAddress(wifiMac);

  char wifiMacStr[18];
  snprintf(wifiMacStr, sizeof(wifiMacStr), "%02X:%02X:%02X:%02X:%02X:%02X",
           wifiMac[0], wifiMac[1], wifiMac[2], wifiMac[3], wifiMac[4], wifiMac[5]);

  char cleanWifiMac[13];
  snprintf(cleanWifiMac, sizeof(cleanWifiMac), "%02X%02X%02X%02X%02X%02X",
           wifiMac[0], wifiMac[1], wifiMac[2], wifiMac[3], wifiMac[4], wifiMac[5]);

  // 2. On ESP32, Bluetooth/BLE MAC is Base MAC + 2
  uint8_t bleMac[6];
  memcpy(bleMac, wifiMac, 6);
  // Add 2 with carry handling
  uint16_t last = bleMac[5] + 2;
  bleMac[5] = last & 0xFF;
  if (last > 0xFF) {
    bleMac[4]++;
  }

  char bleMacStr[18];
  snprintf(bleMacStr, sizeof(bleMacStr), "%02X:%02X:%02X:%02X:%02X:%02X",
           bleMac[0], bleMac[1], bleMac[2], bleMac[3], bleMac[4], bleMac[5]);

  char cleanBleMac[13];
  snprintf(cleanBleMac, sizeof(cleanBleMac), "%02X%02X%02X%02X%02X%02X",
           bleMac[0], bleMac[1], bleMac[2], bleMac[3], bleMac[4], bleMac[5]);

  Serial.println("\n=======================================================");
  Serial.println("  ESP32 Hardware MAC Address Diagnostic");
  Serial.println("=======================================================");
  Serial.print  ("  1. Wi-Fi Station MAC (Base MAC) : ");
  Serial.println(wifiMacStr);
  Serial.print  ("     Clean Wi-Fi MAC              : ");
  Serial.println(cleanWifiMac);
  Serial.println("     -> Used by Developer Dashboard / Wi-Fi Telemetry");
  Serial.println("-------------------------------------------------------");
  Serial.print  ("  2. Bluetooth Low Energy MAC     : ");
  Serial.println(bleMacStr);
  Serial.print  ("     Clean BLE MAC                : ");
  Serial.println(cleanBleMac);
  Serial.println("     -> Detected by Phone during BLE Scan (+2 offset)");
  Serial.println("=======================================================\n");
}

void loop() {
  // Nothing needed
}
