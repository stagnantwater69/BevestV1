# BeVest Smart IoT Vest — `esp32integration.ino` Hardware Wiring & Architecture Diagram

This document provides the complete hardware pinout, wiring diagram, sensor bus architecture, and firmware workflow for [`esp32integration.ino`](file:///c:/Users/User/Documents/GitHub/BevestV1/firmware/esp32integration/esp32integration.ino).

---

## 1. Complete Hardware Wiring Diagram (Mermaid)

```mermaid
flowchart LR
    subgraph POWER["⚡ Power Supply Rails"]
        VCC33["3.3V Pin (ESP32)"]
        VIN5V["VIN / 5V Pin (ESP32)"]
        GND["GND Pins (ESP32 Common Ground)"]
    end

    subgraph ESP32["🧠 ESP32 DevKit V1 (30-Pin / 38-Pin)"]
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

    subgraph I2C_SENSORS["🔗 Shared I2C Bus (400 kHz Fast Mode)"]
        MPU["🧭 MPU6050 6-Axis IMU\n(I2C Addr: 0x68)\nFall & Motion Detection"]
        MAX["❤️ MAX30102 Pulse Oximeter\n(I2C Addr: 0x57)\nHeart Rate & SpO2"]
    end

    subgraph UART_GPS["🛰️ UART2 Serial (9600 Baud)"]
        GPS["📍 NEO-6M GPS Module\nReal-Time Coordinates & Sats"]
    end

    subgraph ONEWIRE_TEMP["🌡️ 1-Wire Bus"]
        DS18["🌡️ DS18B20 Temperature Probe\n(+ 4.7kΩ Pull-up Resistor to 3.3V)"]
    end

    subgraph IO_CONTROLS["🚨 Buttons, Buzzer & Status LED"]
        SOS["🔘 External SOS Push Button"]
        BUZ["🔊 Active Buzzer\n(Connected to D25, NOT D2!)"]
        LED["🔵 Built-in Blue LED\n(Hardwired on PCB to D2)"]
    end

    %% I2C Connections
    GPIO21 <-->|"SDA (Data)"| MPU
    GPIO21 <-->|"SDA (Data)"| MAX
    GPIO22 -->|"SCL (Clock)"| MPU
    GPIO22 -->|"SCL (Clock)"| MAX

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
    VCC33 -.->|"3.3V (or 5V VIN)"| GPS
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
| | `SDA` | **`D21`** | `GPIO 21` | I²C Data (`400 kHz`) | Address `0x68` (`AD0` = LOW) |
| | `SCL` | **`D22`** | `GPIO 22` | I²C Clock (`400 kHz`) | |
| **MAX30102** *(Heart Rate & $\text{SpO}_2$)* | `VIN` | `3V3` | — | Power (`3.3V`) | Shares I²C bus with MPU6050 |
| | `GND` | `GND` | — | Ground | |
| | `SDA` | **`D21`** | `GPIO 21` | I²C Data (`400 kHz`) | Address `0x57` |
| | `SCL` | **`D22`** | `GPIO 22` | I²C Clock (`400 kHz`) | |
| **NEO-6M GPS** *(Location)* | `VCC` | `VIN` (`5V`) or `3V3` | — | Power | Cross `TX`/`RX` between GPS & ESP32 |
| | `GND` | `GND` | — | Ground | |
| | `TX` | **`RX2` (`D16`)** | `GPIO 16` | UART2 Receive (`9600`) | GPS transmits $\rightarrow$ ESP32 receives |
| | `RX` | **`TX2` (`D17`)** | `GPIO 17` | UART2 Transmit (`9600`) | ESP32 transmits $\rightarrow$ GPS receives |
| **DS18B20** *(Body Temp)* | `VDD` (Red) | `3V3` | — | Power (`3.3V`) | Requires $4.7\text{ k}\Omega$ resistor between `DQ` & `3V3` |
| | `GND` (Black) | `GND` | — | Ground | |
| | `DQ` (Yellow) | **`D4`** | `GPIO 4` | OneWire Digital | |
| **SOS Button** *(Emergency)* | Pin 1 | **`D15`** | `GPIO 15` | `INPUT_PULLUP` | Short press = SOS; Hold 3s = Factory Reset |
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
    BOOT(["⚡ ESP32 Power On / Reset"]) --> CHECK_RST{"Double-Tap EN\nor Hold BOOT/SOS 3s?"}
    CHECK_RST -->|"Yes"| CLEAR_NVS["🧹 Clear NVS Flash ('bevest')\nMark Vest Offline in Firebase"]
    CLEAR_NVS --> BLE_MODE

    CHECK_RST -->|"No"| LOAD_NVS["📂 Load NVS ('bevest')\nconfigured, ssid, pass, vestId, workerId"]
    LOAD_NVS --> IS_CFG{"Is Wi-Fi\nConfigured?"}

    IS_CFG -->|"No"| BLE_MODE["🔵 BLE Provisioning Mode\nAdvertise: BEVEST-<MAC>\nBlue LED: Slow Blink (1 Hz)"]
    BLE_MODE --> BLE_RX["📱 Receive JSON over BLE GATT\n{ssid, pass, vestId, workerId}\nSave to NVS & Deinit BLE"]
    BLE_RX --> WIFI_INIT

    IS_CFG -->|"Yes"| WIFI_INIT["📶 Connect to Wi-Fi & NTP Time Sync\nSign in to Firebase Auth (Anonymous)\nSync Online=true to Firestore & RTDB"]

    WIFI_INIT --> MAIN_LOOP["🔄 Main Telemetry Loop (Non-Blocking)"]

    subgraph SENSORS["Physical Sensor Polling"]
        MAIN_LOOP --> S_MPU["🧭 MPU6050 (I2C 0x68)\nFree-fall (<0.55g) -> Impact (>4.5g within 1.2s)"]
        MAIN_LOOP --> S_MAX["❤️ MAX30102 (I2C 0x57)\nIR Finger Detect (>4500) -> BPM & SpO2"]
        MAIN_LOOP --> S_TEMP["🌡️ DS18B20 (GPIO 4)\nBody Temp (°C) every 2s"]
        MAIN_LOOP --> S_GPS["🛰️ NEO-6M GPS (GPIO 16/17)\nLat/Lng/Sats/HDOP"]
    end

    S_MPU -->|"Fall Detected!"| FALL_PUSH["🚨 Immediate Single Push (Fall:1)\nStart 6s/15s Local Buzzer Siren (D25)\nThen Clear fallDetected=false for Cooldown"]
    S_MPU & S_MAX & S_TEMP & S_GPS --> RTDB_PUSH["☁️ Atomic Multi-Path RTDB Push (Every 1s–3s)\n/liveReadings/{workerId}\n/liveReadingsByVest/{vestId}\n/liveReadingsByMac/{cleanMac}"]
    MAIN_LOOP -->|"Every 10s"| HB_PUSH["💓 Atomic Heartbeat Push\n/vestsLive/{vestId}\n/vestsLiveByMac/{cleanMac}"]
```

