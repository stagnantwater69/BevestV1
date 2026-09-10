#include "battery.h"
#include "../config.h"
#include <Arduino.h>

namespace battery {

void begin() {
#if !SENSORS_SIMULATED
  analogReadResolution(12);
  analogSetPinAttenuation(PIN_VBAT, ADC_11db);   // full ~0..3.3V range at the divider node
#endif
}

int percent() {
#if SENSORS_SIMULATED
  return 87;
#else
  uint32_t mv = 0;
  for (int i = 0; i < 16; i++) mv += analogReadMilliVolts(PIN_VBAT);
  mv /= 16;

  const float vbat = (mv / 1000.0f) * VBAT_DIVIDER;

  // Coarse linear map, 3.30 V -> 0%, 4.20 V -> 100%.
  // TODO: replace with a measured discharge curve for the actual 3000 mAh cell.
  float pct = (vbat - 3.30f) / (4.20f - 3.30f) * 100.0f;
  if (pct < 0)   pct = 0;
  if (pct > 100) pct = 100;
  return (int)(pct + 0.5f);
#endif
}

}  // namespace battery
