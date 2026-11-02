# BeVest — Smart Wearable IoT Vest Firmware

This directory contains the unified production firmware for the **BeVest** smart safety vest. It integrates full hardware sensor acquisition, emergency alerts, Firebase Realtime Database telemetry, and in-app Bluetooth Low Energy (BLE) Wi-Fi provisioning.

---

## ⚠️ Important Arduino IDE Setup (Avoid Compilation Error)

Because this sketch combines both the **Bluetooth LE (Bluedroid)** stack and the **Firebase Realtime Database SSL Client**, the compiled binary is approximately ~1.6 MB.

The default ESP32 flash partition table only allocates 1.3 MB (`1,310,720 bytes`) for application code. If you compile using the default setting, you will see:
`Sketch uses 1642087 bytes (125%) ... text section exceeds available space in board`

### Fix in Arduino IDE:
1. Open Arduino IDE.
2. Go to **Tools** > **Board** and ensure your ESP32 board is selected (e.g. `ESP32 Dev Module`).
3. Go to **Tools** > **Partition Scheme** and select:
   👉 **`Huge APP (3MB No OTA/1MB SPIFFS)`**
   *(or `Minimal SPIFFS (1.9MB APP with OTA/190KB SPIFFS)`)*
4. Click **Verify / Upload**. The sketch will compile and upload with plenty of room to spare (~52% of 3MB).

---

## Required Libraries
Install the following via the Arduino IDE **Library Manager** (`Ctrl+Shift+I`):
1. **Firebase ESP32 Client** by *Mobizt*
2. **TinyGPSPlus** by *Mikal Hart*
3. **Adafruit MPU6050** + **Adafruit Sensor**
4. **SparkFun MAX3010x Pulse and Proximity Sensor Library**
5. **DallasTemperature** + **OneWire** by *Miles Burton / Paul Stoffbjbnregen*

---

## Hardware Pinout & Wiring

| Component | ESP32 GPIO | Description / Protocol |
| :--- | :--- | :--- |
| **GPS NEO-6M TX** | **GPIO 16** | HardwareSerial 2 (ESP32 RX2) |
| **GPS NEO-6M RX** | **GPIO 17** | HardwareSerial 2 (ESP32 TX2) |
| **MPU6050 SDA** | **GPIO 21** | I2C Shared Data Bus |
| **MPU6050 SCL** | **GPIO 22** | I2C Shared Clock Bus |
| **MAX30102 SDA** | **GPIO 21** | I2C Shared Data Bus |
| **MAX30102 SCL** | **GPIO 22** | I2C Shared Clock Bus |
| **DS18B20 Data** | **GPIO 4** | OneWire (requires 4.7kΩ pullup to 3.3V) |
| **SOS Emergency Button** | **GPIO 15** | Push button to GND (`INPUT_PULLUP`) |
| **Onboard BOOT Button** | **GPIO 0** | Built-in button on ESP32 board |
| **Onboard Blue LED** | **GPIO 2** | Pairing blinker / Online indicator |
| **Active Buzzer** | **GPIO 25** | Emergency Siren / Beeps *(or GPIO 2 if shared)* |

---

## In-App BLE Pairing Workflow

1. **First Boot / Unpaired**:
   - The onboard blue LED blinks (500ms cycle).
   - ESP32 broadcasts BLE service: `4fafc201-1fb5-459e-8fcc-c5c9c331914b`.
   - Broadcast Name: `BEVEST-<CLEAN_MAC>` (e.g. `BEVEST-582ABD717D42`).
2. **SSO Connects in Mobile App**:
   - SSO taps **Add Vest** in the Bevest mobile app.
   - The app scans and displays discovered vests with name and signal strength.
   - SSO taps **Connect**, then scans the physical QR code on the vest.
   - Optional: SSO enters site Wi-Fi name and password.
3. **Provisioning & Telemetry**:
   - The app transmits credentials over BLE characteristic `beb5483e-36e1-4688-b7f5-ea07361b26a8`.
   - ESP32 connects to Wi-Fi and saves credentials permanently to flash (NVS).
   - ESP32 notifies status `2` (Success) to the app.
   - **ESP32 shuts down the BLE radio** to reclaim ~60KB of RAM for Firebase TLS communication.
   - Onboard LED stays solid ON and live streaming begins.

---

## How to Factory Reset / Re-Pair

If you move to a new Wi-Fi network or need to re-pair the vest, you do not need to reflash the code:

- **Method 1 (EN / RST Double-Click)**:
  Quickly double-press the **EN** (RST) button on the ESP32 board (like double-clicking a mouse).
- **Method 2 (BOOT Button Hold)**:
  Hold the onboard **BOOT** button (GPIO 0) down for **3 seconds**.
- **Method 3 (SOS Button Hold)**:
  Hold the external **SOS** button (GPIO 15) down for **3 seconds**.

The buzzer will beep 4 times, saved credentials will be wiped from flash, and the ESP32 will reboot directly back into BLE Pairing Mode.

