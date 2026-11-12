const prisma = require('../lib/prisma');
const { getFcm } = require('../lib/fcm');

// Batas FCM untuk satu panggilan sendEachForMulticast (backend/AGENTS.md aturan 7).
const BATCH_SIZE = 500;

// Token dari aplikasi yang sudah di-uninstall atau token rusak: aman dihapus.
const STALE_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);
const INVALID_ARGUMENT_CODE = 'messaging/invalid-argument';

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
  const failureCodes = {};
  for (let i = 0; i < tokens.length; i += BATCH_SIZE) {
    const batch = tokens.slice(i, i + BATCH_SIZE);
    const response = await fcm.sendEachForMulticast({
      tokens: batch,
      data,
      android: { priority: 'high' },
    });
    result.sent += response.successCount;
    result.failed += response.failureCount;
    // invalid-argument bisa berarti token malformed atau payload salah; hanya dianggap token rusak
    // bila batch ini punya pengiriman sukses (payload terbukti valid).
    const payloadProvenValid = response.successCount > 0;
    response.responses.forEach((r, j) => {
      if (r.success) return;
      const code = r.error?.code ?? 'unknown';
      failureCodes[code] = (failureCodes[code] ?? 0) + 1;
      if (STALE_TOKEN_CODES.has(code) || (payloadProvenValid && code === INVALID_ARGUMENT_CODE)) {
        stale.push(batch[j]);
      }
    });
  }

  if (stale.length > 0) {
    await prisma.deviceToken.deleteMany({ where: { token: { in: stale } } });
    result.removed = stale.length;
  }

  // Ringkasan tanpa token, agar kegagalan FCM (kredensial, token kedaluwarsa) terlihat di log.
  const failedInfo = result.failed > 0 ? ` (${JSON.stringify(failureCodes)})` : '';
  console.info(
    `[notifikasi] ${data.type}: ${result.sent}/${result.tokens} terkirim, ${result.failed} gagal${failedInfo}, ${result.removed} token dihapus.`,
  );
  return result;
}

module.exports = { dispatch, BATCH_SIZE };
