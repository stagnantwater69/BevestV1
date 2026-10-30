#include <WiFi.h>

void setup() {
  Serial.begin(115200);
  delay(1000);

  // 1. Initialize WiFi station mode so the radio loads the MAC
  WiFi.mode(WIFI_STA);
  delay(200);

  // 2. Read the hardware MAC string
  String mac = WiFi.macAddress();
  
  // 3. Remove colons for clean format
  String cleanMac = mac;
  cleanMac.replace(":", "");
  cleanMac.toUpperCase();

  Serial.println("\n==================================");
  Serial.print("ESP32 MAC Address: ");
  Serial.println(mac);
  Serial.print("Clean MAC (No :):  ");
  Serial.println(cleanMac);
  Serial.println("==================================\n");
}

void loop() {
  // Nothing needed here
}