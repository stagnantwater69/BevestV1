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
 *   every 1 min  --schedule-->  escalate
 *     - WARNING longer than warningDuration        -> DANGER (+ buzzer flag)
 *     - DANGER longer than responseTimeout w/o ack -> EMERGENCY -> incident + FCM
 *   incidents/{id}  --onCreate-->  notifyIncident  (high-priority FCM to on-duty SSOs)
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

/** Step 1: react to every new reading. */
export const evaluateReading = onValueWritten(
  "/liveReadings/{workerId}",
  async (event) => {
    const workerId = event.params.workerId as string;
    const reading = (event.data.after.val() as Reading | null) ?? null;
    if (!reading) return;

    const t = await thresholds();
    const now = Date.now();
    const status = instantaneousStatus(reading, t, now);
    const ctx = await workerContext(workerId);
    if (!ctx.exists) return;

    // Reflect the instantaneous status on the worker record (never downgrade an
    // open EMERGENCY here — only escalate/resolve does that).
    if (ctx.currentStatus !== "EMERGENCY") {
      await db.doc(`workers/${workerId}`).update({
        currentStatus: status,
        lastReadingAt: reading.timestamp ?? now,
      });
    }

    const windowRef = db.doc(`conditionWindows/${workerId}`);
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
        });
      } else if (status === "DANGER" && existing.data()?.level === "WARNING") {
        await windowRef.update({ level: "DANGER", dangerAt: now });
      }
      if (reading.safetyResponse === "ACKNOWLEDGED") {
        await windowRef.update({ acknowledgedAt: now });
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

      // Create exactly one incident + alert per window.
      const incidentId = `${workerId}_${w.startedAt}`;
      const incidentRef = db.doc(`incidents/${incidentId}`);
      if (!(await incidentRef.get()).exists) {
        await incidentRef.set({
          workerId,
          vestId: w.vestId ?? "",
          siteId: w.siteId ?? "",
          contractorId: w.contractorId ?? "",
          type: "NO_SAFETY_RESPONSE",
          severity: "EMERGENCY",
          heartRate: w.heartRate ?? null,
          temperature: w.temperature ?? null,
          latitude: null,
          longitude: null,
          createdAt: now,
          outcome: null,
          resolutionNotes: null,
        });
        const alertRef = await db.collection("alerts").add({
          workerId,
          vestId: w.vestId ?? "",
          siteId: w.siteId ?? "",
          contractorId: w.contractorId ?? "",
          type: "NO_SAFETY_RESPONSE",
          severity: "EMERGENCY",
          status: "ACTIVE",
          message: w.reason ?? "No safety response",
          createdAt: now,
          incidentId,
        });
        await incidentRef.update({ alertId: alertRef.id });
      }
    }
  }
});

/** Step 3: notify on new incident. */
export const notifyIncident = onDocumentCreated("incidents/{incidentId}", async (event) => {
  const incident = event.data?.data();
  if (!incident) return;
  const siteId = incident.siteId as string;

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

  await getMessaging().sendEachForMulticast({
    tokens,
    notification: {
      title: "⚠ EMERGENCY",
      body: `Worker ${incident.workerId}: ${incident.type}`,
    },
    android: { priority: "high", notification: { channelId: "critical" } },
    data: {
      type: "INCIDENT",
      incidentId: event.params.incidentId,
      workerId: String(incident.workerId),
    },
  });
});
