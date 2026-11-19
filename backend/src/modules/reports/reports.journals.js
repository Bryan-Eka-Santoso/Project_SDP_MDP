// F-LAP-06: jurnal per sesi mengajar, dengan filter kelas/guru/tanggal.
const { z } = require('zod');
const prisma = require('../../lib/prisma');
const { parseDateOnly, formatDateOnly } = require('../../utils/date');
const { activeSemesterId } = require('../schedules/schedules.service');
const id = z.coerce.number().int().positive();
const date = z.string().refine((v) => { try { parseDateOnly(v); return true; } catch { return false; } },
  'Tanggal harus YYYY-MM-DD yang valid.');
const makeQuery = (extra) => z.object({ ...extra,
  semesterId: id.optional(), classGroupId: id.optional(), teacherId: id.optional(),
  dateFrom: date.optional(), dateTo: date.optional(),
}).refine((d) => !d.dateFrom || !d.dateTo || d.dateFrom <= d.dateTo,
  { path: ['dateTo'], message: 'Tanggal akhir sebelum tanggal awal.' });
const columns = [
  { header: 'Tanggal', width: 15 }, { header: 'Kelas', width: 16 },
  { header: 'Mata Pelajaran', width: 25 }, { header: 'Guru', width: 26 },
  { header: 'Materi Jurnal', width: 65 },
];
const toCells = (row) => [row.date, row.className, row.subjectName, row.teacherName, row.journal];
const toRow = (session) => {
  const assignment = session.scheduleSlot.teachingAssignment;
  return { id: session.id, date: formatDateOnly(session.date), journal: session.journal,
    classGroupId: assignment.classGroupId, className: assignment.classGroup.name,
    teacherId: assignment.teacherId, teacherName: assignment.teacher.user.name,
    subjectName: assignment.subject.name };
};
async function load({ semesterId, classGroupId, teacherId, dateFrom, dateTo }, { skip, take }) {
  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };
  const where = {
    scheduleSlot: { teachingAssignment: { semesterId: periodId,
      ...(classGroupId && { classGroupId }), ...(teacherId && { teacherId }) } },
    ...((dateFrom || dateTo) ? { date: {
      ...(dateFrom && { gte: parseDateOnly(dateFrom) }),
      ...(dateTo && { lte: parseDateOnly(dateTo) }),
    } } : {}),
  };
  const [records, total] = await Promise.all([
    prisma.lessonSession.findMany({ where,
      select: { id: true, date: true, journal: true, scheduleSlot: { select: {
        teachingAssignment: { select: { classGroupId: true, teacherId: true,
          classGroup: { select: { name: true } }, subject: { select: { name: true } },
          teacher: { select: { user: { select: { name: true } } } },
        } },
      } } },
      orderBy: [{ date: 'asc' }, { id: 'asc' }], skip, take,
    }),
    prisma.lessonSession.count({ where }),
  ]);
  return { items: records.map(toRow), total };
}
module.exports = { code: 'F-LAP-06', title: 'Laporan Jurnal', makeQuery, load, toCells, columns };
