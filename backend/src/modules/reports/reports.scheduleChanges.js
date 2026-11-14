// Laporan perubahan jadwal (F-LAP-07). Kolom adalah usulan (BUSINESS_RULES PO-10) dan mudah diubah.
const { z } = require('zod');
const prisma = require('../../lib/prisma');
const { DAY_LABELS } = require('../../config/constants');
const { parseDateOnly, formatDateOnly, formatTimeOfDay } = require('../../utils/date');
const { activeSemesterId } = require('../schedules/schedules.service');

const id = z.coerce.number().int().positive();
const dateOnly = z.string().refine(
  (value) => {
    try {
      parseDateOnly(value);
      return true;
    } catch {
      return false;
    }
  },
  { message: 'Format tanggal harus YYYY-MM-DD dan tanggalnya harus ada.' },
);

const TYPE_LABELS = Object.freeze({
  SUBSTITUTE: 'Ganti guru',
  RESCHEDULE: 'Pindah jadwal',
  CANCEL: 'Dibatalkan',
});

// Tanpa semesterId memakai semester aktif.
const filters = {
  semesterId: id.optional(),
  classGroupId: id.optional(),
  // Guru asli atau guru pengganti.
  teacherId: id.optional(),
  type: z.enum(['SUBSTITUTE', 'RESCHEDULE', 'CANCEL']).optional(),
  dateFrom: dateOnly.optional(),
  dateTo: dateOnly.optional(),
};

const makeQuery = (extra) =>
  z.object({ ...extra, ...filters }).refine((d) => !d.dateFrom || !d.dateTo || d.dateFrom <= d.dateTo, {
    path: ['dateTo'],
    message: 'Tanggal akhir tidak boleh sebelum tanggal awal.',
  });

const teacherSelect = { select: { id: true, user: { select: { name: true } } } };

const select = {
  id: true,
  type: true,
  date: true,
  description: true,
  substituteTeacher: teacherSelect,
  newRoom: { select: { id: true, name: true } },
  newStartTime: true,
  newEndTime: true,
  scheduleSlot: {
    select: {
      dayOfWeek: true,
      startTime: true,
      endTime: true,
      room: { select: { id: true, name: true } },
      teachingAssignment: {
        select: {
          teacher: teacherSelect,
          subject: { select: { id: true, code: true, name: true } },
          classGroup: { select: { id: true, name: true } },
        },
      },
    },
  },
};

const person = (teacher) => (teacher ? { id: teacher.id, name: teacher.user.name } : null);

function toRow(change) {
  const slot = change.scheduleSlot;
  const ta = slot.teachingAssignment;
  const rescheduled = change.type === 'RESCHEDULE';
  return {
    id: change.id,
    date: formatDateOnly(change.date),
    dayOfWeek: slot.dayOfWeek,
    classGroup: ta.classGroup,
    subject: ta.subject,
    originalTeacher: person(ta.teacher),
    type: change.type,
    substituteTeacher: person(change.substituteTeacher),
    originalRoom: slot.room,
    originalStartTime: formatTimeOfDay(slot.startTime),
    originalEndTime: formatTimeOfDay(slot.endTime),
    newRoom: change.newRoom,
    newStartTime: rescheduled && change.newStartTime ? formatTimeOfDay(change.newStartTime) : null,
    newEndTime: rescheduled && change.newEndTime ? formatTimeOfDay(change.newEndTime) : null,
    description: change.description,
  };
}

const columns = [
  { header: 'Tanggal', width: 12 },
  { header: 'Hari', width: 10 },
  { header: 'Kelas', width: 10 },
  { header: 'Mata Pelajaran', width: 22 },
  { header: 'Guru Asli', width: 24 },
  { header: 'Jenis Perubahan', width: 16 },
  { header: 'Guru Pengganti', width: 24 },
  { header: 'Jam Asli', width: 14 },
  { header: 'Jam Baru', width: 14 },
  { header: 'Ruangan Asli', width: 18 },
  { header: 'Ruangan Baru', width: 18 },
  { header: 'Keterangan', width: 40 },
];

const range = (start, end) => (start && end ? `${start}–${end}` : '');

const toCells = (row) => [
  row.date,
  DAY_LABELS[row.dayOfWeek],
  row.classGroup.name,
  row.subject.name,
  row.originalTeacher.name,
  TYPE_LABELS[row.type],
  row.substituteTeacher?.name ?? '',
  range(row.originalStartTime, row.originalEndTime),
  range(row.newStartTime, row.newEndTime),
  row.originalRoom.name,
  row.newRoom?.name ?? '',
  row.description,
];

async function load({ semesterId, classGroupId, teacherId, type, dateFrom, dateTo }, { skip, take }) {
  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };

  const where = {
    AND: [
      { scheduleSlot: { teachingAssignment: { semesterId: periodId } } },
      ...(classGroupId ? [{ scheduleSlot: { teachingAssignment: { classGroupId } } }] : []),
      ...(teacherId
        ? [{ OR: [{ scheduleSlot: { teachingAssignment: { teacherId } } }, { substituteTeacherId: teacherId }] }]
        : []),
      ...(type ? [{ type }] : []),
      ...(dateFrom ? [{ date: { gte: parseDateOnly(dateFrom) } }] : []),
      ...(dateTo ? [{ date: { lte: parseDateOnly(dateTo) } }] : []),
    ],
  };

  const [rows, total] = await Promise.all([
    prisma.scheduleChange.findMany({ where, select, orderBy: [{ date: 'asc' }, { id: 'asc' }], skip, take }),
    prisma.scheduleChange.count({ where }),
  ]);
  return { items: rows.map(toRow), total };
}

module.exports = {
  code: 'F-LAP-07',
  title: 'Perubahan Jadwal',
  makeQuery,
  columns,
  toCells,
  load,
};
