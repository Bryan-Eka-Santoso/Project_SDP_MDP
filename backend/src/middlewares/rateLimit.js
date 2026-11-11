// Pembatas laju dalam memori (tanpa dependency) untuk endpoint yang bisa dipakai menebak password:
// login, refresh, dan ganti password. Cocok untuk satu instance server; counter hilang saat restart.
const AppError = require('../utils/AppError');

const MINUTE = 60 * 1000;
const SWEEP_INTERVAL = MINUTE;

// Semua penyimpanan counter, agar bisa dibersihkan berkala dan di-reset oleh tes.
const stores = new Set();

const sweeper = setInterval(() => {
  const now = Date.now();
  for (const hits of stores) {
    for (const [key, entry] of hits) if (entry.resetAt <= now) hits.delete(key);
  }
}, SWEEP_INTERVAL);
sweeper.unref(); // tidak menahan proses (mis. Jest) agar bisa berhenti

function tooMany(retryAfterSeconds) {
  const minutes = Math.max(1, Math.ceil(retryAfterSeconds / 60));
  return new AppError(429, 'TOO_MANY_REQUESTS', `Terlalu banyak percobaan. Coba lagi dalam ${minutes} menit.`);
}

/**
 * Pembatas laju dengan jendela tetap.
 *
 * - `key(req)`: kunci hitungan (mis. IP); `null` berarti tidak dibatasi untuk request ini.
 * - Hitungan **ditambah saat request masuk** (bukan setelah selesai), supaya request paralel
 *   tidak bisa lolos sebelum counter naik.
 * - `refundOn(status)`: setelah respons selesai, kurangi hitungan jika true (mis. login yang
 *   berhasil tidak dihitung sebagai percobaan gagal).
 * - `clearOn(status)`: setelah respons selesai, hapus hitungan kunci itu sepenuhnya (mis. login
 *   berhasil membersihkan percobaan gagal sebelumnya).
 */
function createLimiter({ windowMs, limit, key, refundOn = () => false, clearOn = () => false }) {
  const hits = new Map();
  stores.add(hits);

  return (req, res, next) => {
    const id = key(req);
    if (id === null || id === undefined) return next();

    const now = Date.now();
    let entry = hits.get(id);
    if (!entry || entry.resetAt <= now) {
      entry = { count: 0, resetAt: now + windowMs };
      hits.set(id, entry);
    }

    if (entry.count >= limit) {
      const retryAfter = Math.ceil((entry.resetAt - now) / 1000);
      res.set('Retry-After', String(retryAfter));
      return next(tooMany(retryAfter));
    }

    entry.count += 1;
    res.on('finish', () => {
      if (clearOn(res.statusCode)) hits.delete(id);
      else if (refundOn(res.statusCode) && entry.count > 0) entry.count -= 1;
    });
    return next();
  };
}

// Percobaan login yang ditolak karena email/password salah (bukan kesalahan format atau server).
const isFailedLogin = (status) => status === 401;

const clientIp = (req) => req.ip;
const emailOf = (req) => {
  const email = req.body && req.body.email;
  return typeof email === 'string' ? email.trim().toLowerCase().slice(0, 100) : null;
};

const LOGIN_WINDOW = 15 * MINUTE;

/**
 * Login. Dua lapis, keduanya hanya menghitung login yang GAGAL:
 * - per (IP + email): 8 kali → menghambat tebak password satu akun dari satu sumber. Login yang
 *   berhasil membersihkannya. Dikunci bersama IP supaya orang lain tidak bisa mengunci akun korban
 *   dari IP berbeda.
 * - per IP: 100 kali → menghambat tebak banyak akun. Longgar karena satu jaringan sekolah
 *   (NAT) bisa dipakai banyak siswa sekaligus.
 */
const loginLimits = [
  createLimiter({
    windowMs: LOGIN_WINDOW,
    limit: 8,
    key: (req) => {
      const email = emailOf(req);
      return email === null ? null : `${clientIp(req)}|${email}`;
    },
    refundOn: (status) => !isFailedLogin(status),
    clearOn: (status) => status === 200,
  }),
  createLimiter({
    windowMs: LOGIN_WINDOW,
    limit: 100,
    key: clientIp,
    refundOn: (status) => !isFailedLogin(status),
  }),
];

// Refresh token acak 48 byte tidak bisa ditebak; batas ini hanya melindungi database dari banjir request.
const refreshLimit = createLimiter({ windowMs: LOGIN_WINDOW, limit: 300, key: clientIp });

// Ganti password: tebak password lama oleh pemegang token yang dicuri. Per akun, hanya yang gagal.
const passwordLimit = createLimiter({
  windowMs: LOGIN_WINDOW,
  limit: 10,
  key: (req) => (req.user ? `user:${req.user.id}` : null),
  refundOn: (status) => status < 400,
});

// Dipakai tes agar satu kasus tidak memengaruhi kasus berikutnya.
function resetRateLimits() {
  for (const hits of stores) hits.clear();
}

module.exports = { createLimiter, loginLimits, refreshLimit, passwordLimit, resetRateLimits };
