/**
 * BeVest authoritative safety engine.
 *
 * Detection runs here — NOT only in the app — so emergencies fire even when no phone
 * has the app open (plan section 16 & 31.1/31.2).
 *
 * Pipeline:
 *   liveReadings/{workerId}  --onWrite-->  evaluateReading
 *     - writes worker.currentStatus
 *     - opens/updates a "condition window" doc when status leaves NORMAL
 *     - a fall opens a FALL_DETECTED alert and pushes to the site's SSOs at once
 *   every 1 min  --schedule-->  escalate
 *     - WARNING longer than warningDuration        -> DANGER (+ buzzer flag)
 *     - DANGER longer than responseTimeout w/o ack -> EMERGENCY -> incident + FCM
 *   incidents/{id}  --onCreate-->  notifyIncident  (siren push to on-duty SSOs,
 *                                                   normal push to the contractor)
 *
 * The siren is for Site Safety Officers only; contractors and admins never get it.
 */

import { initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onValueWritten } from "firebase-functions/v2/database";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";
import {
  DEFAULT_THRESHOLDS,
  Thresholds,
  Reading,
  instantaneousStatus,
  reason,
} from "./safety";

initializeApp();
const db = getFirestore();
const rtdb = getDatabase();

// Vest telemetry mirror + offline/low-battery alerts
export { mirrorVestLive, vestHealthSweep } from "./vests";
// Firestore role -> Auth custom claim (needed by the RTDB rules)
export { syncUserClaims } from "./claims";

async function thresholds(): Promise<Thresholds> {
  const snap = await db.doc("settings/thresholds").get();
  if (!snap.exists) return DEFAULT_THRESHOLDS;
  return { ...DEFAULT_THRESHOLDS, ...(snap.data() as Partial<Thresholds>) };
}

async function workerContext(workerId: string) {
  const snap = await db.doc(`workers/${workerId}`).get();
  const data = snap.data() ?? {};
  return {
    exists: snap.exists,
    vestId: (data.assignedVestId as string) ?? "",
    siteId: (data.siteId as string) ?? "",
    contractorId: (data.contractorId as string) ?? "",
    currentStatus: (data.currentStatus as string) ?? "OFFLINE",
  };
}

async function workerName(workerId: string): Promise<string> {
  const data = (await db.doc(`workers/${workerId}`).get()).data() ?? {};
  const name = `${data.firstName ?? ""} ${data.lastName ?? ""}`.trim();
  return name || `Worker ${workerId}`;
}

const TYPE_LABELS: Record<string, string> = {
  FALL_DETECTED: "Fall detected",
  NO_SAFETY_RESPONSE: "No safety response",
  EMERGENCY_REQUEST: "Emergency requested",
};

function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type;
}

function isFall(reading: Reading): boolean {
  return Boolean(reading.fallDetected) || reading.motionState === "FALL_DETECTED";
}

/**
 * High-priority, data-only push. Data-only so the app's messaging service always builds
 * the notification itself — with the siren, repeating vibration and full-screen alert
 * when `alarm` is set — even while the app is closed. A `notification` block would
 * let Android draw a plain notification instead and skip all of that.
 */
async function send(
  tokens: string[],
  title: string,
  body: string,
  data: Record<string, string>,
  alarm: boolean
): Promise<void> {
  if (tokens.length === 0) return;
  await getMessaging().sendEachForMulticast({
    tokens,
    android: { priority: "high" },
    data: { ...data, title, body, alarm: String(alarm) },
  });
}

/** Siren push to every active SSO on a site — the people who can reach the worker. */
async function pushToSite(
  siteId: string,
  title: string,
  body: string,
  data: Record<string, string>
): Promise<void> {
  const officers = await db
    .collection("users")
    .where("role", "==", "SSO")
    .where("siteId", "==", siteId)
    .where("active", "==", true)
    .get();

  const tokens: string[] = [];
  for (const o of officers.docs) {
    const t = o.data().fcmTokens as string[] | undefined;
    if (t) tokens.push(...t);
  }
  if (tokens.length === 0) {
    logger.warn(`No FCM tokens for site ${siteId}`);
    return;
  }
  await send(tokens, title, body, data, true);
}

/** Normal (never siren) push to the contractor who owns the site. */
async function pushToContractor(
  contractorId: string,
  title: string,
  body: string,
  data: Record<string, string>
): Promise<void> {
  if (!contractorId) return;
  const snap = await db.doc(`users/${contractorId}`).get();
  const user = snap.data();
  if (!user || user.active === false) return;
  await send((user.fcmTokens as string[] | undefined) ?? [], title, body, data, false);
}

/**
 * Open the FALL_DETECTED alert for a condition window, once. The window's
 * `fallAlertId` is claimed in a transaction so back-to-back readings can't open two.
 */
async function openFallAlert(
  windowRef: FirebaseFirestore.DocumentReference,
  workerId: string,
  ctx: { vestId: string; siteId: string; contractorId: string },
  now: number
): Promise<void> {
  const alertRef = db.collection("alerts").doc();
  const created = await db.runTransaction(async (tx) => {
    const w = await tx.get(windowRef);
    if (!w.exists || w.data()?.fallAlertId) return false;
    tx.set(alertRef, {
      workerId,
      vestId: ctx.vestId,
      siteId: ctx.siteId,
      contractorId: ctx.contractorId,
      type: "FALL_DETECTED",
      severity: "DANGER",
      status: "ACTIVE",
      message: "Fall detected",
      createdAt: now,
    });
    tx.update(windowRef, { fallAlertId: alertRef.id });
    return true;
  });
  if (!created) return;

  const name = await workerName(workerId);
  const data = { type: "ALERT", alertId: alertRef.id, workerId };
  await pushToSite(ctx.siteId, "⚠ Fall detected", `${name} may have fallen. Check on them now.`, data);
  await pushToContractor(ctx.contractorId, "Fall detected on your site", `${name} may have fallen.`, data);
}

/**
 * Create exactly one incident + one ACTIVE alert for an emergency. Idempotent on
 * `incidentId`. `notifyIncident` picks up the new incident doc and sends the FCM.
 */
async function openIncident(p: {
  incidentId: string;
  workerId: string;
  vestId?: string;
  siteId?: string;
  contractorId?: string;
  type: "NO_SAFETY_RESPONSE" | "EMERGENCY_REQUEST" | "FALL_DETECTED";
  /** An alert already open for this condition (a fall); upgraded instead of duplicated. */
  alertId?: string;
  heartRate?: number | null;
  temperature?: number | null;
  latitude?: number | null;
  longitude?: number | null;
  message: string;
  now: number;
}): Promise<void> {
  const incidentRef = db.doc(`incidents/${p.incidentId}`);
  if ((await incidentRef.get()).exists) return;

  const common = {
    workerId: p.workerId,
    vestId: p.vestId ?? "",
    siteId: p.siteId ?? "",
    contractorId: p.contractorId ?? "",
    type: p.type,
    severity: "EMERGENCY" as const,
  };

  await incidentRef.set({
    ...common,
    heartRate: p.heartRate ?? null,
    temperature: p.temperature ?? null,
    latitude: p.latitude ?? null,
    longitude: p.longitude ?? null,
    createdAt: p.now,
    outcome: null,
    resolutionNotes: null,
  });
  const alertBody = {
    ...common,
    status: "ACTIVE",
    message: p.message,
    incidentId: p.incidentId,
  };
  const existing = p.alertId ? db.doc(`alerts/${p.alertId}`) : null;
  let alertId: string;
  if (existing && (await existing.get()).exists) {
    await existing.update(alertBody);
    alertId = existing.id;
  } else {
    alertId = (await db.collection("alerts").add({ ...alertBody, createdAt: p.now })).id;
  }
  await incidentRef.update({ alertId });
}

/** Step 1: react to every new reading. */
export const evaluateReading = onValueWritten(
  "/liveReadings/{workerId}",
  async (event) => {
    const workerId = event.params.workerId as string;
    const reading = (event.data.after.val() as Reading | null) ?? null;
    if (!reading) return;

    const t = await thresholds();
    const now = Date.now();
    const ctx = await workerContext(workerId);
    if (!ctx.exists) return;

    const windowRef = db.doc(`conditionWindows/${workerId}`);
    let status = instantaneousStatus(reading, t, now);
    // The vest reports a fall in one reading and clears it on the next. Hold DANGER
    // while the window's fall alert is unresolved, so the next normal reading doesn't
    // close the window (and with it the escalation) before anyone has responded.
    if (status === "NORMAL" || status === "WARNING" || status === "OFFLINE") {
      const fallAlertId = (await windowRef.get()).data()?.fallAlertId as string | undefined;
      if (fallAlertId) {
        const fallAlert = await db.doc(`alerts/${fallAlertId}`).get();
        if (fallAlert.exists && fallAlert.data()?.status !== "RESOLVED") status = "DANGER";
      }
    }

    // Reflect the status on the worker record (never downgrade an open EMERGENCY
    // here — only escalate/resolve does that).
    if (ctx.currentStatus !== "EMERGENCY") {
      await db.doc(`workers/${workerId}`).update({
        currentStatus: status,
        lastReadingAt: reading.timestamp ?? now,
      });
    }

    if (status === "WARNING" || status === "DANGER") {
      const existing = await windowRef.get();
      if (!existing.exists) {
        await windowRef.set({
          workerId,
          vestId: ctx.vestId,
          siteId: ctx.siteId,
          contractorId: ctx.contractorId,
          level: status,
          reason: reason(reading, t),
          startedAt: now,
          acknowledgedAt: null,
          heartRate: reading.heartRate ?? null,
          temperature: reading.temperature ?? null,
          latitude: reading.latitude ?? null,
          longitude: reading.longitude ?? null,
        });
      } else {
        // Keep last-known vitals + location fresh so an incident born from this
        // window carries where the worker actually was.
        const patch: Record<string, unknown> = {
          heartRate: reading.heartRate ?? existing.data()?.heartRate ?? null,
          temperature: reading.temperature ?? existing.data()?.temperature ?? null,
        };
        if (reading.latitude != null) patch.latitude = reading.latitude;
        if (reading.longitude != null) patch.longitude = reading.longitude;
        if (status === "DANGER" && existing.data()?.level === "WARNING") {
          patch.level = "DANGER";
          patch.dangerAt = now;
        }
        await windowRef.update(patch);
      }
      if (reading.safetyResponse === "ACKNOWLEDGED") {
        await windowRef.update({ acknowledgedAt: now });
      }
      // Don't wait for the response timeout to tell anyone about a fall.
      if (status === "DANGER" && isFall(reading)) {
        await openFallAlert(windowRef, workerId, ctx, now);
      }
    } else if (status === "EMERGENCY" && reading.safetyResponse === "EMERGENCY_REQUESTED") {
      // Manual SOS — the worker held the button. (NO_RESPONSE / ESCALATED already
      // flow through escalate()'s DANGER path, so they're excluded here.)
      // The currentStatus guard + openIncident's id check keep this to one incident.
      if (ctx.currentStatus !== "EMERGENCY") {
        const existing = await windowRef.get();
        const startedAt = (existing.data()?.startedAt as number) ?? now;
        await windowRef.set(
          {
            workerId,
            vestId: ctx.vestId,
            siteId: ctx.siteId,
            contractorId: ctx.contractorId,
            level: "EMERGENCY",
            reason: "Manual emergency request",
            startedAt,
            emergencyAt: now,
            acknowledgedAt: null,
            heartRate: reading.heartRate ?? null,
            temperature: reading.temperature ?? null,
            latitude: reading.latitude ?? null,
            longitude: reading.longitude ?? null,
          },
          { merge: true }
        );
        await db.doc(`workers/${workerId}`).update({ currentStatus: "EMERGENCY" });
        await openIncident({
          incidentId: `${workerId}_${startedAt}`,
          workerId,
          vestId: ctx.vestId,
          siteId: ctx.siteId,
          contractorId: ctx.contractorId,
          type: "EMERGENCY_REQUEST",
          heartRate: reading.heartRate ?? null,
          temperature: reading.temperature ?? null,
          latitude: reading.latitude ?? null,
          longitude: reading.longitude ?? null,
          message: "Manual emergency request",
          now,
        });
      }
    } else if (status === "NORMAL") {
      // condition cleared before escalation
      const existing = await windowRef.get();
      if (existing.exists && existing.data()?.level !== "EMERGENCY") {
        await windowRef.delete();
      }
    }
  }
);

/** Step 2: time-based escalation. */
export const escalate = onSchedule("every 1 minutes", async () => {
  const t = await thresholds();
  const now = Date.now();
  const windows = await db.collection("conditionWindows").get();

  for (const doc of windows.docs) {
    const w = doc.data();
    const workerId = w.workerId as string;
    const level = w.level as string;
    const startedAt = w.startedAt as number;
    const dangerAt = (w.dangerAt as number) ?? startedAt;
    const acknowledgedAt = w.acknowledgedAt as number | null;

    if (level === "WARNING" && now - startedAt > t.warningDurationSeconds * 1000) {
      await doc.ref.update({ level: "DANGER", dangerAt: now });
      await db.doc(`workers/${workerId}`).update({ currentStatus: "DANGER" });
      await rtdb.ref(`liveReadings/${workerId}/safetyResponse`).set("WAITING");
      continue;
    }

    if (
      level === "DANGER" &&
      !acknowledgedAt &&
      now - dangerAt > t.responseTimeoutSeconds * 1000
    ) {
      await doc.ref.update({ level: "EMERGENCY", emergencyAt: now });
      await db.doc(`workers/${workerId}`).update({ currentStatus: "EMERGENCY" });
      await rtdb.ref(`liveReadings/${workerId}/safetyResponse`).set("NO_RESPONSE");

      const fell = Boolean(w.fallAlertId) || w.reason === "Fall detected";
      await openIncident({
        incidentId: `${workerId}_${w.startedAt}`,
        workerId,
        vestId: w.vestId,
        siteId: w.siteId,
        contractorId: w.contractorId,
        type: fell ? "FALL_DETECTED" : "NO_SAFETY_RESPONSE",
        alertId: w.fallAlertId ?? undefined,
        heartRate: w.heartRate ?? null,
        temperature: w.temperature ?? null,
        latitude: w.latitude ?? null,
        longitude: w.longitude ?? null,
        message: fell ? "Fall detected — no response" : (w.reason ?? "No safety response"),
        now,
      });
    }
  }
});

/** Step 3: notify on new incident. */
export const notifyIncident = onDocumentCreated("incidents/{incidentId}", async (event) => {
  const incident = event.data?.data();
  if (!incident) return;
  const workerId = String(incident.workerId);
  const body = `${await workerName(workerId)}: ${typeLabel(String(incident.type))}`;
  const data = { type: "INCIDENT", incidentId: event.params.incidentId, workerId };

  await pushToSite(incident.siteId as string, "⚠ EMERGENCY", body, data);
  await pushToContractor(String(incident.contractorId ?? ""), "⚠ Emergency on your site", body, data);
});
