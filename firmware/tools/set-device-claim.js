// Grant a Firebase Auth user the custom claim { device: true } so the vest firmware
// is allowed to write vestsLive/{vestId} (and, once database.rules.json is tightened,
// liveReadings/{workerId}).
//
// Usage:
//   npm i firebase-admin
//   node set-device-claim.js vest-001@device.bevest ./serviceAccount.json
//
// Get serviceAccount.json from: Firebase console -> Project settings -> Service accounts
// -> Generate new private key.  Do NOT commit it.

const admin = require("firebase-admin");

const [email, keyPath] = process.argv.slice(2);
if (!email || !keyPath) {
  console.error("usage: node set-device-claim.js <email> <serviceAccount.json>");
  process.exit(1);
}

admin.initializeApp({ credential: admin.credential.cert(require(require("path").resolve(keyPath))) });

(async () => {
  const user = await admin.auth().getUserByEmail(email);   // create the user first (console or Admin SDK)
  await admin.auth().setCustomUserClaims(user.uid, { device: true });
  console.log(`ok — { device: true } set on ${email} (uid ${user.uid})`);
  console.log("the device must re-sign-in (restart the ESP32) to pick up the new token");
  process.exit(0);
})().catch((e) => { console.error(e.message); process.exit(1); });
