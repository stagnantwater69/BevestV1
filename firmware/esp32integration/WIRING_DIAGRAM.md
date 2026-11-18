# BeVest Smart IoT Vest — `esp32integration.ino` Hardware Wiring & Architecture Diagram

This document provides the hardware pinout, wiring diagram, sensor bus architecture, and firmware workflow for [`esp32integration.ino`](./esp32integration.ino).

> [!IMPORTANT]
> **Looking for the Latest Multi-GNSS (`NEO-M8N`) & Power Documentation?**
> - **Latest Wiring Diagram (`esp32integration-gnss.ino` + u-blox NEO-M8N)**: [`../esp32integration-gnss/WIRING_DIAGRAM.md`](../esp32integration-gnss/WIRING_DIAGRAM.md)
> - **TP4056 Charger, 18650 Battery Wiring & Battery Life Calculator**: [`../esp32integration-gnss/TP4056_BATTERY_AND_LIFE.md`](../esp32integration-gnss/TP4056_BATTERY_AND_LIFE.md)
> - **ADC Battery SoC Firmware Guide (`GPIO 35`)**: [`../../BATTERY_SOC_IMPLEMENTATION.md`](../../BATTERY_SOC_IMPLEMENTATION.md)

---

## 1. Complete Hardware Wiring Diagram (Mermaid)

```mermaid
flowchart LR
    subgraph POWER["⚡ Power Supply Rails (See TP4056_BATTERY_AND_LIFE.md)"]
        VCC33["3.3V Pin (ESP32)"]
        VIN5V["VIN / 5V Pin (ESP32 <- 5V Boost / USB)"]
        GND["GND Pins (ESP32 Common Ground)"]
    end

    subgraph ESP32["🧠 ESP32 DevKit V1 (30-Pin)"]
        GPIO21["D21 (GPIO 21) — I2C SDA"]
        GPIO22["D22 (GPIO 22) — I2C SCL"]
        GPIO16["RX2 (GPIO 16) — UART2 RX"]
        GPIO17["TX2 (GPIO 17) — UART2 TX"]
        GPIO4["D4 (GPIO 4) — OneWire Data"]
        GPIO15["D15 (GPIO 15) — INPUT_PULLUP"]
        GPIO0["BOOT (GPIO 0) — On-Board Button"]
        GPIO2["D2 (GPIO 2) — On-Board Blue LED"]
        GPIO25["D25 (GPIO 25) — Buzzer Output"]
    end

    subgraph I2C_SENSORS["🔗 Shared I2C Bus (100 kHz Standard Mode)"]
        MPU["🧭 MPU6050 6-Axis IMU\n(I2C Addr: 0x68)\nFall & Motion Detection"]
        MAX["❤️ MAX30102 Pulse Oximeter\n(I2C Addr: 0x57)\nHeart Rate & SpO2"]
    end

    subgraph UART_GPS["🛰️ UART2 Serial (9600 Baud)"]
        GPS["📍 NEO-6M GPS Module\n(Upgraded to NEO-M8N in esp32integration-gnss.ino)"]
    end

    subgraph ONEWIRE_TEMP["🌡️ 1-Wire Bus"]
        DS18["🌡️ DS18B20 Temperature Probe\n(+ 4.7kΩ Pull-up Resistor to 3.3V)"]
    end

    subgraph IO_CONTROLS["🚨 Buttons, Buzzer & Status LED"]
        SOS["🔘 External Safety / SOS Button"]
        BUZ["🔊 Active Buzzer\n(Connected to D25, NOT D2!)"]
        LED["🔵 Built-in Blue LED\n(Hardwired on PCB to D2)"]
    end

    %% I2C Connections
    GPIO21 <-->|"SDA (Data)"| MPU
    GPIO21 <-->|"SDA (Data)"| MAX
    GPIO22 -->|"SCL (100 kHz Clock)"| MPU
    GPIO22 -->|"SCL (100 kHz Clock)"| MAX

    %% GPS UART Connections (Crossed RX/TX)
    GPS -->|"GPS TX -> ESP32 RX2"| GPIO16
    GPIO17 -->|"ESP32 TX2 -> GPS RX"| GPS

    %% Temperature Connection
    GPIO4 <-->|"DQ (Data)"| DS18

    %% Buttons & Indicators
    SOS -->|"Short to GND on Press"| GPIO15
    GPIO2 -->|"Status Blink / Strobe"| LED
    GPIO25 -->|"HIGH = Beep / Siren"| BUZ

    %% Power & Ground
    VCC33 -.->|"3.3V"| MPU
    VCC33 -.->|"3.3V"| MAX
    VIN5V -.->|"5V (or 3.3V)"| GPS
    VCC33 -.->|"3.3V"| DS18
    GND -.->|"GND"| MPU
    GND -.->|"GND"| MAX
    GND -.->|"GND"| GPS
    GND -.->|"GND"| DS18
    GND -.->|"GND"| SOS
    GND -.->|"GND (-)"| BUZ
```

---

## 2. Pin-by-Pin Hardware Mapping Table

| Component | Component Pin | ESP32 Board Label | ESP32 GPIO | Protocol / Mode | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MPU6050** *(Fall & Motion)* | `VCC` | `3V3` | — | Power (`3.3V`) | Shares I²C bus with MAX30102 |
| | `GND` | `GND` | — | Ground | |
| | `SDA` | **`D21`** | `GPIO 21` | I²C Data (`100 kHz`) | Address `0x68` (`AD0` = LOW); `25 ms` timeout recovery |
| | `SCL` | **`D22`** | `GPIO 22` | I²C Clock (`100 kHz`) | |
| **MAX30102** *(Heart Rate & $\text{SpO}_2$)* | `VIN` | `3V3` | — | Power (`3.3V`) | Shares I²C bus with MPU6050 (`I2C_SPEED_STANDARD`) |
| | `GND` | `GND` | — | Ground | |
| | `SDA` | **`D21`** | `GPIO 21` | I²C Data (`100 kHz`) | Address `0x57`; finger threshold `IR >= 50000` |
| | `SCL` | **`D22`** | `GPIO 22` | I²C Clock (`100 kHz`) | |
| **NEO-6M GPS** *(Location)* | `VCC` | `VIN` (`5V`) or `3V3` | — | Power | Cross `TX`/`RX` between GPS & ESP32 |
| | `GND` | `GND` | — | Ground | |
| | `TX` | **`RX2` (`D16`)** | `GPIO 16` | UART2 Receive (`9600`) | GPS transmits $\rightarrow$ ESP32 receives |
| | `RX` | **`TX2` (`D17`)** | `GPIO 17` | UART2 Transmit (`9600`) | ESP32 transmits $\rightarrow$ GPS receives |
| **DS18B20** *(Body Temp)* | `VDD` (Red) | `3V3` | — | Power (`3.3V`) | Requires $4.7\text{ k}\Omega$ resistor between `DQ` & `3V3` |
| | `GND` (Black) | `GND` | — | Ground | |
| | `DQ` (Yellow) | **`D4`** | `GPIO 4` | OneWire Digital | Non-blocking poll every `2s` |
| **Safety / SOS Button** | Pin 1 | **`D15`** | `GPIO 15` | `INPUT_PULLUP` | 1-click (`<15s`) = Safety Check `ACKNOWLEDGED`; Hold 3s = `EMERGENCY_REQUESTED` |
| | Pin 2 | `GND` | — | Ground | |
| **BOOT Button** *(Reset)* | Built-in | **`BOOT`** | `GPIO 0` | `INPUT_PULLUP` | Hold 3s (or double-tap `EN`) = Factory Reset |
| **Built-in Blue LED** | Built-in PCB | **`D2`** | `GPIO 2` | Digital `OUTPUT` | Hardwired to `D2` on ESP32 PCB; blinks status |
| **Active Buzzer** *(Audio)* | `+` (Signal) | **`D25`** | `GPIO 25` | Digital `OUTPUT` | **Must be on `D25` (NOT `D2`)** so LED blinks silently! |
| | `-` (GND) | `GND` | — | Ground | |

---

## 3. Physical ESP32 DevKit V1 Pin Layout Diagram

```text
                       +---------------------------+
                       |      [Micro-USB / C]      |
                       +---------------------------+
                   3V3 | 1                      30 | VIN (5V -> GPS VCC)
                   GND | 2                      29 | GND (Common Ground)
      (SOS Btn) <- D15 | 3                      28 | D13
  (Temp DS18B20) <- D4 | 4                      27 | D12
  (Blue LED PCB) <- D2 | 5   ⚠️ DO NOT WIRE     26 | D14
   (GPS TX ->) <- RX2  | 6      BUZZER TO D2    25 | D27
   (GPS RX <-) <- TX2  | 7      USE D25!        24 | D26
                    D5 | 8                      23 | D25 -> 🔊 ACTIVE BUZZER (+)
                   D18 | 9                      22 | D33
                   D19 | 10                     21 | D32
  (I2C SDA Bus) <- D21 | 11 (MPU6050 + MAX30102)20 | D35
                   RX0 | 12                     19 | D34
                   TX0 | 13                     18 | VN (GPIO 39)
  (I2C SCL Bus) <- D22 | 14 (MPU6050 + MAX30102)17 | VP (GPIO 36)
                   D23 | 15                     16 | EN (Double-Tap = Reset)
                       +---------------------------+
```

---

## 4. Firmware Execution & Cloud Dataflow Diagram

```mermaid
flowchart TD
    BOOT(["⚡ ESP32 Power On / Reset"]) --> CHECK_RST{"Double-Tap EN\nor Hold BOOT 3s?"}
    CHECK_RST -->|"Yes"| CLEAR_NVS["🧹 Clear NVS Flash ('bevest')\nMark Vest Offline in RTDB"]
    CLEAR_NVS --> BLE_MODE

    CHECK_RST -->|"No"| LOAD_NVS["📂 Load NVS ('bevest')\nconfigured, ssid, pass, vestId, workerId"]
    LOAD_NVS --> IS_CFG{"Is Wi-Fi\nConfigured?"}

    IS_CFG -->|"No"| BLE_MODE["🔵 BLE Provisioning Mode\nAdvertise: BEVEST-<MAC>\nBlue LED: Slow Blink (1 Hz)"]
    BLE_MODE --> BLE_RX["📱 Receive JSON over BLE GATT\n{ssid, pass, vestId}\nSave to NVS & Deinit BLE"]
    BLE_RX --> WIFI_INIT

    IS_CFG -->|"Yes"| WIFI_INIT["📶 Connect to Wi-Fi & NTP Time Sync\nSign in to Firebase Auth (vest@bevest.com)\nSync Online=true to RTDB"]

    WIFI_INIT --> MAIN_LOOP["🔄 Main Telemetry Loop (Non-Blocking)"]

    subgraph SENSORS["Physical Sensor Polling"]
        MAIN_LOOP --> S_MPU["🧭 MPU6050 (I2C 100kHz, 0x68)\nFree-fall (<0.65g) -> Impact (>2.8g within 1.2s)\nor Severe Impact (>4.5g)"]
        MAIN_LOOP --> S_MAX["❤️ MAX30102 (I2C 100kHz, 0x57)\nIR Finger Detect (>=50000) -> 4-Beat Avg BPM"]
        MAIN_LOOP --> S_TEMP["🌡️ DS18B20 (GPIO 4)\nBody Temp (°C) every 2s"]
        MAIN_LOOP --> S_GPS["🛰️ NEO-6M GPS (GPIO 16/17)\nLat/Lng/Sats/HDOP"]
    end

    S_MPU -->|"Fall Detected!"| FALL_PUSH["🚨 Immediate Fall Push (fallDetected: true)\nStart 6s Cooldown & Buzzer Siren (D25)\nClear fallDetected=false after Cooldown"]
    S_MPU & S_MAX & S_TEMP & S_GPS --> RTDB_PUSH["☁️ Atomic RTDB Push (Every 2.5s)\n/liveReadings/{workerId}\n/liveReadingsByVest/{vestId}"]
    MAIN_LOOP -->|"Every 10s / 1m"| HB_PUSH["💓 Heartbeat Push (10s) & /vitalLogs (1m)\n/vestsLive/{vestId}\n/vestsLiveByMac/{cleanMac}"]
```

