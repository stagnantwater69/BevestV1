#include <Arduino.h>
#include "config.h"
#include "contract.h"
#include "net/net.h"
#include "sensors/sensors.h"
#include "power/battery.h"
#include "logic/safety.h"

static uint32_t s_lastReading  = 0;
static uint32_t s_lastVestLive = 0;

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println(F("\n[bevest] wearable unit — boot"));

  safety::begin();      // + alert (buzzer / button)
  sensors::begin();
  battery::begin();

  if (!net::begin()) {
    Serial.println(F("[bevest] network bring-up failed — restarting in 10s"));
    delay(10000);
    ESP.restart();
  }
  net::beginSafetyStream();
  Serial.printf("[bevest] live as worker=%s vest=%s\n", net::workerId(), VEST_ID);
}

void loop() {
  sensors::loop();
  safety::loop();
  net::loop();

  // publish a safetyResponse transition the instant it happens (button / timeout)
  if (safety::consumeChanged()) {
    net::writeSafetyResponse(safety::current());
    Serial.printf("[safety] -> %s\n", safety::current());
  }

  const uint32_t now = millis();

  if (now - s_lastReading >= READING_INTERVAL_MS) {
    s_lastReading = now;

    double lat = 0, lon = 0;
    const bool fix = sensors::gps::location(lat, lon);

    Payload p;
    p.vestId       = VEST_ID;
    p.heartRate    = sensors::heart::bpm();
    p.temperatureC = sensors::temp::readC();
    p.motion       = sensors::motion::state();
    p.fallDetected = sensors::motion::fall();
    p.latitude     = lat;
    p.longitude    = lon;
    p.hasGpsFix    = fix;
    p.battery      = battery::percent();
    p.timestampMs  = net::epochMillis();

    if (net::pushReading(p)) {
      Serial.printf("[push] hr=%d temp=%.1f motion=%s fall=%d batt=%d%% fix=%d\n",
                    p.heartRate, p.temperatureC, toString(p.motion),
                    p.fallDetected, p.battery, fix);
      if (p.fallDetected) sensors::motion::clearFall();   // reported once; app opens the incident
    }
  }

  if (now - s_lastVestLive >= VESTLIVE_INTERVAL_MS) {
    s_lastVestLive = now;
    net::pushVestLive(battery::percent(), net::epochMillis());
  }

  delay(5);
}
