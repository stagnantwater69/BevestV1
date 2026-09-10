#pragma once
#include <Arduino.h>
// Piezo buzzer + safety-response push button (the vest's "Alert Components").

namespace alert {
  enum class Press { NONE, SHORT, LONG };

  void begin();
  void loop();          // call every iteration — services buzzer pattern + button debounce

  void buzzerOn();      // start the intermittent alert chirp
  void buzzerOff();

  Press pollButton();   // returns a pending press event once, then clears it
}
