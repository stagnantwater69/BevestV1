#include "alert.h"
#include "../config.h"

namespace alert {

namespace {
  constexpr int      BUZZER_CH   = 0;
  constexpr uint32_t DEBOUNCE_MS = 40;
  constexpr uint32_t LONG_MS     = 1500;
  constexpr uint32_t CHIRP_MS    = 200;   // buzzer on/off half-period

  bool     s_buzzing   = false;
  bool     s_tone      = false;
  uint32_t s_lastChirp = 0;

  bool     s_btnStable   = false;   // debounced "is pressed"
  bool     s_btnRaw      = false;
  uint32_t s_btnChangeMs = 0;
  uint32_t s_pressStart  = 0;
  Press    s_event       = Press::NONE;
}

void begin() {
  pinMode(PIN_BUTTON, INPUT_PULLUP);
  ledcSetup(BUZZER_CH, 2731 /* Hz */, 8 /* bit */);
  ledcAttachPin(PIN_BUZZER, BUZZER_CH);
  ledcWrite(BUZZER_CH, 0);
}

void buzzerOn()  { s_buzzing = true; }
void buzzerOff() { s_buzzing = false; s_tone = false; ledcWrite(BUZZER_CH, 0); }

void loop() {
  const uint32_t now = millis();

  // ── buzzer: intermittent chirp while active ──
  if (s_buzzing && now - s_lastChirp >= CHIRP_MS) {
    s_lastChirp = now;
    s_tone = !s_tone;
    ledcWrite(BUZZER_CH, s_tone ? 128 : 0);
  }

  // ── button: debounce, classify short vs long press on release ──
  const bool raw = (digitalRead(PIN_BUTTON) == LOW);   // active-low
  if (raw != s_btnRaw) { s_btnRaw = raw; s_btnChangeMs = now; }

  if (now - s_btnChangeMs > DEBOUNCE_MS && raw != s_btnStable) {
    s_btnStable = raw;
    if (s_btnStable) {
      s_pressStart = now;                              // pressed
    } else {
      const uint32_t held = now - s_pressStart;        // released
      s_event = (held >= LONG_MS) ? Press::LONG : Press::SHORT;
    }
  }
}

Press pollButton() {
  const Press e = s_event;
  s_event = Press::NONE;
  return e;
}

}  // namespace alert
