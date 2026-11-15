#pragma once
#include <Arduino.h>
#include "../contract.h"
// WiFi + NTP + Firebase Realtime Database transport.

namespace net {
  bool begin();                                  // WiFi -> NTP -> Firebase sign-in
  bool ready();                                  // link + token both healthy
  void loop();                                   // housekeeping (call every iteration)

  uint64_t epochMillis();                        // 0 until the clock is set

  // Re-reads vests/{VEST_ID}.assignedWorkerId and re-points the reading path and the
  // safetyResponse stream when the app has paired or unpaired the vest. Returns true
  // if the assignment changed.
  bool syncAssignment();
  bool assigned();                               // paired with a worker right now

  bool pushReading(const Payload& p);            // -> liveReadings/{workerId}   (PATCH, no safetyResponse)
  bool pushVestLive(int battery, uint64_t nowMs);// -> vestsLive/{vestId}        (needs device claim)

  void beginSafetyStream();                      // stream liveReadings/{workerId}/safetyResponse
  bool writeSafetyResponse(const char* value);   // device-side transition

  const char* workerId();
}
