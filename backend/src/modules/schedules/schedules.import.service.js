// Impor jadwal pelajaran dari Excel (F-JDW-02, docs/BUSINESS_RULES.md 2.3 dan 2.3.1).
// Alurnya: baca file → validasi SEMUA baris (referensi, jam, bentrok) → simpan sekaligus.
// Jika ada satu saja kesalahan, tidak ada yang disimpan (ADR-012).
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { DAY_LABELS } = require('../../config/constants');
const { parseTimeOfDay } = require('../../utils/date');
const { readSheet, importInvalid, cellText } = require('../../lib/excel');
const { loadSemesterSlots, loadClassPeers, findConflictsInBatch } = require('./schedules.conflicts');
const { invalidField } = require('./schedules.service');

// Nama header sama dengan docs/import-templates/jadwal.xlsx.
const COLUMNS = [
  { key: 'className', header: 'Kelas' },
  { key: 'subjectCode', header: 'Kode Mapel' },
  { key: 'employeeNumber', header: 'NIP Guru' },
  { key: 'day', header: 'Hari' },
  { key: 'start', header: 'Jam Mulai' },
  { key: 'end', header: 'Jam Selesai' },
  { key: 'roomName', header: 'Ruangan' },
];
const HEADER = Object.fromEntries(COLUMNS.map(({ key, header }) => [key, header]));

// Kolom yang ditandai untuk tiap penyebab bentrok. Duplikat menyangkut seluruh baris.
const CONFLICT_COLUMN = {
  TEACHER: HEADER.employeeNumber,
  CLASS: HEADER.className,
  STUDENT: HEADER.className,
  ROOM: HEADER.roomName,
  DUPLICATE: null,
};

// Pencocokan tidak membedakan huruf besar/kecil. NIP sering ditulis dengan spasi antar bagian.
const nameKey = (text) => text.toLowerCase();
const nipKey = (text) => text.replace(/\s+/g, '');
// "Jum'at" dan "Jumat" sama-sama dikenali.
const dayKey = (text) => text.toLowerCase().replace(/['’`´]/g, '');
const DAY_BY_NAME = new Map(Object.entries(DAY_LABELS).map(([day, label]) => [dayKey(label), day]));

// Penanda nama yang cocok dengan lebih dari satu data master (mis. dua ruangan bernama sama).
const AMBIGUOUS = Symbol('ambiguous');

function indexBy(items, keyOf) {
  const map = new Map();
  for (const item of items) {
    const key = keyOf(item);
    map.set(key, map.has(key) ? AMBIGUOUS : item);
  }
  return map;
}

/**
 * Sel jam di Excel bisa berupa teks ("07:30", "7.30"), angka pecahan hari (0.3125), atau Date
 * bertanggal acuan 1899 (sel berformat jam, hasil ExcelJS). Mengembalikan menit sejak 00:00
 * atau null jika tidak terbaca.
 */
function toMinutes(value) {
  let minutes = null;
  if (value instanceof Date) {
    const seconds = value.getUTCHours() * 3600 + value.getUTCMinutes() * 60 + value.getUTCSeconds();
    minutes = Math.round(seconds / 60);
  } else if (typeof value === 'number') {
    if (value >= 0 && value < 1) minutes = Math.round(value * 1440);
  } else if (typeof value === 'string') {
    const match = /^(\d{1,2})[:.](\d{2})$/.exec(value);
    if (match && Number(match[1]) < 24 && Number(match[2]) < 60) {
      minutes = Number(match[1]) * 60 + Number(match[2]);
    }
  }
  return minutes !== null && minutes < 1440 ? minutes : null;
}

const toClock = (minutes) =>
  `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

async function resolveSemester(semesterId) {
  const semester = await prisma.semester.findFirst({
    where: semesterId ? { id: semesterId } : { isActive: true },
    select: { id: true, academicYearId: true, academicYear: { select: { name: true } } },
  });
  if (semester) return semester;

  if (semesterId) throw invalidField('semesterId', 'Semester tidak ditemukan.');
  throw new AppError(409, 'INVALID_STATE', 'Belum ada semester aktif. Pilih semester tujuan impor.');
}

// Semua data pembanding dimuat sekali (satu query per tabel), bukan satu query per baris Excel.
async function loadReferences(semester) {
  const { id: semesterId, academicYearId } = semester;
  const [classes, subjects, teachers, facilities, assignments, existingSlots, classPeers] = await Promise.all([
    prisma.classGroup.findMany({ where: { academicYearId }, select: { id: true, name: true } }),
    prisma.subject.findMany({ select: { id: true, code: true, name: true } }),
    prisma.teacher.findMany({ select: { id: true, employeeNumber: true, user: { select: { name: true } } } }),
    prisma.facility.findMany({ select: { id: true, name: true, type: true } }),
    prisma.teachingAssignment.findMany({
      where: { semesterId },
      select: { id: true, teacherId: true, subjectId: true, classGroupId: true },
    }),
    loadSemesterSlots(prisma, semesterId),
    loadClassPeers(prisma, academicYearId),
  ]);

  return {
    academicYearName: semester.academicYear.name,
    classes: indexBy(classes, (item) => nameKey(item.name)),
    subjects: indexBy(subjects, (item) => nameKey(item.code)),
    teachers: indexBy(
      teachers.map((teacher) => ({ id: teacher.id, employeeNumber: teacher.employeeNumber, name: teacher.user.name })),
      (item) => nipKey(item.employeeNumber),
    ),
    rooms: indexBy(
      facilities.filter((item) => item.type === 'ROOM'),
      (item) => nameKey(item.name),
    ),
    items: indexBy(
      facilities.filter((item) => item.type === 'ITEM'),
      (item) => nameKey(item.name),
    ),
    assignments: new Map(
      assignments.map((item) => [`${item.teacherId}:${item.subjectId}:${item.classGroupId}`, item.id]),
    ),
    existingSlots,
    classPeers,
  };
}

/**
 * Validasi satu baris Excel. Semua kesalahan baris itu dicatat ke `errors` (bukan berhenti di
 * kesalahan pertama) agar admin bisa memperbaiki semuanya sekaligus. Mengembalikan kandidat slot
 * jika baris valid, null jika tidak.
 */
function validateRow({ row, values }, refs, errors) {
  const before = errors.length;
  const fail = (column, message) => errors.push({ row, column, message });

  const text = (key) => {
    const cell = cellText(values[key]);
    if (cell.error) fail(HEADER[key], cell.error);
    else if (cell.text === null) fail(HEADER[key], 'Wajib diisi.');
    return cell.text ?? undefined;
  };

  const find = (map, value, normalize, column, missing) => {
    if (value === undefined) return undefined;
    const hit = map.get(normalize(value));
    if (hit === AMBIGUOUS) {
      fail(column, `“${value}” terdaftar lebih dari satu kali di data master, sehingga tidak bisa dipastikan.`);
      return undefined;
    }
    if (hit === undefined) fail(column, missing(value));
    return hit;
  };

  const minutesOf = (key) => {
    if (values[key] === null) {
      fail(HEADER[key], 'Wajib diisi.');
      return undefined;
    }
    const minutes = toMinutes(values[key]);
    if (minutes === null) fail(HEADER[key], 'Format jam harus HH:mm, mis. 07:30.');
    return minutes ?? undefined;
  };

  const className = text('className');
  const subjectCode = text('subjectCode');
  const employeeNumber = text('employeeNumber');
  const dayName = text('day');
  const roomName = text('roomName');

  const klass = find(refs.classes, className, nameKey, HEADER.className, (value) =>
    `Kelas “${value}” tidak ditemukan di tahun ajaran ${refs.academicYearName}.`,
  );
  const subject = find(refs.subjects, subjectCode, nameKey, HEADER.subjectCode, (value) =>
    `Kode mapel “${value}” tidak ditemukan.`,
  );
  const teacher = find(refs.teachers, employeeNumber, nipKey, HEADER.employeeNumber, (value) =>
    `NIP “${value}” tidak ditemukan di data guru.`,
  );
  const room = find(refs.rooms, roomName, nameKey, HEADER.roomName, (value) =>
    refs.items.has(nameKey(value)) ? `“${value}” adalah barang, bukan ruangan.` : `Ruangan “${value}” tidak ditemukan.`,
  );

  let dayOfWeek;
  if (dayName !== undefined) {
    dayOfWeek = DAY_BY_NAME.get(dayKey(dayName));
    if (!dayOfWeek) {
      fail(HEADER.day, `Hari “${dayName}” tidak dikenal. Gunakan Senin, Selasa, Rabu, Kamis, Jumat, atau Sabtu.`);
    }
  }

  const start = minutesOf('start');
  const end = minutesOf('end');
  if (start !== undefined && end !== undefined && start >= end) {
    fail(HEADER.end, 'Jam selesai harus setelah jam mulai.');
  }

  if (errors.length > before) return null;

  // Impor hanya merujuk penugasan yang sudah ada (F-MST-10); tidak membuat penugasan baru agar salah
  // ketik NIP/kode tidak diam-diam menugaskan guru yang keliru.
  const teachingAssignmentId = refs.assignments.get(`${teacher.id}:${subject.id}:${klass.id}`);
  if (teachingAssignmentId === undefined) {
    fail(
      null,
      `Guru ${teacher.name} belum ditugaskan mengajar ${subject.name} di kelas ${klass.name} pada semester ini. ` +
        'Tambahkan lewat "Atur guru pengajar" di data master, lalu impor ulang.',
    );
    return null;
  }

  return {
    row,
    teachingAssignmentId,
    dayOfWeek,
    startTime: parseTimeOfDay(toClock(start)),
    endTime: parseTimeOfDay(toClock(end)),
    roomId: room.id,
    teacherId: teacher.id,
    classGroupId: klass.id,
    teacherName: teacher.name,
    className: klass.name,
    subjectName: subject.name,
    roomName: room.name,
  };
}

/**
 * Baca dan validasi file tanpa menyimpan apa pun. Melempar 422 IMPORT_INVALID jika ada kesalahan.
 * Dipisah dari importSlots agar validasinya bisa diuji (dan dicoba) tanpa menulis ke database.
 */
async function planImport({ buffer, semesterId }) {
  const semester = await resolveSemester(semesterId);
  const rows = await readSheet(buffer, { columns: COLUMNS });
  const refs = await loadReferences(semester);

  const errors = [];
  const candidates = [];
  for (const raw of rows) {
    const candidate = validateRow(raw, refs, errors);
    if (candidate) candidates.push(candidate);
  }

  for (const { row, reason, message } of findConflictsInBatch(candidates, refs.existingSlots, refs.classPeers)) {
    errors.push({ row, column: CONFLICT_COLUMN[reason], message });
  }
  if (errors.length > 0) throw importInvalid(errors);

  return { semester, candidates };
}

// F-JDW-02. Slot dari file DITAMBAHKAN ke jadwal semester; slot yang sudah ada tidak diubah/dihapus.
async function importSlots({ buffer, semesterId }) {
  const { semester, candidates } = await planImport({ buffer, semesterId });

  // Satu transaksi: semua baris masuk atau tidak sama sekali.
  await prisma.$transaction([
    prisma.scheduleSlot.createMany({
      data: candidates.map((slot) => ({
        teachingAssignmentId: slot.teachingAssignmentId,
        roomId: slot.roomId,
        dayOfWeek: slot.dayOfWeek,
        startTime: slot.startTime,
        endTime: slot.endTime,
      })),
    }),
  ]);

  return { semesterId: semester.id, imported: candidates.length };
}

module.exports = { importSlots, planImport, COLUMNS };
