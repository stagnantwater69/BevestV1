#include "safety.h"
#include "../io/alert.h"
#include "../contract.h"
#include "../config.h"

namespace safety {

namespace {
  String   s_state    = SafetyResponse::NONE;
  bool     s_changed  = false;
  uint32_t s_waitStart = 0;

  void setLocal(const char* s) {
    if (s_state != s) {
      s_state = s;
      s_changed = true;   // main() will publish this to Firebase
    }
  }
}

void begin() {
  alert::begin();
}

void onRemoteState(const String& s) {
  // Remote-driven transitions: adopt the value WITHOUT marking it changed,
  // otherwise we'd echo it straight back to Firebase.
  if (s == SafetyResponse::WAITING) {
    if (s_state != SafetyResponse::WAITING) {
      s_state = SafetyResponse::WAITING;
      s_waitStart = millis();
      alert::buzzerOn();
    }
  } else if (s == SafetyResponse::NONE ||
             s == SafetyResponse::ESCALATED ||
             s == SafetyResponse::ACKNOWLEDGED ||
             s == SafetyResponse::NO_RESPONSE) {
    s_state = s;
    s_waitStart = 0;
    alert::buzzerOff();
  }
}

void loop() {
  alert::loop();
  const alert::Press p = alert::pollButton();

  if (s_state == SafetyResponse::WAITING) {
    if (p == alert::Press::SHORT) {
      alert::buzzerOff();
      setLocal(SafetyResponse::ACKNOWLEDGED);
    } else if (p == alert::Press::LONG) {
      alert::buzzerOff();
      setLocal(SafetyResponse::EMERGENCY_REQUESTED);
    } else if (RESPONSE_TIMEOUT_S > 0 &&
               millis() - s_waitStart > (uint32_t)RESPONSE_TIMEOUT_S * 1000UL) {
      alert::buzzerOff();
      setLocal(SafetyResponse::NO_RESPONSE);
    }
  } else if (s_state == SafetyResponse::NONE) {
    // worker-initiated SOS at any time
    if (p == alert::Press::LONG) {
      setLocal(SafetyResponse::EMERGENCY_REQUESTED);
    }
  }
}

const char* current()   { return s_state.c_str(); }
bool consumeChanged()   { const bool c = s_changed; s_changed = false; return c; }

}  // namespace safety
