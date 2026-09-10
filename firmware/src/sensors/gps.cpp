#include "sensors.h"
#include "../config.h"

#if !SENSORS_SIMULATED
  #include <TinyGPSPlus.h>
  static TinyGPSPlus s_gps;
#endif

namespace sensors { namespace gps {

void begin() {
#if !SENSORS_SIMULATED
  Serial2.begin(9600, SERIAL_8N1, PIN_GPS_RX, PIN_GPS_TX);
#endif
}

void loop() {
#if !SENSORS_SIMULATED
  while (Serial2.available()) s_gps.encode(Serial2.read());
#endif
}

bool location(double& lat, double& lon) {
#if SENSORS_SIMULATED
  lat = 14.5995; lon = 120.9842;   // Manila — stand-in fix
  return true;
#else
  if (s_gps.location.isValid() && s_gps.location.age() < 5000) {
    lat = s_gps.location.lat();
    lon = s_gps.location.lng();
    return true;
  }
  return false;
#endif
}

}}  // namespace sensors::gps
