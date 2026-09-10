#pragma once
#include <Arduino.h>
// The safetyResponse state machine — single source of truth on the device.
//
//  - The app / Cloud Functions drive us into WAITING (or clear us to NONE / ESCALATED)
//    via the RTDB stream -> onRemoteState().
//  - The physical button drives us to ACKNOWLEDGED / EMERGENCY_REQUESTED, or a timeout
//    drives us to NO_RESPONSE -> consumeChanged() then reports true so main() publishes.

namespace safety {
  void begin();                          // also initialises alert (buzzer + button)
  void onRemoteState(const String& s);   // called from the Firebase stream callback
  void loop();                           // services buzzer, button, timeout
  const char* current();                 // current safetyResponse value
  bool consumeChanged();                 // true exactly once after a local state change
}
