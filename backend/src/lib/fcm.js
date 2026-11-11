const fs = require('node:fs');
const { initializeApp, cert, getApps } = require('firebase-admin/app');
const { getMessaging } = require('firebase-admin/messaging');
const env = require('../config/env');

let messaging;

/**
 * Klien FCM, dibuat sekali saat pertama dipakai.
 * Mengembalikan `null` jika FIREBASE_SERVICE_ACCOUNT_PATH kosong: notifikasi hanya dicatat
 * di log (mode pengembangan sebelum proyek Firebase siap).
 */
function getFcm() {
  if (messaging !== undefined) return messaging;

  const path = env.firebaseServiceAccountPath;
  if (!path) {
    console.warn('[fcm] FIREBASE_SERVICE_ACCOUNT_PATH kosong: notifikasi hanya dicatat di log.');
    messaging = null;
    return messaging;
  }

  let serviceAccount;
  try {
    serviceAccount = JSON.parse(fs.readFileSync(path, 'utf8'));
  } catch (err) {
    // Tidak disimpan ke cache agar setelah file diperbaiki tidak perlu restart server.
    throw new Error(`[fcm] File service account tidak bisa dibaca (${path}): ${err.message}`);
  }

  const app = getApps()[0] ?? initializeApp({ credential: cert(serviceAccount) });
  messaging = getMessaging(app);
  return messaging;
}

module.exports = { getFcm };
