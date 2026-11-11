const env = require('../config/env');

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * 'YYYY-MM-DD' → Date UTC tengah malam, cocok untuk kolom `@db.Date`.
 * Melempar error jika format atau tanggalnya tidak ada (mis. 2026-02-30).
 */
function parseDateOnly(value) {
  const match = DATE_ONLY.exec(value);
  if (!match) throw new RangeError(`Tanggal tidak valid: ${value}`);

  const [, y, m, d] = match.map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d) {
    throw new RangeError(`Tanggal tidak valid: ${value}`);
  }
  return date;
}

// Nilai kolom `@db.Date` dari Prisma (UTC tengah malam) → 'YYYY-MM-DD' untuk response API.
function formatDateOnly(date) {
  return date.toISOString().slice(0, 10);
}

// Tanggal hari ini menurut zona sekolah (WIB), bukan UTC: pukul 01.00 WIB masih "kemarin" di UTC.
function todayLocal(now = new Date(), timeZone = env.tzDisplay) {
  // Locale en-CA menghasilkan format YYYY-MM-DD.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

const TIME_OF_DAY = /^([01]\d|2[0-3]):([0-5]\d)$/;

// 'HH:mm' → Date 1970-01-01 UTC, cocok untuk kolom `@db.Time` (sama dengan seed.js).
function parseTimeOfDay(value) {
  const match = TIME_OF_DAY.exec(value);
  if (!match) throw new RangeError(`Jam tidak valid: ${value}`);
  return new Date(Date.UTC(1970, 0, 1, Number(match[1]), Number(match[2])));
}

// Nilai kolom `@db.Time` dari Prisma → 'HH:mm' untuk response API.
function formatTimeOfDay(date) {
  return date.toISOString().slice(11, 16);
}

const DURATION_UNITS = { s: 1000, m: 60 * 1000, h: 60 * 60 * 1000, d: 24 * 60 * 60 * 1000 };

// Durasi dari env, mis. '15m', '7d' → milidetik. Angka tanpa satuan ditolak agar tidak ambigu.
function parseDuration(value) {
  const match = /^(\d+)([smhd])$/.exec(value);
  if (!match) throw new RangeError(`Durasi tidak valid: ${value}`);
  return Number(match[1]) * DURATION_UNITS[match[2]];
}

module.exports = {
  parseDateOnly,
  formatDateOnly,
  parseTimeOfDay,
  formatTimeOfDay,
  TIME_OF_DAY,
  todayLocal,
  parseDuration,
};
