// Cek bentrok jadwal (F-JDW-03, docs/BUSINESS_RULES.md 2.2). Dipisah dari service agar bisa
// dipakai ulang oleh impor jadwal (F-JDW-02) dan perubahan jadwal (F-JDW-04).
const { DAY_LABELS } = require('../../config/constants');
const { formatTimeOfDay } = require('../../utils/date');

// Rentang [a1, a2) dan [b1, b2): slot yang bersambung (08.30 selesai, 08.30 mulai) tidak bentrok.
function overlaps(a, b) {
  return a.startTime.getTime() < b.endTime.getTime() && b.startTime.getTime() < a.endTime.getTime();
}

const FIELD_BY_REASON = {
  TEACHER: 'teachingAssignmentId',
  CLASS: 'teachingAssignmentId',
  STUDENT: 'teachingAssignmentId',
  ROOM: 'roomId',
};

function describe(reason, other) {
  const when = `${DAY_LABELS[other.dayOfWeek]} ${formatTimeOfDay(other.startTime)}–${formatTimeOfDay(other.endTime)}`;
  const lesson = `${other.subjectName} di ${other.className}`;
  // Slot dari baris lain di file impor belum punya id; tunjukkan nomor barisnya.
  const source = other.row ? ` (baris ${other.row} di file)` : '';
  switch (reason) {
    case 'TEACHER':
      return `Guru ${other.teacherName} sudah mengajar ${lesson} pada ${when}${source}.`;
    case 'CLASS':
      return `Kelas ${other.className} sudah ada pelajaran ${other.subjectName} pada ${when}${source}.`;
    case 'ROOM':
      return `Ruangan ${other.roomName} sudah dipakai untuk ${lesson} pada ${when}${source}.`;
    default:
      return `Ada siswa yang juga mengikuti ${lesson} pada ${when}${source}.`;
  }
}

/**
 * Bandingkan satu kandidat slot dengan slot lain pada semester yang sama.
 *
 * `candidate` dan setiap `others[i]`: { dayOfWeek, startTime, endTime, teacherId, classGroupId, roomId }
 * (jam berupa Date dari kolom @db.Time). `others[i]` juga punya `id` dan nama untuk pesan.
 * `sharedClassIds`: id kelas lain yang punya anggota sama dengan kelas kandidat.
 *
 * Mengembalikan `details` untuk 409 SCHEDULE_CONFLICT: satu entri per slot per penyebab.
 */
function detectConflicts(candidate, others, sharedClassIds = new Set()) {
  const details = [];
  for (const other of others) {
    if (other.dayOfWeek !== candidate.dayOfWeek || !overlaps(candidate, other)) continue;

    const reasons = [];
    if (other.teacherId === candidate.teacherId) reasons.push('TEACHER');
    if (other.classGroupId === candidate.classGroupId) reasons.push('CLASS');
    else if (sharedClassIds.has(other.classGroupId)) reasons.push('STUDENT');
    if (other.roomId === candidate.roomId) reasons.push('ROOM');

    for (const reason of reasons) {
      details.push({
        field: FIELD_BY_REASON[reason],
        reason,
        slotId: other.id,
        message: describe(reason, other),
      });
    }
  }
  return details;
}

const slotForConflict = {
  id: true,
  teachingAssignmentId: true,
  dayOfWeek: true,
  startTime: true,
  endTime: true,
  roomId: true,
  room: { select: { name: true } },
  teachingAssignment: {
    select: {
      teacherId: true,
      classGroupId: true,
      teacher: { select: { user: { select: { name: true } } } },
      subject: { select: { name: true } },
      classGroup: { select: { name: true } },
    },
  },
};

function flatten(slot) {
  const ta = slot.teachingAssignment;
  return {
    id: slot.id,
    teachingAssignmentId: slot.teachingAssignmentId,
    dayOfWeek: slot.dayOfWeek,
    startTime: slot.startTime,
    endTime: slot.endTime,
    roomId: slot.roomId,
    roomName: slot.room.name,
    teacherId: ta.teacherId,
    teacherName: ta.teacher.user.name,
    classGroupId: ta.classGroupId,
    className: ta.classGroup.name,
    subjectName: ta.subject.name,
  };
}

// Siswa yang sama di dua kelas berbeda (mis. kelas reguler + kelas paket) juga bentrok.
async function conflictsAgainst(db, candidate, others) {
  const overlapping = others.filter((o) => o.dayOfWeek === candidate.dayOfWeek && overlaps(candidate, o));
  const otherClassIds = [
    ...new Set(overlapping.map((o) => o.classGroupId).filter((c) => c !== candidate.classGroupId)),
  ];
  let sharedClassIds = new Set();
  if (otherClassIds.length > 0) {
    const shared = await db.classMember.findMany({
      where: {
        classGroupId: { in: otherClassIds },
        student: { classMemberships: { some: { classGroupId: candidate.classGroupId } } },
      },
      select: { classGroupId: true },
      distinct: ['classGroupId'],
    });
    sharedClassIds = new Set(shared.map((s) => s.classGroupId));
  }
  return detectConflicts(candidate, overlapping, sharedClassIds);
}

/**
 * Cari bentrok kandidat terhadap slot mingguan yang tersimpan di `semesterId` (F-JDW-03).
 * `excludeSlotId` diisi saat mengubah slot agar tidak dibandingkan dengan dirinya sendiri.
 */
async function findSlotConflicts(db, candidate, { semesterId, excludeSlotId } = {}) {
  const rows = await db.scheduleSlot.findMany({
    where: {
      ...(excludeSlotId ? { id: { not: excludeSlotId } } : {}),
      dayOfWeek: candidate.dayOfWeek,
      startTime: { lt: candidate.endTime },
      endTime: { gt: candidate.startTime },
      teachingAssignment: { semesterId },
    },
    select: slotForConflict,
  });
  return conflictsAgainst(db, candidate, rows.map(flatten));
}

// Slot pada tanggal tertentu setelah ditimpa perubahan di tanggal itu (BUSINESS_RULES 2.4).
// Mengembalikan null jika slot dibatalkan.
function applyChange(slot, change) {
  if (!change) return slot;
  if (change.type === 'CANCEL') return null;
  if (change.type === 'SUBSTITUTE') {
    return { ...slot, teacherId: change.substituteTeacherId, teacherName: change.substituteTeacher.user.name };
  }
  return {
    ...slot,
    startTime: change.newStartTime ?? slot.startTime,
    endTime: change.newEndTime ?? slot.endTime,
    roomId: change.newRoomId ?? slot.roomId,
    roomName: change.newRoom?.name ?? slot.roomName,
  };
}

/**
 * Cek bentrok untuk satu tanggal (F-JDW-04): slot lain di hari itu dibandingkan setelah
 * perubahan mereka pada tanggal yang sama diterapkan. Jam slot lain bisa sudah dipindah,
 * jadi semua slot pada hari itu diambil tanpa filter jam.
 */
async function findConflictsOnDate(db, candidate, { semesterId, date, excludeSlotId }) {
  const rows = await db.scheduleSlot.findMany({
    where: {
      id: { not: excludeSlotId },
      dayOfWeek: candidate.dayOfWeek,
      teachingAssignment: { semesterId },
    },
    select: {
      ...slotForConflict,
      changes: {
        where: { date },
        select: {
          type: true,
          substituteTeacherId: true,
          substituteTeacher: { select: { user: { select: { name: true } } } },
          newRoomId: true,
          newRoom: { select: { name: true } },
          newStartTime: true,
          newEndTime: true,
        },
      },
    },
  });
  const others = rows.map((row) => applyChange(flatten(row), row.changes[0])).filter(Boolean);
  return conflictsAgainst(db, candidate, others);
}

// Semua slot satu semester, sudah di-flatten untuk detectConflicts (satu query untuk impor).
async function loadSemesterSlots(db, semesterId) {
  const rows = await db.scheduleSlot.findMany({
    where: { teachingAssignment: { semesterId } },
    select: slotForConflict,
  });
  return rows.map(flatten);
}

// Map<classGroupId, Set<classGroupId>>: kelas lain yang punya anggota sama (mis. kelas paket).
function buildClassPeers(members) {
  const classesByStudent = new Map();
  for (const { studentId, classGroupId } of members) {
    if (!classesByStudent.has(studentId)) classesByStudent.set(studentId, []);
    classesByStudent.get(studentId).push(classGroupId);
  }

  const peers = new Map();
  for (const classIds of classesByStudent.values()) {
    if (classIds.length < 2) continue;
    for (const classId of classIds) {
      if (!peers.has(classId)) peers.set(classId, new Set());
      for (const other of classIds) if (other !== classId) peers.get(classId).add(other);
    }
  }
  return peers;
}

async function loadClassPeers(db, academicYearId) {
  const members = await db.classMember.findMany({
    where: { classGroup: { academicYearId } },
    select: { classGroupId: true, studentId: true },
  });
  return buildClassPeers(members);
}

const identityKey = (slot) =>
  [slot.teachingAssignmentId, slot.dayOfWeek, slot.startTime.getTime(), slot.endTime.getTime(), slot.roomId].join('|');

/**
 * Cek bentrok untuk banyak kandidat sekaligus (impor jadwal, F-JDW-02) tanpa query per baris.
 * Kandidat diperiksa berurutan terhadap slot tersimpan dan kandidat di atasnya, jadi bentrok
 * antarbaris dilaporkan pada baris yang lebih bawah.
 *
 * `candidates[i]`: seperti pada detectConflicts, ditambah `row` (nomor baris Excel) dan
 * `teachingAssignmentId`. `existing`: slot tersimpan hasil loadSemesterSlots. `peers`: hasil
 * loadClassPeers.
 *
 * Mengembalikan [{ row, reason, message }] dengan reason TEACHER | CLASS | ROOM | STUDENT |
 * DUPLICATE. Jadwal yang persis sama dengan yang sudah ada dilaporkan satu kali sebagai
 * DUPLICATE, bukan sebagai bentrok guru + kelas + ruangan sekaligus.
 */
function findConflictsInBatch(candidates, existing, peers = new Map()) {
  const byDay = new Map();
  const identities = new Map();
  const remember = (slot) => {
    if (!byDay.has(slot.dayOfWeek)) byDay.set(slot.dayOfWeek, []);
    byDay.get(slot.dayOfWeek).push(slot);
    identities.set(identityKey(slot), slot);
  };
  existing.forEach(remember);

  const found = [];
  for (const candidate of candidates) {
    const twin = identities.get(identityKey(candidate));
    if (twin) {
      found.push({
        row: candidate.row,
        reason: 'DUPLICATE',
        message: twin.row
          ? `Baris ini sama persis dengan baris ${twin.row}.`
          : 'Jadwal ini sudah ada di semester tersebut.',
      });
      continue;
    }

    const others = byDay.get(candidate.dayOfWeek) ?? [];
    for (const { reason, message } of detectConflicts(candidate, others, peers.get(candidate.classGroupId))) {
      found.push({ row: candidate.row, reason, message });
    }
    remember(candidate);
  }
  return found;
}

module.exports = {
  overlaps,
  detectConflicts,
  applyChange,
  findSlotConflicts,
  findConflictsOnDate,
  loadSemesterSlots,
  loadClassPeers,
  buildClassPeers,
  findConflictsInBatch,
};
