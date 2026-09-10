#pragma once
// Li-ion fuel gauge via a resistor-divider on an ADC1 pin.

namespace battery {
  void begin();
  int  percent();   // 0..100 (voltage-curve estimate; calibrate on the real pack)
}
