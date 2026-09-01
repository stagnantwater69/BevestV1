# BeVest Cloud Functions

The authoritative safety engine. Detection runs here — not only in the app — so
emergencies fire even when no phone has the app open (plan §16, §31.1–31.2).

## Functions

| Function | Trigger | Job |
|---|---|---|
| `evaluateReading` | RTDB write `liveReadings/{workerId}` | Write `worker.currentStatus`; open/update a `conditionWindows/{workerId}` doc when status leaves NORMAL |
| `escalate` | schedule, every 1 min | WARNING > `warningDurationSeconds` → DANGER; DANGER > `responseTimeoutSeconds` without ack → EMERGENCY → one incident + one alert |
| `notifyIncident` | Firestore create `incidents/{id}` | High-priority FCM multicast to active SSOs on that site |

## Setup

```bash
cd functions
npm install
npm run build
npm test                       # jest — pure safety logic
firebase emulators:start       # local test with the Emulator Suite
npm run deploy                 # requires Blaze plan
```

Thresholds come from Firestore `settings/thresholds`; defaults live in `src/safety.ts`
and must match the Android `SafetyStatusEngine` / `ThresholdConfig`.

## Device auth

The ESP32 firmware writes to `liveReadings/{workerId}` and `vestsLive/{vestId}`.
`database.rules.json` allows writes from clients whose auth token has `device: true`
(a custom claim) or from SSO/Admin users. Mint device tokens with the Admin SDK.
