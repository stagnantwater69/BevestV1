# BeVest — running the app

Full status of every phase: see the build plan. This file is the "make it run" checklist.

## 1. Firebase console (project `bevest-70698`)

Status:

- **Authentication** → Email/Password — enabled
- **Firestore Database** — created (`asia-southeast1`), rules + indexes deployed
- **Realtime Database** — created (`asia-southeast1`), rules deployed, URL in `google-services.json`
- **Cloud Messaging** — available (no setup needed)
- **Storage** — enable in the console if not already; then `firebase deploy --only storage`

## 2. Deploy rules & indexes

```bash
npm i -g firebase-tools
firebase login
firebase deploy --only firestore:rules,firestore:indexes,database,storage
```

## 3. Create the first admin (bootstrap)

Rules require an admin to create other users, so make the first one by hand:

1. Firebase console → Authentication → Add user (email + password).
2. Copy that user's UID.
3. Firestore → create collection `users`, document id = that UID:

```
firstName: "Site"
lastName:  "Admin"
email:     "admin@bevest.com"
phone:     ""
role:      "ADMIN"
contractorId: null
siteId:    null
active:    true
```

Now sign in to the app as that admin. Admin can create Contractors; each Contractor
creates their SSOs; SSOs register workers and pair vests.

## 4. Maps key

`local.properties` → `MAPS_API_KEY=...` (Maps SDK for Android, restricted to
`com.jtexpress.bevest` + your debug SHA-1 from `./gradlew signingReport`).

## 5. Cloud Functions (needs Blaze plan)

```bash
cd functions && npm install && npm run deploy
```

Until deployed, safety **status** still shows in the app (client-side
`SafetyStatusEngine`), but time-based escalation, incident creation and push
notifications do not fire.

## 6. Test without hardware — IoT simulation

Debug builds carry a full simulator that stands in for the ESP32 vests **and** the
cloud safety engine, reachable from the wrench icon on the SSO **and** Contractor
dashboards ("IoT simulation").

**As an SSO** — the live view:
1. Open it, tap **Go live**. Every worker's vest starts transmitting realistic,
   drifting vitals + GPS. Leave the screen and browse the app — the dashboard,
   worker detail, and live map are all now "connected".
2. Tap a scenario chip on a worker (High heart rate, Fall detected, Manual
   emergency, …). It escalates on a fast timeline: WARNING → DANGER (raises an
   `alerts` doc) → EMERGENCY (raises an `incidents` doc, fires the red banner).
   **Escalate to emergency now** skips the wait.
3. Set the worker back to **Healthy** to auto-resolve the open alert.

**As a Contractor** — the rollup:
- Tap **Seed 6 months of history** to fill the project with ~4–8 past incidents
  per worker. The dashboard charts, safety score, monthly reports, and incident
  history populate immediately.

**Both roles**: **Reset** stops the stream and permanently deletes every record
flagged `simulated: true`. Real data is never touched. `firestore.rules` only lets
signed-in users create/delete `simulated == true` docs — production incidents still
require the cloud engine.

> Realtime Database: **created** (`asia-southeast1`), rules deployed, and its URL
> is in `google-services.json`. Live streaming works.

## 7. App Check (recommended before a public release)

Firestore/RTDB/Storage are currently reachable by anything holding the API key.
To lock traffic to genuine app builds:

1. Firebase console → App Check → register the Android app with **Play Integrity**.
2. Add the SDK (catalog entries are stubbed in `gradle/libs.versions.toml`):
   ```kotlin
   implementation("com.google.firebase:firebase-appcheck-playintegrity")
   debugImplementation("com.google.firebase:firebase-appcheck-debug")
   ```
3. In `BeVestApplication.onCreate()` install the provider factory
   (`DebugAppCheckProviderFactory` for debug, `PlayIntegrityAppCheckProviderFactory`
   for release), then paste the debug token from Logcat into the console.
4. Turn on **enforcement** per service only once real clients report success.

## 8. Release build

`release` now runs R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) with
keep rules in `proguard-rules.pro`. Add a `signingConfig` before shipping, and
smoke-test a signed release build on a device — R8 changes are not covered by unit
tests.

## 9. Thresholds

Firestore `settings/thresholds` (optional — defaults are used if absent):

```
heartRateHigh: 100
temperatureHigh: 38.0
warningDurationSeconds: 300
responseTimeoutSeconds: 15
offlineTimeoutSeconds: 60
```
