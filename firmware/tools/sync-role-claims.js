// Copies every user's Firestore role into a Firebase Auth custom claim { role }.
//
// The Realtime Database rules can't read Firestore, so they rely on this claim to tell
// an SSO or admin from any other signed-in account. On the Blaze plan the
// syncUserClaims Cloud Function keeps it up to date automatically; on the free plan run
// this script after creating, disabling or changing the role of any user.
//
// Usage:
//   npm i firebase-admin
//   node sync-role-claims.js ./serviceAccount.json
//
// Get serviceAccount.json from: Firebase console -> Project settings -> Service accounts
// -> Generate new private key.  Do NOT commit it.

const admin = require("firebase-admin");

const [keyPath] = process.argv.slice(2);
if (!keyPath) {
  console.error("usage: node sync-role-claims.js <serviceAccount.json>");
  process.exit(1);
}

admin.initializeApp({ credential: admin.credential.cert(require(require("path").resolve(keyPath))) });

(async () => {
  const users = await admin.firestore().collection("users").get();
  let changed = 0;
  for (const doc of users.docs) {
    const data = doc.data();
    let user;
    try {
      user = await admin.auth().getUser(doc.id);
    } catch {
      console.warn(`skip ${doc.id}: no Auth account`);
      continue;
    }
    const existing = user.customClaims || {};
    if (existing.device === true) continue; // vest accounts keep their device claim
    const role = data.active !== false && typeof data.role === "string" ? data.role : null;
    if ((existing.role || null) === role) continue;
    const { role: _old, ...others } = existing;
    await admin.auth().setCustomUserClaims(doc.id, role ? { ...others, role } : others);
    console.log(`${data.email || doc.id}: role claim -> ${role || "(cleared)"}`);
    changed++;
  }
  console.log(`done — ${changed} account(s) updated; users pick it up on their next sign-in`);
  process.exit(0);
})().catch((e) => { console.error(e.message); process.exit(1); });
