#include "sensors.h"
#include "../config.h"

// AD8232 single-lead ECG -> heart rate.
// The analog output rests near mid-rail; each heartbeat is a sharp R-peak spike.
// We threshold-detect peaks (with hysteresis + a refractory interval) and average
// the last few beat-to-beat intervals into a BPM.

namespace sensors { namespace heart {

namespace {
  int      s_bpm       = 0;
  uint32_t s_lastBeat  = 0;
  uint32_t s_lastSample = 0;
  bool     s_above     = false;
  int      s_intervals[4] = {0, 0, 0, 0};
  uint8_t  s_idx       = 0;

  // ── tunables — CALIBRATE against a scope/serial plot of your board ──
  constexpr int PEAK_THRESHOLD = 2200;   // 12-bit counts; ~mid of the R-peak swing
  constexpr int HYSTERESIS     = 200;
}

void begin() {
#if !SENSORS_SIMULATED
  analogReadResolution(12);
  pinMode(PIN_ECG_LOp, INPUT);
  pinMode(PIN_ECG_LOn, INPUT);
#endif
}

void loop() {
#if SENSORS_SIMULATED
  s_bpm = 72 + random(-4, 5);
  return;
#else
  const uint32_t now = millis();
  if (now - s_lastSample < 2) return;    // ~500 Hz sampling
  s_lastSample = now;

  // leads-off detection (AD8232 LO+ / LO- go HIGH when an electrode is loose)
  if (digitalRead(PIN_ECG_LOp) == HIGH || digitalRead(PIN_ECG_LOn) == HIGH) {
    s_bpm = 0;
    s_lastBeat = 0;
    return;
  }

  const int v = analogRead(PIN_ECG_OUT);

  if (!s_above && v > PEAK_THRESHOLD) {
    s_above = true;                       // rising edge across threshold = R-peak
    if (s_lastBeat != 0) {
      const int interval = (int)(now - s_lastBeat);
      if (interval > 300 && interval < 2000) {   // 30..200 bpm plausibility gate
        s_intervals[s_idx++ & 3] = interval;
        long sum = 0; int n = 0;
        for (int i = 0; i < 4; i++) if (s_intervals[i]) { sum += s_intervals[i]; n++; }
        if (n) s_bpm = (int)(60000L / (sum / n));
      }
    }
    s_lastBeat = now;
  } else if (s_above && v < PEAK_THRESHOLD - HYSTERESIS) {
    s_above = false;
  }

  if (s_lastBeat != 0 && now - s_lastBeat > 3000) s_bpm = 0;   // signal lost
#endif
}

int bpm() { return s_bpm; }

}}  // namespace sensors::heart
