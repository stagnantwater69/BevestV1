// Security-rules tests. Run with `npm test` (starts the emulators via the Firebase CLI).
// Each block pairs what must be denied with the legitimate use that must still work.

import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, collection, query, where, getDocs } from "firebase/firestore";
import { ref, set, get } from "firebase/database";
import { ref as sref, getBytes, uploadString } from "firebase/storage";

const root = new URL("../", import.meta.url);
const read = (f) => readFileSync(new URL(f, root), "utf8");

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-bevest",
    firestore: { rules: read("firestore.rules") },
    database: { rules: read("database.rules.json") },
    storage: { rules: read("storage.rules") },
  });
});

after(async () => env?.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.clearDatabase();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const user = (role, extra = {}) => ({ role, active: true, email: `${role}@x.test`, ...extra });
    await setDoc(doc(db, "users/admin1"), user("ADMIN"));
    await setDoc(doc(db, "users/c1"), user("CONTRACTOR", { contractorId: "c1" }));
    await setDoc(doc(db, "users/c2"), user("CONTRACTOR"));
    await setDoc(doc(db, "users/sso1"), user("SSO", { contractorId: "c1", siteId: "SITE-A" }));
    await setDoc(doc(db, "users/sso2"), user("SSO", { contractorId: "c2", siteId: "SITE-B" }));
    await setDoc(doc(db, "workers/W1"), { siteId: "SITE-A", contractorId: "c1", active: true });
    await setDoc(doc(db, "workers/W2"), { siteId: "SITE-B", contractorId: "c2", active: true });
    await setDoc(doc(db, "vests/V1"), { assignedWorkerId: "W2", status: "ASSIGNED" });
    await setDoc(doc(db, "vests/V2"), { assignedWorkerId: null, status: "AVAILABLE" });
    await setDoc(doc(db, "projects/P2"), { contractorId: "c2", name: "Site B" });
    await setDoc(doc(db, "alerts/AL-A"), { siteId: "SITE-A", contractorId: "c1", status: "ACTIVE", type: "FALL_DETECTED" });
    await setDoc(doc(db, "incidents/IN-A"), { siteId: "SITE-A", contractorId: "c1", outcome: null });

    await set(ref(ctx.database(), "liveReadings/W1"), { vestId: "V9", timestamp: 1, heartRate: 80, safetyResponse: "NONE" });
    await uploadString(sref(ctx.storage(), "reports/c1/2026-10.pdf"), "pdf");
    await uploadString(sref(ctx.storage(), "reports/c2/2026-10.pdf"), "pdf");
  });
});

const as = (uid, claims = {}) => env.authenticatedContext(uid, claims);
const fs = (uid, claims) => as(uid, claims).firestore();

describe("users", () => {
  test("a contractor can't list every user, only their own officers", async () => {
    await assertFails(getDocs(collection(fs("c1"), "users")));
    await assertSucceeds(getDocs(query(collection(fs("c1"), "users"), where("contractorId", "==", "c1"))));
  });

  test("nobody changes their own email or role; profile edits still work", async () => {
    await assertFails(updateDoc(doc(fs("sso1"), "users/sso1"), { email: "new@x.test" }));
    await assertFails(updateDoc(doc(fs("sso1"), "users/sso1"), { role: "ADMIN" }));
    await assertSucceeds(updateDoc(doc(fs("sso1"), "users/sso1"), { phone: "0917", mustChangePassword: false }));
  });

  test("a contractor edits their officer but not the officer's email", async () => {
    await assertFails(updateDoc(doc(fs("c1"), "users/sso1"), { email: "x@x.test" }));
    await assertSucceeds(updateDoc(doc(fs("c1"), "users/sso1"), { siteId: "SITE-C" }));
  });
});

describe("projects", () => {
  test("a contractor can't take over another contractor's project", async () => {
    await assertFails(updateDoc(doc(fs("c1"), "projects/P2"), { contractorId: "c1" }));
    await assertSucceeds(updateDoc(doc(fs("c2"), "projects/P2"), { name: "Renamed" }));
  });
});

describe("alerts", () => {
  test("an SSO can't raise an alert (simulated or not) on another site", async () => {
    const a = { siteId: "SITE-B", contractorId: "c2", status: "ACTIVE", simulated: true };
    await assertFails(setDoc(doc(fs("sso1"), "alerts/x"), a));
    await assertSucceeds(setDoc(doc(fs("sso1"), "alerts/y"), { ...a, siteId: "SITE-A", contractorId: "c1" }));
  });

  test("a contractor's simulator only raises simulated alerts for themselves", async () => {
    await assertFails(setDoc(doc(fs("c1"), "alerts/x"), { siteId: "SITE-B", contractorId: "c2", simulated: true }));
    await assertFails(setDoc(doc(fs("c1"), "alerts/y"), { siteId: "SITE-A", contractorId: "c1", simulated: false }));
    await assertSucceeds(setDoc(doc(fs("c1"), "alerts/z"), { siteId: "SITE-A", contractorId: "c1", simulated: true }));
  });

  test("an SSO can't move an alert to another site; acknowledging still works", async () => {
    await assertFails(updateDoc(doc(fs("sso1"), "alerts/AL-A"), { siteId: "SITE-B" }));
    await assertSucceeds(updateDoc(doc(fs("sso1"), "alerts/AL-A"), { status: "ACKNOWLEDGED" }));
  });

  test("a contractor can only resolve their alert, nothing more", async () => {
    await assertFails(updateDoc(doc(fs("c1"), "alerts/AL-A"), { status: "ACTIVE", severity: "WARNING" }));
    await assertSucceeds(updateDoc(doc(fs("c1"), "alerts/AL-A"), { status: "RESOLVED", resolutionNotes: "ok" }));
    await assertFails(updateDoc(doc(fs("c2"), "alerts/AL-A"), { status: "RESOLVED" }));
  });
});

describe("incidents", () => {
  test("a contractor closes out an incident but can't rewrite it", async () => {
    await assertSucceeds(updateDoc(doc(fs("c1"), "incidents/IN-A"), { outcome: "Treated", resolutionNotes: "fine" }));
    await assertFails(updateDoc(doc(fs("c1"), "incidents/IN-A"), { siteId: "SITE-B" }));
  });
});

describe("workers", () => {
  test("an SSO can't overwrite another site's worker by reusing its ID", async () => {
    await assertFails(setDoc(doc(fs("sso1"), "workers/W2"), { siteId: "SITE-A", contractorId: "c1" }));
  });

  test("an SSO creates workers only on their site and for their contractor", async () => {
    await assertFails(setDoc(doc(fs("sso1"), "workers/W3"), { siteId: "SITE-A", contractorId: "c2" }));
    await assertSucceeds(setDoc(doc(fs("sso1"), "workers/W4"), { siteId: "SITE-A", contractorId: "c1", active: true }));
  });

  test("a free worker ID can be checked, a taken one elsewhere can't be read", async () => {
    await assertSucceeds(getDoc(doc(fs("sso1"), "workers/W-FREE")));
    await assertFails(getDoc(doc(fs("sso1"), "workers/W2")));
  });
});

describe("worker ID counter", () => {
  test("only ever moves forward", async () => {
    await assertSucceeds(setDoc(doc(fs("sso1"), "counters/workers"), { last: 5 }));
    await assertFails(setDoc(doc(fs("sso1"), "counters/workers"), { last: 3 }));
    await assertSucceeds(setDoc(doc(fs("sso2"), "counters/workers"), { last: 6 }));
  });
});

describe("shifts", () => {
  const shift = (site, contractorId, startedBy) => ({
    siteId: site, contractorId, date: "2026-10-29", startedBy, startedByName: "x",
    workerCount: 2, readyCount: 1, issues: ["W1: No vest paired"],
  });

  test("an SSO watches and starts today's shift only on their own site", async () => {
    await assertSucceeds(getDoc(doc(fs("sso1"), "shifts/SITE-A_2026-10-29")));
    await assertFails(getDoc(doc(fs("sso1"), "shifts/SITE-B_2026-10-29")));
    await assertFails(setDoc(doc(fs("sso1"), "shifts/SITE-B_2026-10-29"), shift("SITE-B", "c2", "sso1")));
    await assertSucceeds(setDoc(doc(fs("sso1"), "shifts/SITE-A_2026-10-29"), shift("SITE-A", "c1", "sso1")));
  });

  test("a recheck can't rewrite who started the shift; the contractor can read it", async () => {
    await assertSucceeds(setDoc(doc(fs("sso1"), "shifts/SITE-A_2026-10-29"), shift("SITE-A", "c1", "sso1")));
    await assertFails(updateDoc(doc(fs("sso1"), "shifts/SITE-A_2026-10-29"), { startedBy: "someone" }));
    await assertSucceeds(updateDoc(doc(fs("sso1"), "shifts/SITE-A_2026-10-29"), { readyCount: 2, issues: [], checkedBy: "sso1" }));
    await assertSucceeds(getDoc(doc(fs("c1"), "shifts/SITE-A_2026-10-29")));
    await assertFails(getDoc(doc(fs("c2"), "shifts/SITE-A_2026-10-29")));
  });
});

describe("alerts: responding", () => {
  test("an SSO marks an alert as theirs with their name", async () => {
    await assertSucceeds(updateDoc(doc(fs("sso1"), "alerts/AL-A"), {
      status: "ACKNOWLEDGED", acknowledgedBy: "sso1", acknowledgedByName: "Officer One",
    }));
  });
});

describe("vests", () => {
  test("an SSO can't touch a vest worn on another site", async () => {
    await assertFails(updateDoc(doc(fs("sso1"), "vests/V1"), { assignedWorkerId: null, status: "AVAILABLE" }));
  });

  test("an SSO pairs a free vest only to a worker on their site", async () => {
    await assertFails(updateDoc(doc(fs("sso1"), "vests/V2"), { assignedWorkerId: "W2" }));
    await assertSucceeds(updateDoc(doc(fs("sso1"), "vests/V2"), { assignedWorkerId: "W1", status: "ASSIGNED" }));
  });

  test("only admins register vests; the vest firmware may read its own doc", async () => {
    await assertFails(setDoc(doc(fs("sso1"), "vests/V9"), { status: "AVAILABLE" }));
    await assertSucceeds(getDoc(doc(fs("vest-device", { device: true }), "vests/V1")));
  });
});

describe("live readings (Realtime Database)", () => {
  const rtdb = (uid, claims) => as(uid, claims).database();

  test("an SSO's simulator can't overwrite a real vest's readings", async () => {
    await assertFails(set(ref(rtdb("sso1", { role: "SSO" }), "liveReadings/W1"), { timestamp: 2, simulated: true }));
  });

  test("simulators write only simulated nodes", async () => {
    const db = rtdb("sso1", { role: "SSO" });
    await assertFails(set(ref(db, "liveReadings/W7"), { timestamp: 2 }));
    await assertSucceeds(set(ref(db, "liveReadings/W7"), { timestamp: 2, simulated: true }));
  });

  test("the vest firmware writes real readings", async () => {
    await assertSucceeds(set(ref(rtdb("vest-device", { device: true }), "liveReadings/W1"), { timestamp: 3, simulated: false }));
  });

  test("safetyResponse only takes known states, and contractors only on simulated nodes", async () => {
    await assertFails(set(ref(rtdb("sso1", { role: "SSO" }), "liveReadings/W1/safetyResponse"), "HACKED"));
    await assertSucceeds(set(ref(rtdb("sso1", { role: "SSO" }), "liveReadings/W1/safetyResponse"), "ACKNOWLEDGED"));
    await assertFails(set(ref(rtdb("c1", { role: "CONTRACTOR" }), "liveReadings/W1/safetyResponse"), "NONE"));
  });

  test("anyone signed in reads live data, nobody signed out does", async () => {
    await assertSucceeds(get(ref(rtdb("c2", { role: "CONTRACTOR" }), "liveReadings/W1")));
    await assertFails(get(ref(env.unauthenticatedContext().database(), "liveReadings/W1")));
  });
});

describe("report PDFs (Storage)", () => {
  test("a contractor reads only their own reports", async () => {
    await assertFails(getBytes(sref(as("c1").storage(), "reports/c2/2026-10.pdf")));
    await assertSucceeds(getBytes(sref(as("c1").storage(), "reports/c1/2026-10.pdf")));
    await assertSucceeds(getBytes(sref(as("admin1").storage(), "reports/c2/2026-10.pdf")));
  });
});
