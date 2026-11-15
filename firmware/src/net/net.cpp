#include "net.h"
#include "../config.h"
#include "../logic/safety.h"

// Older config.h files predate these; see config.example.h.
#ifndef FIREBASE_PROJECT_ID
#define FIREBASE_PROJECT_ID "bevest-70698"
#endif

#include <WiFi.h>
#include <time.h>
#include <math.h>

#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"   // provides tokenStatusCallback
#include "addons/RTDBHelper.h"

namespace {

FirebaseData   s_fbdo;      // one-shot writes
FirebaseData   s_stream;    // dedicated stream connection
FirebaseAuth   s_auth;
FirebaseConfig s_config;

String s_workerId   = WORKER_ID;        // "" while the vest is not paired
String s_readingPath;                  // /liveReadings/{workerId}
String s_safetyPath;                   // /liveReadings/{workerId}/safetyResponse
String s_vestPath;                     // /vestsLive/{vestId}
bool   s_streamStarted = false;

void streamCallback(FirebaseStream data) {
  // Fires on any change under s_safetyPath. When streaming a leaf, dataPath is "/".
  if (data.dataType() == "string") {
    safety::onRemoteState(data.to<String>());
  }
}

void streamTimeoutCallback(bool timeout) {
  if (timeout) Serial.println(F("[net] stream timeout — auto-resuming"));
  if (!s_stream.httpConnected())
    Serial.printf("[net] stream error: %s\n", s_stream.errorReason().c_str());
}

}  // namespace

namespace net {

bool begin() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print(F("[net] wifi "));
  uint32_t t0 = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - t0 < 30000) { delay(300); Serial.print('.'); }
  Serial.println();
  if (WiFi.status() != WL_CONNECTED) { Serial.println(F("[net] wifi FAILED")); return false; }
  Serial.printf("[net] wifi ok, ip=%s\n", WiFi.localIP().toString().c_str());

  // NTP — needed for the `timestamp` field and TLS cert validation
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  struct tm tm;
  for (int i = 0; i < 20 && !getLocalTime(&tm, 500); i++) delay(100);

  s_config.api_key      = FIREBASE_API_KEY;
  s_config.database_url  = FIREBASE_DATABASE_URL;
  s_auth.user.email      = DEVICE_EMAIL;
  s_auth.user.password   = DEVICE_PASSWORD;
  s_config.token_status_callback = tokenStatusCallback;
  s_config.timeout.serverResponse = 10 * 1000;

  Firebase.begin(&s_config, &s_auth);
  Firebase.reconnectWiFi(true);
  s_fbdo.setBSSLBufferSize(4096, 1024);
  s_stream.setBSSLBufferSize(4096, 1024);

  s_vestPath    = String("/vestsLive/") + VEST_ID;
  if (s_workerId.length() > 0) {           // WORKER_ID fallback until Firestore answers
    s_readingPath = String("/liveReadings/") + s_workerId;
    s_safetyPath  = s_readingPath + "/safetyResponse";
  }

  Serial.print(F("[net] firebase token "));
  t0 = millis();
  while (!Firebase.ready() && millis() - t0 < 20000) { delay(200); Serial.print('.'); }
  Serial.println();

  if (!Firebase.ready()) { Serial.println(F("[net] firebase sign-in FAILED")); return false; }
  Serial.println(F("[net] firebase ready"));
  syncAssignment();
  return true;
}

namespace {
void pointAt(const String& workerId) {
  if (s_streamStarted) {
    Firebase.RTDB.endStream(&s_stream);
    s_streamStarted = false;
  }
  s_workerId    = workerId;
  s_readingPath = String("/liveReadings/") + s_workerId;
  s_safetyPath  = s_readingPath + "/safetyResponse";
}
}  // namespace

bool syncAssignment() {
  if (!ready()) return false;
  const String doc = String("vests/") + VEST_ID;
  if (!Firebase.Firestore.getDocument(&s_fbdo, FIREBASE_PROJECT_ID, "", doc.c_str(), "assignedWorkerId")) {
    Serial.printf("[net] assignment lookup failed: %s\n", s_fbdo.errorReason().c_str());
    return false;   // keep the current assignment
  }
  FirebaseJson payload;
  payload.setJsonData(s_fbdo.payload());
  FirebaseJsonData field;
  payload.get(field, "fields/assignedWorkerId/stringValue");
  const String next = field.success ? field.to<String>() : String("");

  if (next == s_workerId) return false;
  if (next.isEmpty()) {
    pointAt("");
    Serial.println(F("[net] vest not paired — readings paused until an officer pairs it"));
  } else {
    pointAt(next);
    beginSafetyStream();
    Serial.printf("[net] paired with worker=%s\n", next.c_str());
  }
  return true;
}

bool assigned() { return s_workerId.length() > 0; }

bool ready() { return WiFi.status() == WL_CONNECTED && Firebase.ready(); }

void loop() {
  // Firebase_ESP_Client services streams internally; nothing required here.
}

uint64_t epochMillis() {
  const time_t now = time(nullptr);
  if (now < 1700000000) return 0;             // clock not set yet
  return (uint64_t)now * 1000ULL;
}

bool pushReading(const Payload& p) {
  if (!ready() || !assigned()) return false;

  FirebaseJson j;
  j.set("vestId", p.vestId);
  if (p.heartRate > 0)         j.set("heartRate", p.heartRate);
  if (!isnan(p.temperatureC))  j.set("temperature", (double)p.temperatureC);
  j.set("motionState", toString(p.motion));
  j.set("fallDetected", p.fallDetected);
  if (p.hasGpsFix) {
    j.set("latitude", p.latitude);
    j.set("longitude", p.longitude);
  }
  j.set("battery", p.battery);
  j.set("timestamp", (double)p.timestampMs);
  j.set("simulated", false);   // a real vest takes the node back from the simulator

  // PATCH — never touches safetyResponse, so an app-initiated WAITING can't be clobbered.
  const bool ok = Firebase.RTDB.updateNode(&s_fbdo, s_readingPath.c_str(), &j);
  if (!ok) Serial.printf("[net] pushReading failed: %s\n", s_fbdo.errorReason().c_str());
  return ok;
}

bool pushVestLive(int battery, uint64_t nowMs) {
  if (!ready()) return false;

  FirebaseJson j;
  j.set("battery", battery);
  j.set("online", true);
  j.set("lastSeen", (double)nowMs);

  const bool ok = Firebase.RTDB.updateNode(&s_fbdo, s_vestPath.c_str(), &j);
  if (!ok) Serial.printf("[net] pushVestLive failed: %s (device claim set?)\n",
                         s_fbdo.errorReason().c_str());
  return ok;
}

void beginSafetyStream() {
  if (s_streamStarted || !assigned()) return;
  if (!Firebase.RTDB.beginStream(&s_stream, s_safetyPath.c_str()))
    Serial.printf("[net] beginStream failed: %s\n", s_stream.errorReason().c_str());
  Firebase.RTDB.setStreamCallback(&s_stream, streamCallback, streamTimeoutCallback);
  s_streamStarted = true;
  Serial.printf("[net] streaming %s\n", s_safetyPath.c_str());
}

bool writeSafetyResponse(const char* value) {
  if (!ready() || !assigned()) return false;
  const bool ok = Firebase.RTDB.setString(&s_fbdo, s_safetyPath.c_str(), value);
  if (!ok) Serial.printf("[net] writeSafetyResponse failed: %s\n", s_fbdo.errorReason().c_str());
  return ok;
}

const char* workerId() { return s_workerId.c_str(); }

}  // namespace net
