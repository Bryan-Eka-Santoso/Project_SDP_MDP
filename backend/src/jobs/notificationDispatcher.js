const prisma = require('../lib/prisma');
const { getFcm } = require('../lib/fcm');

// Batas FCM untuk satu panggilan sendEachForMulticast (backend/AGENTS.md aturan 7).
const BATCH_SIZE = 500;

// Token dari aplikasi yang sudah di-uninstall atau token rusak: aman dihapus.
const STALE_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

// FCM data message hanya menerima nilai string.
function toData(payload) {
  return {
    type: payload.type,
    title: payload.title,
    body: payload.body,
    referenceId:
      payload.referenceId === undefined || payload.referenceId === null ? '' : String(payload.referenceId),
    sentAt: new Date().toISOString(),
  };
}

/**
 * Kirim satu notifikasi ke semua perangkat milik `userIds`.
 * Dikirim sebagai data message (tanpa blok `notification`) agar Android selalu memanggil
 * onMessageReceived dan bisa menyimpan riwayat ke Room (ADR-010).
 */
async function dispatch(userIds, payload) {
  const result = { tokens: 0, sent: 0, failed: 0, removed: 0 };

  const ids = [...new Set(userIds)].filter(Number.isInteger);
  if (ids.length === 0) return result;

  const rows = await prisma.deviceToken.findMany({
    where: { userId: { in: ids } },
    select: { token: true },
  });
  const tokens = rows.map((row) => row.token);
  result.tokens = tokens.length;
  if (tokens.length === 0) return result;

  const data = toData(payload);
  const fcm = getFcm();
  if (!fcm) {
    console.info(`[notifikasi] ${data.type} → ${tokens.length} perangkat: ${data.title} | ${data.body}`);
    return result;
  }

  const stale = [];
  for (let i = 0; i < tokens.length; i += BATCH_SIZE) {
    const batch = tokens.slice(i, i + BATCH_SIZE);
    const response = await fcm.sendEachForMulticast({
      tokens: batch,
      data,
      android: { priority: 'high' },
    });
    result.sent += response.successCount;
    result.failed += response.failureCount;
    response.responses.forEach((r, j) => {
      if (!r.success && STALE_TOKEN_CODES.has(r.error?.code)) stale.push(batch[j]);
    });
  }

  if (stale.length > 0) {
    await prisma.deviceToken.deleteMany({ where: { token: { in: stale } } });
    result.removed = stale.length;
  }
  return result;
}

module.exports = { dispatch, BATCH_SIZE };
