// Laporan beban mengajar guru (F-LAP-09): jumlah jam dan sebarannya per hari, tanpa insentif.
// Dihitung dari jadwal master (slot mingguan) pada semester; pengganti dan pembatalan per tanggal
// tidak ikut dihitung. Kolom adalah usulan (BUSINESS_RULES PO-10).
const { z } = require('zod');
const prisma = require('../../lib/prisma');
const { DAYS_OF_WEEK, DAY_LABELS } = require('../../config/constants');
const { activeSemesterId } = require('../schedules/schedules.service');

const id = z.coerce.number().int().positive();

const filters = {
  semesterId: id.optional(),
  teacherId: id.optional(),
};

const makeQuery = (extra) => z.object({ ...extra, ...filters });

const roundHours = (minutes) => Math.round((minutes / 60) * 100) / 100;

function emptyPerDay() {
  return Object.fromEntries(DAYS_OF_WEEK.map((day) => [day, { sessions: 0, minutes: 0 }]));
}

function buildRow(teacher, slots) {
  const perDay = emptyPerDay();
  const classes = new Set();
  const subjects = new Map();
  let totalMinutes = 0;

  for (const slot of slots) {
    const minutes = (slot.endTime.getTime() - slot.startTime.getTime()) / 60000;
    perDay[slot.dayOfWeek].sessions += 1;
    perDay[slot.dayOfWeek].minutes += minutes;
    totalMinutes += minutes;
    classes.add(slot.teachingAssignment.classGroupId);
    const { subject } = slot.teachingAssignment;
    subjects.set(subject.id, subject);
  }

  return {
    teacher: { id: teacher.id, name: teacher.user.name, employeeNumber: teacher.employeeNumber },
    sessionsPerWeek: slots.length,
    totalMinutes,
    totalHours: roundHours(totalMinutes),
    classCount: classes.size,
    subjects: [...subjects.values()],
    perDay,
  };
}

const columns = [
  { header: 'Nama Guru', width: 26 },
  { header: 'NIP', width: 22 },
  { header: 'Pertemuan per Minggu', width: 20 },
  { header: 'Total Jam per Minggu', width: 20 },
  { header: 'Jumlah Kelas', width: 14 },
  { header: 'Mata Pelajaran', width: 30 },
  ...DAYS_OF_WEEK.map((day) => ({ header: `${DAY_LABELS[day]} (jam)`, width: 14 })),
];

const toCells = (row) => [
  row.teacher.name,
  row.teacher.employeeNumber,
  row.sessionsPerWeek,
  row.totalHours,
  row.classCount,
  row.subjects.map((subject) => subject.name).join(', '),
  ...DAYS_OF_WEEK.map((day) => roundHours(row.perDay[day].minutes)),
];

// Jumlah guru kecil, jadi dihitung di memori lalu dipotong sesuai halaman.
async function load({ semesterId, teacherId }, { skip, take }) {
  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };

  const [teachers, slots] = await Promise.all([
    prisma.teacher.findMany({
      where: teacherId ? { id: teacherId } : undefined,
      select: { id: true, employeeNumber: true, user: { select: { name: true } } },
    }),
    prisma.scheduleSlot.findMany({
      where: { teachingAssignment: { semesterId: periodId, ...(teacherId && { teacherId }) } },
      select: {
        dayOfWeek: true,
        startTime: true,
        endTime: true,
        teachingAssignment: {
          select: {
            teacherId: true,
            classGroupId: true,
            subject: { select: { id: true, code: true, name: true } },
          },
        },
      },
    }),
  ]);

  const slotsByTeacher = new Map();
  for (const slot of slots) {
    const list = slotsByTeacher.get(slot.teachingAssignment.teacherId) ?? [];
    list.push(slot);
    slotsByTeacher.set(slot.teachingAssignment.teacherId, list);
  }

  const rows = teachers
    .map((teacher) => buildRow(teacher, slotsByTeacher.get(teacher.id) ?? []))
    .sort((a, b) => a.teacher.name.localeCompare(b.teacher.name, 'id'));

  return { items: rows.slice(skip, skip + take), total: rows.length };
}

module.exports = {
  code: 'F-LAP-09',
  title: 'Beban Mengajar Guru',
  makeQuery,
  columns,
  toCells,
  load,
};
