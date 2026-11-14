const prisma = require('../../lib/prisma');
const { dispatch } = require('../../jobs/notificationDispatcher');
const { NOTIFICATION_TYPES } = require('../../config/constants');

const MAX_TITLE = 100;
const MAX_BODY = 500;

const truncate = (text, max) => (text.length > max ? `${text.slice(0, max - 1)}…` : text);

/**
 * Kirim notifikasi FCM ke user tertentu tanpa menunggu (ADR-010).
 *
 * Panggil SETELAH transaksi data berhasil, jangan di dalam `$transaction`, agar notifikasi
 * tidak terkirim untuk data yang batal disimpan. Contoh:
 *
 *   const change = await prisma.$transaction(...);
 *   notify(userIds, { type: 'SCHEDULE_CHANGE', title: 'Perubahan jadwal', body: '...', referenceId: change.id });
 *
 * `type` wajib salah satu NOTIFICATION_TYPES. Teks `title`/`body` dalam bahasa Indonesia.
 * Kegagalan pengiriman hanya dicatat di log, tidak memengaruhi response API.
 */
function notify(userIds, { type, title, body, referenceId }) {
  // Kesalahan pemanggil (bug), bukan kesalahan pengguna: lempar Error biasa.
  if (!NOTIFICATION_TYPES.includes(type)) {
    throw new Error(`Jenis notifikasi tidak dikenal: ${type}`);
  }
  if (!title || !body) throw new Error('Notifikasi wajib punya title dan body.');

  const payload = {
    type,
    title: truncate(title, MAX_TITLE),
    body: truncate(body, MAX_BODY),
    referenceId,
  };

  setImmediate(() => {
    dispatch(userIds, payload).catch((err) => {
      console.error(`[notifikasi] gagal mengirim ${type}:`, err);
    });
  });
}

// Satu token hanya milik satu akun: jika HP yang sama dipakai login akun lain, token pindah.
async function registerDeviceToken(userId, token) {
  await prisma.deviceToken.upsert({
    where: { token },
    update: { userId },
    create: { token, userId },
  });
}

module.exports = { notify, registerDeviceToken };
