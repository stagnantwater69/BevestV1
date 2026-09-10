/**
 * Vest-level telemetry and health.
 *
 *  - mirrorVestLive : RTDB vestsLive/{vestId}  ->  Firestore vests/{vestId}
 *      The app reads battery / online / lastSeen from the Firestore vest doc;
 *      the firmware only writes the fast-changing RTDB node. This keeps them in sync.
 *
 *  - vestHealthSweep : every 1 min
 *      Raises / clears VEST_OFFLINE (stale lastSeen) and LOW_BATTERY alerts.
 *      System alerts use deterministic ids (vest_offline_{vestId}, low_battery_{vestId})
 *      so there is exactly one open alert of each kind per vest, no dedupe query needed.
 */

import { getFirestore } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { onValueWritten } from "firebase-functions/v2/database";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";
import { DEFAULT_THRESHOLDS, Thresholds } from "./safety";

interface VestLive {
  battery?: number | null;
  online?: boolean | null;
  lastSeen?: number | null;
}

async function thresholds(): Promise<Thresholds> {
  const snap = await getFirestore().doc("settings/thresholds").get();
  if (!snap.exists) return DEFAULT_THRESHOLDS;
  return { ...DEFAULT_THRESHOLDS, ...(snap.data() as Partial<Thresholds>) };
}

export const mirrorVestLive = onValueWritten("/vestsLive/{vestId}", async (event) => {
  const vestId = event.params.vestId as string;
  const v = (event.data.after.val() as VestLive | null) ?? null;
  if (!v) return;

  const ref = getFirestore().doc(`vests/${vestId}`);
  if (!(await ref.get()).exists) {
    logger.warn(`mirrorVestLive: vest ${vestId} is transmitting but not registered`);
    return;
  }

  await ref.update({
    battery: v.battery ?? null,
    online: v.online ?? false,
    lastSeen: v.lastSeen ?? null,
  });
});

async function vestContext(vestId: string, assignedWorkerId: string | undefined) {
  const out = { workerId: assignedWorkerId ?? "", siteId: "", contractorId: "" };
  if (!assignedWorkerId) return out;
  const w = await getFirestore().doc(`workers/${assignedWorkerId}`).get();
  const d = w.data() ?? {};
  out.siteId = (d.siteId as string) ?? "";
  out.contractorId = (d.contractorId as string) ?? "";
  return out;
}

async function raiseVestAlert(
  vestId: string,
  type: "VEST_OFFLINE" | "LOW_BATTERY",
  ctx: { workerId: string; siteId: string; contractorId: string },
  message: string,
  now: number
) {
  const id = `${type.toLowerCase()}_${vestId}`;
  const ref = getFirestore().doc(`alerts/${id}`);
  const snap = await ref.get();
  if (snap.exists && snap.data()?.status === "ACTIVE") return; // already open

  await ref.set({
    workerId: ctx.workerId,
    vestId,
    siteId: ctx.siteId,
    contractorId: ctx.contractorId,
    type,
    severity: "WARNING",
    status: "ACTIVE",
    message,
    createdAt: now,
    acknowledgedAt: null,
    resolvedAt: null,
    resolvedBy: null,
  });
  logger.info(`vestHealthSweep: raised ${type} for ${vestId}`);
}

async function clearVestAlert(vestId: string, type: "VEST_OFFLINE" | "LOW_BATTERY", now: number) {
  const ref = getFirestore().doc(`alerts/${type.toLowerCase()}_${vestId}`);
  const snap = await ref.get();
  if (snap.exists && snap.data()?.status === "ACTIVE") {
    await ref.update({ status: "RESOLVED", resolvedAt: now, resolvedBy: "system" });
    logger.info(`vestHealthSweep: cleared ${type} for ${vestId}`);
  }
}

export const vestHealthSweep = onSchedule("every 1 minutes", async () => {
  const t = await thresholds();
  const now = Date.now();
  const offlineMs = t.offlineTimeoutSeconds * 1000;

  const vests = await getFirestore().collection("vests").get();

  for (const doc of vests.docs) {
    const v = doc.data();
    const vestId = doc.id;
    const assignedWorkerId = v.assignedWorkerId as string | undefined;
    const ctx = await vestContext(vestId, assignedWorkerId);

    // ── VEST_OFFLINE ── only actionable for an in-use vest
    const lastSeen = (v.lastSeen as number) ?? 0;
    const isOffline = now - lastSeen > offlineMs; // never-seen (lastSeen 0) counts as offline
    if (assignedWorkerId && isOffline) {
      if (v.online !== false) await doc.ref.update({ online: false });
      await raiseVestAlert(vestId, "VEST_OFFLINE", ctx, "Vest stopped transmitting", now);
    } else {
      if (!isOffline && v.online !== true) await doc.ref.update({ online: true });
      await clearVestAlert(vestId, "VEST_OFFLINE", now);
    }

    // ── LOW_BATTERY ──
    const battery = v.battery as number | null | undefined;
    if (battery != null && battery <= t.lowBatteryPercent) {
      await raiseVestAlert(vestId, "LOW_BATTERY", ctx, `Vest battery at ${battery}%`, now);
    } else if (battery != null && battery >= t.lowBatteryPercent + 10) {
      // hysteresis so it doesn't flap around the threshold
      await clearVestAlert(vestId, "LOW_BATTERY", now);
    }
  }
});
