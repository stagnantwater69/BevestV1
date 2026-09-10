#pragma once
#include "../contract.h"
// Sensor drivers. Each module is independent — write and bench-test one as its
// breakout board arrives; the others keep returning simulated values until then
// (SENSORS_SIMULATED in config.h, or an unresponsive board at runtime).

namespace sensors {

  namespace temp   { void begin(); float readC(); }                     // DS18B20
  namespace motion { void begin(); void loop();                         // MPU6050
                     Motion state(); bool fall(); void clearFall(); }
  namespace gps    { void begin(); void loop();                         // NEO-6M
                     bool location(double& lat, double& lon); }
  namespace heart  { void begin(); void loop(); int bpm(); }            // AD8232

  inline void begin() { temp::begin(); motion::begin(); gps::begin(); heart::begin(); }
  inline void loop()  { motion::loop(); gps::loop(); heart::loop(); }   // non-blocking servicing
}
