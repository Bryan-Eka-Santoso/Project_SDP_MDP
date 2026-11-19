const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

const notFound = () => new AppError(404, 'NOT_FOUND', 'Data presensi tidak ditemukan.');
const dateOnly = (date) => date.toISOString().slice(0, 10);

function sessionDateFilter(query) {
  return {
    ...(query.startDate ? { gte: new Date(`${query.startDate}T00:00:00.000Z`) } : {}),
    ...(query.endDate ? { lte: new Date(`${query.endDate}T00:00:00.000Z`) } : {}),
  };
}

const sessionSelect = {
  date: true,
  scheduleSlot: { select: {
    id: true,
    teachingAssignment: { select: {
      id: true,
      subject: { select: { name: true } },
      classGroup: { select: { name: true } },
    } },
  } },
};

async function assertStudentAccess(user, studentId) {
  if (user.role === 'STUDENT') {
    const match = await prisma.student.findFirst({
      where: { id: studentId, userId: user.id }, select: { id: true },
    });
    if (!match) throw notFound();
    return;
  }
  if (user.role === 'GUARDIAN') {
    const match = await prisma.studentGuardian.findFirst({
      where: { studentId, guardian: { userId: user.id } }, select: { studentId: true },
    });
    if (!match) throw notFound();
    return;
  }
  throw new AppError(403, 'FORBIDDEN', 'Tidak memiliki akses ke presensi siswa.');
}

function toSummary(rows, studentId) {
  const counts = { PRESENT: 0, SICK: 0, EXCUSED: 0, ABSENT: 0 };
  for (const row of rows) counts[row.status] += 1;
  return {
    studentId,
    total: rows.length,
    counts,
    // Persentase hanya dihitung dari sesi yang memang telah diisi presensinya.
    attendancePercentage: rows.length ? Math.round((counts.PRESENT / rows.length) * 10000) / 100 : null,
    entries: rows.map((row) => ({
      date: dateOnly(row.session.date),
      status: row.status,
      scheduleSlotId: row.session.scheduleSlot.id,
      teachingAssignmentId: row.session.scheduleSlot.teachingAssignment.id,
      subjectName: row.session.scheduleSlot.teachingAssignment.subject.name,
      className: row.session.scheduleSlot.teachingAssignment.classGroup.name,
    })),
  };
}

async function studentSummary(user, studentId, query) {
  await assertStudentAccess(user, studentId);
  const date = sessionDateFilter(query);
  const rows = await prisma.attendance.findMany({
    where: {
      studentId,
      session: {
        ...(Object.keys(date).length ? { date } : {}),
        scheduleSlot: { teachingAssignment: { semester: { isActive: true } } },
      },
    },
    select: { status: true, session: { select: sessionSelect } },
    orderBy: [{ session: { date: 'desc' } }, { sessionId: 'desc' }],
  });
  return toSummary(rows, studentId);
}

async function ownSummary(user, query) {
  const student = await prisma.student.findUnique({
    where: { userId: user.id }, select: { id: true },
  });
  if (!student) throw notFound();
  return studentSummary(user, student.id, query);
}

async function teacherClassSummary(user, assignmentId, query) {
  const teacher = await prisma.teacher.findUnique({
    where: { userId: user.id }, select: { id: true },
  });
  if (!teacher) throw notFound();
  const assignment = await prisma.teachingAssignment.findFirst({
    where: { id: assignmentId, teacherId: teacher.id, semester: { isActive: true } },
    select: { id: true, classGroupId: true },
  });
  if (!assignment) throw notFound();

  const [members, attendances] = await Promise.all([
    prisma.classMember.findMany({
      where: { classGroupId: assignment.classGroupId },
      select: { studentId: true, student: { select: {
        nis: true, user: { select: { name: true } },
      } } },
      orderBy: { studentId: 'asc' },
    }),
    prisma.attendance.findMany({
      where: { session: {
        scheduleSlot: { teachingAssignmentId: assignmentId },
        ...(Object.keys(sessionDateFilter(query)).length ? { date: sessionDateFilter(query) } : {}),
      } },
      select: { studentId: true, status: true },
    }),
  ]);
  const counts = new Map(members.map((m) => [m.studentId,
    { PRESENT: 0, SICK: 0, EXCUSED: 0, ABSENT: 0 }]));
  for (const a of attendances) if (counts.has(a.studentId)) counts.get(a.studentId)[a.status] += 1;
  return {
    teachingAssignmentId: assignmentId,
    students: members.map((m) => ({
      studentId: m.studentId,
      nis: m.student.nis,
      name: m.student.user.name,
      counts: counts.get(m.studentId),
    })),
  };
}

async function adminJournals(query) {
  const where = {
    ...(Object.keys(sessionDateFilter(query)).length ? { date: sessionDateFilter(query) } : {}),
    ...(query.teacherId || query.classGroupId ? {
      scheduleSlot: { teachingAssignment: {
        ...(query.teacherId ? { teacherId: query.teacherId } : {}),
        ...(query.classGroupId ? { classGroupId: query.classGroupId } : {}),
      } },
    } : {}),
  };
  const [total, sessions] = await Promise.all([
    prisma.lessonSession.count({ where }),
    prisma.lessonSession.findMany({
      where,
      select: {
        id: true, date: true, journal: true,
        scheduleSlot: { select: {
          id: true,
          teachingAssignment: { select: {
            id: true,
            teacher: { select: { user: { select: { name: true } } } },
            subject: { select: { name: true } },
            classGroup: { select: { name: true } },
          } },
        } },
      },
      orderBy: [{ date: 'desc' }, { id: 'desc' }],
      skip: (query.page - 1) * query.limit,
      take: query.limit,
    }),
  ]);
  return {
    total,
    rows: sessions.map((s) => ({
      id: s.id,
      date: dateOnly(s.date),
      journal: s.journal,
      scheduleSlotId: s.scheduleSlot.id,
      teachingAssignmentId: s.scheduleSlot.teachingAssignment.id,
      teacherName: s.scheduleSlot.teachingAssignment.teacher.user.name,
      subjectName: s.scheduleSlot.teachingAssignment.subject.name,
      className: s.scheduleSlot.teachingAssignment.classGroup.name,
    })),
  };
}

module.exports = { studentSummary, ownSummary, teacherClassSummary, adminJournals };
