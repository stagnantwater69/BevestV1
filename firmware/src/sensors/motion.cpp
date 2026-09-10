#include "sensors.h"
#include "../config.h"
#include <math.h>

#if !SENSORS_SIMULATED
  #include <Wire.h>
  #include <Adafruit_MPU6050.h>
  #include <Adafruit_Sensor.h>
  static Adafruit_MPU6050 s_mpu;
  static bool s_ok = false;
#endif

namespace sensors { namespace motion {

namespace {
  Motion   s_state    = Motion::UNKNOWN;
  bool     s_fall     = false;
  uint32_t s_lastMove = 0;
  uint32_t s_freefallStart = 0;

  // ── tunables — CALIBRATE on the assembled vest ──
  constexpr float    MOVE_DELTA_G = 0.15f;    // |a|-1g beyond this  => moving
  constexpr float    FREEFALL_G   = 0.45f;    // |a| below this      => free fall
  constexpr float    IMPACT_G     = 2.5f;     // spike after free fall => impact/fall
  constexpr uint32_t INACTIVE_MS  = 120000;   // stationary this long => INACTIVE
}

void begin() {
  s_lastMove = millis();
#if !SENSORS_SIMULATED
  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
  s_ok = s_mpu.begin();
  if (s_ok) {
    s_mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
    s_mpu.setGyroRange(MPU6050_RANGE_500_DEG);
    s_mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  }
#endif
}

void loop() {
  const uint32_t now = millis();

#if SENSORS_SIMULATED
  s_state = Motion::MOVING;
  s_lastMove = now;
  return;
#else
  if (!s_ok) { s_state = Motion::UNKNOWN; return; }

  sensors_event_t a, g, t;
  s_mpu.getEvent(&a, &g, &t);
  const float ax = a.acceleration.x / 9.80665f;
  const float ay = a.acceleration.y / 9.80665f;
  const float az = a.acceleration.z / 9.80665f;
  const float mag = sqrtf(ax * ax + ay * ay + az * az);   // ~1.0 at rest

  // fall = a free-fall window immediately followed by an impact spike
  if (mag < FREEFALL_G) {
    if (s_freefallStart == 0) s_freefallStart = now;
  } else {
    if (s_freefallStart && (now - s_freefallStart) > 80 && mag > IMPACT_G) {
      s_fall = true;
      s_state = Motion::FALL_DETECTED;
    }
    s_freefallStart = 0;
  }

  if (fabsf(mag - 1.0f) > MOVE_DELTA_G) {
    s_lastMove = now;
    if (!s_fall) s_state = Motion::MOVING;
  } else if (!s_fall) {
    s_state = (now - s_lastMove > INACTIVE_MS) ? Motion::INACTIVE : Motion::STATIONARY;
  }
#endif
}

Motion state()   { return s_state; }
bool   fall()    { return s_fall; }
void   clearFall() {
  s_fall = false;
  if (s_state == Motion::FALL_DETECTED) s_state = Motion::STATIONARY;
}

}}  // namespace sensors::motion
