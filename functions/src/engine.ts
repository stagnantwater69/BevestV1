import { getFirestore } from "firebase-admin/firestore";

/**
 * Whether these functions are the safety engine, from `settings/engine.mode`.
 *
 * Only one side may open and escalate alerts, or every fall opens two. Until the mode is
 * set to "cloud", officers' phones do it (the app's SafetyWatcher — the default, since
 * the project started on the free plan), and these functions stand down. Pushes for
 * incidents (notifyIncident) and the role claims are sent either way.
 */
export async function cloudEngineActive(): Promise<boolean> {
  const snap = await getFirestore().doc("settings/engine").get();
  return snap.data()?.mode === "cloud";
}
