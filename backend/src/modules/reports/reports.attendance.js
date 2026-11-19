// F-LAP-02: rekap presensi berdasarkan catatan yang ada, tanpa menganggap sesi kosong sebagai absen.
const { z } = require('zod');
const prisma = require('../../lib/prisma');
const { parseDateOnly } = require('../../utils/date');
const { activeSemesterId } = require('../schedules/schedules.service');
const id = z.coerce.number().int().positive();
const date = z.string().refine((v) => { try { parseDateOnly(v); return true; } catch { return false; } },
  'Tanggal harus YYYY-MM-DD yang valid.');
const makeQuery = (extra) => z.object({ ...extra, semesterId: id.optional(),
  classGroupId: id.optional(), studentId: id.optional(),
  dateFrom: date.optional(), dateTo: date.optional(),
}).refine((d) => !d.dateFrom || !d.dateTo || d.dateFrom <= d.dateTo,
  { path: ['dateTo'], message: 'Tanggal akhir sebelum tanggal awal.' });
const columns = [
  { header: 'NIS', width: 18 }, { header: 'Siswa', width: 26 },
  { header: 'Kelas', width: 14 }, { header: 'Hadir', width: 12 },
  { header: 'Izin', width: 12 }, { header: 'Sakit', width: 12 },
  { header: 'Alpa', width: 12 }, { header: 'Total', width: 12 },
  { header: 'Persentase Hadir', width: 18 },
];
const toCells = (row) => [row.nis, row.studentName, row.className,
  row.present, row.excused, row.sick, row.absent, row.total,
  row.attendanceRate ?? ''];
function countStatuses(records) {
  const counts = { present: 0, excused: 0, sick: 0, absent: 0 };
  const byStatus = { PRESENT: 'present', EXCUSED: 'excused', SICK: 'sick', ABSENT: 'absent' };
  for (const record of records) {
    const name = byStatus[record.status];
    if (name) counts[name] += 1;
  }
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  return { ...counts, total, attendanceRate: total ? Math.round(counts.present / total * 10000) / 100 : null };
}
async function load({ semesterId, classGroupId, studentId, dateFrom, dateTo }, { skip, take }) {
  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };
  const where = {
    classGroup: { teachingAssignments: { some: { semesterId: periodId } } },
    ...(classGroupId && { classGroupId }),
    ...(studentId && { studentId }),
  };
  const members = await prisma.classMember.findMany({
    where,
    select: { classGroupId: true, classGroup: { select: { name: true } },
      student: { select: { id: true, nis: true, user: { select: { name: true } } } } },
    orderBy: [{ classGroupId: 'asc' }, { studentId: 'asc' }],
  });
  const ids = [...new Set(members.map((m) => m.student.id))];
  const groupIds = [...new Set(members.map((m) => m.classGroupId))];
  const attendances = ids.length && groupIds.length ? await prisma.attendance.findMany({
    where: { studentId: { in: ids }, session: {
      scheduleSlot: { teachingAssignment: { semesterId: periodId, classGroupId: { in: groupIds } } },
      ...((dateFrom || dateTo) ? { date: {
        ...(dateFrom && { gte: parseDateOnly(dateFrom) }),
        ...(dateTo && { lte: parseDateOnly(dateTo) }),
      } } : {}),
    } },
    select: { studentId: true, status: true,
      session: { select: { scheduleSlot: { select: { teachingAssignment: { select: { classGroupId: true } } } } } } },
  }) : [];
  const grouped = new Map();
  for (const attendance of attendances) {
    const key = `${attendance.studentId}:${attendance.session.scheduleSlot.teachingAssignment.classGroupId}`;
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key).push(attendance);
  }
  const rows = members.map((member) => ({
    studentId: member.student.id, nis: member.student.nis, studentName: member.student.user.name,
    classGroupId: member.classGroupId, className: member.classGroup.name,
    ...countStatuses(grouped.get(`${member.student.id}:${member.classGroupId}`) ?? []),
  }));
  return { total: rows.length, items: rows.slice(skip, skip + take) };
}
module.exports = { code: 'F-LAP-02', title: 'Laporan Kehadiran', makeQuery, load, toCells, columns, countStatuses };
