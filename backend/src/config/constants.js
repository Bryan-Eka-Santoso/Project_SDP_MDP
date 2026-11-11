// Nilai `type` di payload FCM (docs/GLOSSARY.md, enum NotificationType). Tidak ada di
// schema.prisma karena notifikasi tidak disimpan di database (ADR-010).
const NOTIFICATION_TYPES = Object.freeze([
  'SCHEDULE_CHANGE',
  'ANNOUNCEMENT',
  'VIOLATION',
  'LEAVE_REQUEST',
  'BOOKING',
  'BILL_OVERDUE',
  'TASK',
]);

// Urutan sama dengan enum DayOfWeek di schema.prisma; label untuk pesan error ke pengguna.
const DAY_LABELS = Object.freeze({
  MONDAY: 'Senin',
  TUESDAY: 'Selasa',
  WEDNESDAY: 'Rabu',
  THURSDAY: 'Kamis',
  FRIDAY: 'Jumat',
  SATURDAY: 'Sabtu',
});
const DAYS_OF_WEEK = Object.freeze(Object.keys(DAY_LABELS));

module.exports = { NOTIFICATION_TYPES, DAY_LABELS, DAYS_OF_WEEK };
