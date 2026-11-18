/**
 * Mirrors each user's Firestore role into a Firebase Auth custom claim.
 *
 * Why: the Realtime Database security rules cannot read Firestore (where roles
 * live), so they cannot tell an SSO from any other signed-in user. With a
 * `role` claim on the token, `database.rules.json` can allow SSO/Admin to write
 * `liveReadings` (the in-app tools and the "check on worker" handshake) while
 * everyone else is blocked.
 *
 * The vest firmware carries its own `device: true` claim, set out-of-band by
 * firmware/tools/set-device-claim.js — this function never touches those accounts.
 *
 * NOTE: a client already signed in must refresh its ID token to pick up a new
 * claim (automatic within ~1 h, or immediately on next sign-in). See SETUP.md.
 */

import { onDocumentDeleted, onDocumentWritten } from "firebase-functions/v2/firestore";
import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions";

export const syncUserClaims = onDocumentWritten("users/{uid}", async (event) => {
  const uid = event.params.uid as string;
  const after = event.data?.after.data();
  if (!after) return; // Deletion is handled by disableAuthUser.

  let user;
  try {
    user = await getAuth().getUser(uid);
  } catch (e) {
    // The users/{uid} doc can be created before (or without) an Auth account.
    logger.warn(`syncUserClaims: no Auth user for ${uid} (${(e as Error).message})`);
    return;
  }

  const existing = (user.customClaims ?? {}) as { role?: string; device?: boolean };
  if (existing.device === true) return; // never demote a provisioned device account

  // Inactive user -> clear the role claim.
  const nextRole =
    after.active !== false && typeof after.role === "string" ? after.role : null;

  if ((existing.role ?? null) === nextRole) return;

  await getAuth().setCustomUserClaims(uid, nextRole ? { role: nextRole } : {});
  logger.info(`syncUserClaims: ${uid} role claim -> ${nextRole ?? "(cleared)"}`);
});

/**
 * Disables the corresponding Firebase Authentication user whenever a Firestore
 * `users/{uid}` document is deleted (by Developer, Administrator, or Contractor).
 */
export const disableAuthUser = onDocumentDeleted("users/{uid}", async (event) => {
  const uid = event.params.uid as string;

  let user;
  try {
    user = await getAuth().getUser(uid);
  } catch (e) {
    const code = (e as { code?: string }).code;
    if (code === "auth/user-not-found") {
      logger.info(`disableAuthUser: Auth user ${uid} already absent`);
      return;
    }
    throw e;
  }

  const claims = (user.customClaims ?? {}) as { device?: boolean };
  if (claims.device === true) {
    logger.warn(`disableAuthUser: skipping provisioned device account ${uid}`);
    return;
  }

  await getAuth().setCustomUserClaims(uid, {});
  if (!user.disabled) {
    await getAuth().updateUser(uid, { disabled: true });
  }
  logger.info(`disableAuthUser: disabled Firebase Auth account ${uid}`);
});
