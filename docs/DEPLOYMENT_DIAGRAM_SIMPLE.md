# BeVest Smart IoT Safety System — Simple Deployment Diagram

This document presents a streamlined, academic-style **Deployment Diagram** for the **BeVest Smart Safety System**, modeled after industry-standard UML deployment specifications.

---

## 1. Visual Deployment Diagram

```mermaid
flowchart TB
    %% ==========================================
    %% STYLING DEFINITIONS
    %% ==========================================
    classDef cloudNode fill:#eff6ff,stroke:#2563eb,stroke-width:2px,color:#0f172a;
    classDef iotNode fill:#fff7ed,stroke:#ea580c,stroke-width:2px,color:#0f172a;
    classDef mobileNode fill:#f0fdf4,stroke:#16a34a,stroke-width:2px,color:#0f172a;
    classDef webNode fill:#faf5ff,stroke:#9333ea,stroke-width:2px,color:#0f172a;
    classDef extNode fill:#f8fafc,stroke:#64748b,stroke-width:1.5px,stroke-dasharray: 4 4,color:#0f172a;
    classDef routerNode fill:#f1f5f9,stroke:#475569,stroke-width:1.5px,color:#0f172a;

    %% ==========================================
    %% TOP LEVEL NODES
    %% ==========================================
    EXT["<b>&lt;External Services&gt;</b><br/><b>Satellite & Cloud Services</b><br/>------------------------------------------<br/>&lt;Satellite&gt; GPS Constellation (NEO-6M NMEA)<br/>&lt;Service&gt; OpenStreetMap Tile Servers (OSM)<br/>&lt;Service&gt; NTP Time Servers (pool.ntp.org)"]:::extNode

    CLOUD["<b>&lt;Cloud Platform&gt;</b><br/><b>Google Firebase Backend (bevest-70698)</b><br/>------------------------------------------<br/>&lt;Service&gt; Firebase Authentication<br/>&lt;Database&gt; Realtime Database (Live Telemetry)<br/>&lt;Database&gt; Cloud Firestore (Management Data)<br/>&lt;Storage&gt; Cloud Storage / Firebase Hosting"]:::cloudNode

    %% ==========================================
    %% FIELD / LOCAL NETWORK NODES
    %% ==========================================
    ROUTER["<b>&lt;Network Gateway&gt;</b><br/><b>Site Wi-Fi Router / Mobile Hotspot</b><br/>------------------------------------------<br/>&lt;Hardware&gt; 2.4 GHz Access Point (802.11 b/g/n)<br/>&lt;Function&gt; Field Internet Uplink (TLS 443)"]:::routerNode

    VEST["<b>&lt;IoT Device&gt;</b><br/><b>BeVest Smart Safety Vest (Field Workers)</b><br/>------------------------------------------<br/>&lt;Hardware&gt; ESP32-WROOM-32 Microcontroller<br/>&lt;Firmware&gt; FreeRTOS / Arduino C++<br/>&lt;Sensors&gt; MAX30102, MPU6050, GPS, DS18B20<br/>&lt;Controls&gt; SOS Push Button, Piezo Buzzer, LED"]:::iotNode

    %% ==========================================
    %% CLIENT APPLICATION NODES
    %% ==========================================
    MOBILE["<b>&lt;Android Device&gt;</b><br/><b>Mobile Application (Site Safety Officers)</b><br/>------------------------------------------<br/>&lt;Application&gt; BeVest.apk<br/>&lt;Framework&gt; Android / Jetpack Compose (Kotlin)<br/>&lt;Libraries&gt; Firebase SDK / OSMDroid Maps<br/>&lt;Hardware&gt; BLE 4.2 / Camera Barcode Scanner"]:::mobileNode

    WEB["<b>&lt;Web Client&gt;</b><br/><b>Web Application (Admins & Contractors)</b><br/>------------------------------------------<br/>&lt;Application&gt; BeVest Management Portal<br/>&lt;Framework&gt; React 19 / Vite SPA<br/>&lt;Components&gt; Chart.js / Leaflet Maps / QRCode<br/>&lt;Hosting&gt; Firebase Hosting (CDN)"]:::webNode

    %% ==========================================
    %% RELATIONSHIPS & DATA PATHWAYS
    %% ==========================================
    EXT -. "RF 1575.42 MHz (NMEA)" .-> VEST
    EXT -. "HTTPS (Map Tiles)" .-> MOBILE
    EXT -. "HTTPS (Map Tiles)" .-> WEB

    VEST -- "BLE GATT (Pairing & Setup)" --> MOBILE
    VEST -- "IEEE 802.11 b/g/n" --> ROUTER
    ROUTER -- "HTTPS / TLS (REST & Heartbeat)" --> CLOUD

    MOBILE <-->|"HTTPS / WebSocket (Firebase SDK)"| CLOUD
    WEB <-->|"HTTPS / WebSocket (Firebase JS SDK)"| CLOUD
```

<div align="center">
  <b>Figure: BeVest System Deployment Diagram</b>
</div>

---

## 2. Text-Based Architecture View

For quick reference or viewing in text-only environments:

```
                          +-------------------------------------------------------+
                          |                  <External Services>                  |
                          |   GPS Satellites | OpenStreetMap | NTP Time Servers   |
                          +-------------------------------------------------------+
                                     :                            :          :
                     RF Signals (GPS):             Map Tiles (OSM):          : Map Tiles (OSM)
                                     v                            v          v
+------------------------------------+   BLE Pairing   +---------------------+
|             <IoT Device>           |---------------->|   <Android Device>  |
|      BeVest Smart Safety Vest      |                 |   Site Safety App   |
| ---------------------------------- |                 | ------------------- |
| • ESP32-WROOM-32 Microcontroller   |                 | • BeVest.apk        |
| • MAX30102 (Heart Rate & SpO2)     |                 | • Jetpack Compose   |
| • MPU6050 (Fall & Motion IMU)      |                 | • OSMDroid Map      |
| • NEO-6M (GPS Location Module)     |                 | • BLE Provisioner   |
| • DS18B20 (Body Temperature)       |                 +---------------------+
| • SOS Button, Buzzer, Status LED   |                            |
+------------------------------------+                            |
                  |                                               |
         Wi-Fi    | (802.11 b/g/n)                                |
                  v                                               |
+------------------------------------+                            |
|         <Network Gateway>          |                            |
| Site Wi-Fi Router / Mobile Hotspot |                            |
+------------------------------------+                            |
                  |                                               |
            HTTPS | (TLS 443)                                     | HTTPS / WebSockets
                  v                                               | (Firebase SDK)
+-------------------------------------------------------+         |
|                    <Cloud Platform>                   |<--------+
|          Google Firebase Backend (bevest-70698)       |
| ----------------------------------------------------- |
| • Firebase Authentication (Role-based access)         |
| • Realtime Database (Live Telemetry & Fall Alerts)    |<--------+
| • Cloud Firestore (Workers, Vests, Sites, Reports)    |         |
| • Firebase Hosting & Cloud Storage                    |         | HTTPS / WebSockets
+-------------------------------------------------------+         | (Firebase JS SDK)
                                                                  |
                                                       +---------------------+
                                                       |     <Web Client>    |
                                                       | Web Admin & Portal  |
                                                       | ------------------- |
                                                       | • React 19 / Vite   |
                                                       | • Contractor Portal |
                                                       | • System Management |
                                                       | • Leaflet Maps      |
                                                       +---------------------+
```

---

## 3. Deployment Nodes Specification

| Node Stereotype | Hardware / Execution Node | Key Artifacts & Components | Communication Protocols |
| :--- | :--- | :--- | :--- |
| **`«IoT Device»`** | **ESP32-WROOM-32**<br/>(Wearable Smart Safety Vest) | • `esp32integration_max30102.ino`<br/>• MAX30102 (Heart Rate / SpO2)<br/>• MPU6050 (Fall / Motion Detection)<br/>• GY-GPS6MV2 / NEO-6M (GPS)<br/>• DS18B20 (Temperature)<br/>• SOS Button (D15) & Buzzer (D25) | • **Wi-Fi 802.11 b/g/n** (to Router)<br/>• **BLE 4.2 GATT** (to Android App)<br/>• **RF 1575.42 MHz** (from GPS) |
| **`«Network Gateway»`** | **Site Wi-Fi Router / Hotspot**<br/>(Field Infrastructure) | • 2.4 GHz Access Point<br/>• Cellular / Broadband Internet Uplink | • **WPA2 / WPA3** local Wi-Fi<br/>• **HTTPS / TLS 443** outbound |
| **`«Cloud Platform»`** | **Google Firebase**<br/>(Project: `bevest-70698`) | • **Firebase Authentication**<br/>• **Realtime Database** (`/liveReadings`, `/vestsLive`)<br/>• **Cloud Firestore** (`users`, `vests`, `workers`, `sites`)<br/>• **Firebase Hosting** (`web-admin/dist`) | • **HTTPS / REST (TLS 443)**<br/>• **Secure WebSockets (WSS)** |
| **`«Android Device»`** | **Android Smartphone**<br/>(Site Safety Officers) | • `BeVest.apk`<br/>• Kotlin / Jetpack Compose UI<br/>• OSMDroid Map View<br/>• BLE Provisioner & QR Camera Scanner | • **HTTPS / WebSocket** (Firebase)<br/>• **BLE 4.2** (Vest Pairing)<br/>• **HTTPS** (OSM Tiles) |
| **`«Web Client»`** | **Desktop / Workstation Browser**<br/>(Contractors & Admins) | • Single Page Application (SPA)<br/>• React 19 + Vite<br/>• Leaflet OpenStreetMap<br/>• Chart.js Incident Analytics | • **HTTPS** (Firebase Hosting)<br/>• **HTTPS / WebSocket** (Firebase JS)<br/>• **HTTPS** (OSM Tiles) |
| **`«External Services»`** | **Satellite & Cloud Providers** | • GPS Satellite Constellation (US DOD)<br/>• OpenStreetMap Public Tile Servers<br/>• NTP Servers (`pool.ntp.org`) | • **RF Broadcast** (L1 Band)<br/>• **HTTPS** (PNG Tiles)<br/>• **UDP 123** (Time Sync) |

---

## 4. Key Architectural Highlights

1. **Lightweight Edge-to-Cloud Pipeline**:
   The smart vest processes fall detection and biometric threshold alerts directly on the ESP32 microcontroller, pushing immediate telemetry every 2.5 seconds to Firebase Realtime Database.
2. **Offline-Resilient Mobile Pairing**:
   Site Safety Officers provision Wi-Fi credentials to the smart vest via **Bluetooth Low Energy (BLE)** directly from the mobile app without requiring pre-configured site networks.
3. **Low-Latency Supervision**:
   Safety officers receive audible alarms on Android devices within sub-second latency via Firebase Realtime Database WebSocket listeners whenever a fall or biometric emergency is flagged.

