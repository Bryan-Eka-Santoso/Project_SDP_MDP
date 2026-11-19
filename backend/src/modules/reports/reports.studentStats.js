// Laporan statistik kesiswaan (F-LAP-10, PO-10): rekap per kelas untuk rombel, presensi, dan pelanggaran.
const { z } = require('zod');
const prisma = require('../../lib/prisma');
const { positiveId } = require('./reports.helpers');

const filters = {
  semesterId: positiveId.optional(),
  classGroupId: positiveId.optional(),
  gradeLevel: z.string().trim().min(1).max(10).optional(),
};

const makeQuery = (extra) => z.object({ ...extra, ...filters });

const columns = [
  { header: 'Kelas', width: 14 },
  { header: 'Tingkat', width: 10 },
  { header: 'Wali Kelas', width: 24 },
  { header: 'Kapasitas', width: 12 },
  { header: 'Jumlah Siswa', width: 14 },
  { header: 'Hadir', width: 10 },
  { header: 'Sakit', width: 10 },
  { header: 'Izin', width: 10 },
  { header: 'Alpa', width: 10 },
  { header: 'Kehadiran (%)', width: 14 },
  { header: 'Jumlah Pelanggaran', width: 18 },
  { header: 'Total Poin Pelanggaran', width: 22 },
];

const ATTENDANCE_KEYS = Object.freeze({
  PRESENT: 'present',
  SICK: 'sick',
  EXCUSED: 'excused',
  ABSENT: 'absent',
});

const emptyAttendance = () => ({ present: 0, sick: 0, excused: 0, absent: 0, total: 0 });

function summarizeAttendance(rows) {
  const byClass = new Map();
  for (const row of rows) {
    const classId = row.session?.scheduleSlot?.teachingAssignment?.classGroupId;
    const key = ATTENDANCE_KEYS[row.status];
    if (!classId || !key) continue;
    const bucket = byClass.get(classId) ?? emptyAttendance();
    bucket[key] += 1;
    bucket.total += 1;
    byClass.set(classId, bucket);
  }
  return byClass;
}

function summarizeViolations(rows) {
  const byStudent = new Map();
  for (const row of rows) {
    const bucket = byStudent.get(row.studentId) ?? { count: 0, points: 0 };
    bucket.count += 1;
    bucket.points += row.points;
    byStudent.set(row.studentId, bucket);
  }
  return byStudent;
}

function classViolationTotals(members, byStudent) {
  let count = 0;
  let points = 0;
  for (const member of members) {
    const studentTotals = byStudent.get(member.studentId);
    if (!studentTotals) continue;
    count += studentTotals.count;
    points += studentTotals.points;
  }
  return { count, points };
}

function buildRow(cls, attendanceByClass, violationsByStudent) {
  const attendance = attendanceByClass.get(cls.id) ?? emptyAttendance();
  const attendanceRate = attendance.total > 0 ? Math.round((attendance.present / attendance.total) * 1000) / 10 : 0;
  return {
    classGroup: {
      id: cls.id,
      name: cls.name,
      gradeLevel: cls.gradeLevel,
      capacity: cls.capacity,
    },
    homeroomTeacher: cls.homeroomTeacher
      ? { id: cls.homeroomTeacher.id, name: cls.homeroomTeacher.user.name }
      : null,
    studentCount: cls.members.length,
    attendance,
    attendanceRate,
    violations: classViolationTotals(cls.members, violationsByStudent),
  };
}

const toCells = (row) => [
  row.classGroup.name,
  row.classGroup.gradeLevel,
  row.homeroomTeacher?.name ?? '',
  row.classGroup.capacity,
  row.studentCount,
  row.attendance.present,
  row.attendance.sick,
  row.attendance.excused,
  row.attendance.absent,
  row.attendanceRate,
  row.violations.count,
  row.violations.points,
];

async function load({ semesterId, classGroupId, gradeLevel }, { skip, take }) {
  const semester = await prisma.semester.findFirst({
    where: semesterId ? { id: semesterId } : { isActive: true },
    select: { id: true, academicYearId: true, startDate: true, endDate: true },
  });
  if (!semester) return { items: [], total: 0 };

  const classWhere = {
    academicYearId: semester.academicYearId,
    ...(classGroupId && { id: classGroupId }),
    ...(gradeLevel && { gradeLevel }),
  };

  const [classes, total] = await Promise.all([
    prisma.classGroup.findMany({
      where: classWhere,
      select: {
        id: true,
        name: true,
        gradeLevel: true,
        capacity: true,
        homeroomTeacher: { select: { id: true, user: { select: { name: true } } } },
        members: { select: { studentId: true } },
      },
      orderBy: [{ gradeLevel: 'asc' }, { name: 'asc' }, { id: 'asc' }],
      skip,
      take,
    }),
    prisma.classGroup.count({ where: classWhere }),
  ]);
  if (classes.length === 0) return { items: [], total };

  const classIds = classes.map((cls) => cls.id);
  const studentIds = [...new Set(classes.flatMap((cls) => cls.members.map((member) => member.studentId)))];

  const [attendanceRows, violationRows] = await Promise.all([
    prisma.attendance.findMany({
      where: {
        session: {
          scheduleSlot: {
            teachingAssignment: {
              semesterId: semester.id,
              classGroupId: { in: classIds },
            },
          },
        },
      },
      select: {
        status: true,
        session: {
          select: {
            scheduleSlot: {
              select: {
                teachingAssignment: { select: { classGroupId: true } },
              },
            },
          },
        },
      },
    }),
    studentIds.length > 0
      ? prisma.violation.findMany({
          where: {
            studentId: { in: studentIds },
            date: { gte: semester.startDate, lte: semester.endDate },
          },
          select: { studentId: true, points: true },
        })
      : [],
  ]);

  const attendanceByClass = summarizeAttendance(attendanceRows);
  const violationsByStudent = summarizeViolations(violationRows);

  return {
    items: classes.map((cls) => buildRow(cls, attendanceByClass, violationsByStudent)),
    total,
  };
}

module.exports = {
  code: 'F-LAP-10',
  title: 'Statistik Kesiswaan',
  makeQuery,
  columns,
  toCells,
  load,
};
