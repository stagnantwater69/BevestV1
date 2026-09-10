#pragma once
#include <Arduino.h>
#include <math.h>
// ─────────────────────────────────────────────────────────────────────────────
// The device -> cloud contract. Mirrors, on the app side:
//   app/src/main/java/com/jtexpress/bevest/data/mapper/RealtimeMappers.kt
//   app/src/main/java/com/jtexpress/bevest/domain/model/Enums.kt
// Do NOT rename these keys or enum strings without changing the app + Cloud Functions.
// ─────────────────────────────────────────────────────────────────────────────

enum class Motion { MOVING, STATIONARY, INACTIVE, FALL_DETECTED, UNKNOWN };

inline const char* toString(Motion m) {
  switch (m) {
    case Motion::MOVING:        return "MOVING";
    case Motion::STATIONARY:    return "STATIONARY";
    case Motion::INACTIVE:      return "INACTIVE";
    case Motion::FALL_DETECTED: return "FALL_DETECTED";
    default:                    return "UNKNOWN";
  }
}

// app SafetyResponseState — the two-way "are you OK?" handshake
namespace SafetyResponse {
  constexpr const char* NONE                = "NONE";
  constexpr const char* WAITING             = "WAITING";              // set by app/cloud -> buzz + wait
  constexpr const char* ACKNOWLEDGED        = "ACKNOWLEDGED";         // worker pressed button
  constexpr const char* EMERGENCY_REQUESTED = "EMERGENCY_REQUESTED";  // worker held button / SOS
  constexpr const char* NO_RESPONSE         = "NO_RESPONSE";          // timed out
  constexpr const char* ESCALATED           = "ESCALATED";            // cloud took over
}

// One live reading -> RTDB  liveReadings/{workerId}
// NOTE: safetyResponse is deliberately NOT part of this struct. It is written on a
// separate path by the safety module only when it changes, so a periodic reading
// push can never clobber an app-initiated WAITING.
struct Payload {
  const char* vestId       = "";
  int         heartRate    = 0;      // bpm; 0 => no valid reading (key omitted)
  float       temperatureC = NAN;    // NAN => no valid reading (key omitted)
  Motion      motion       = Motion::UNKNOWN;
  bool        fallDetected = false;
  double      latitude     = 0.0;
  double      longitude    = 0.0;
  bool        hasGpsFix    = false;  // false => lat/lon keys omitted
  int         battery      = 0;      // percent
  uint64_t    timestampMs  = 0;      // epoch millis (0 => clock not yet set)
};
