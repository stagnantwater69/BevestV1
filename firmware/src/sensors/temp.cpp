#include "sensors.h"
#include "../config.h"

#if !SENSORS_SIMULATED
  #include <OneWire.h>
  #include <DallasTemperature.h>
  static OneWire s_wire(PIN_DS18B20);
  static DallasTemperature s_dallas(&s_wire);
#endif

namespace sensors { namespace temp {

void begin() {
#if !SENSORS_SIMULATED
  s_dallas.begin();
  s_dallas.setResolution(11);          // ~0.125 C, ~375 ms conversion
#endif
}

float readC() {
#if SENSORS_SIMULATED
  return 36.5f + random(-3, 4) / 10.0f;
#else
  s_dallas.requestTemperatures();      // blocking ~375 ms — fine at the reading cadence
  const float c = s_dallas.getTempCByIndex(0);
  return (c <= DEVICE_DISCONNECTED_C) ? NAN : c;
#endif
}

}}  // namespace sensors::temp
