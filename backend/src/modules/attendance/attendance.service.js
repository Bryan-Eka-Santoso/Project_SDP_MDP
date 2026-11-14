const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

const DAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

function dayOfDate(dateString) {
  return DAYS[new Date(`${dateString}T00:00:00.000Z`).getUTCDay()];
}

function todayJakarta() {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Jakarta', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const find = (type) => parts.find((p) => p.type === type).value;
  return `${find('year')}-${find('month')}-${find('day')}`;
}

async function findTeacher(userId) {
  const teacher = await prisma.teacher.findUnique({
    where: { userId }, select: { id: true },
  });
  if (!teacher) throw new AppError(403, 'FORBIDDEN', 'Akun ini bukan guru.');
  return teacher;
}

const slotSelect = {
  id: true, dayOfWeek: true, startTime: true, endTime: true,
  teachingAssignment: {
    select: {
      id: true, teacherId: true, semesterId: true,
      semester: { select: { isActive: true, startDate: true, endDate: true } },
      subject: { select: { id: true, name: true } },
      classGroup: {
        select: { id: true, name: true, academicYearId: true },
      },
      teacher: { select: { user: { select: { name: true } } } },
    },
  },
};

async function listSlots(userId) {
  const teacher = await findTeacher(userId);
  const slots = await prisma.scheduleSlot.findMany({
    where: {
      teachingAssignment: { teacherId: teacher.id, semester: { isActive: true } },
    },
    select: slotSelect,
    orderBy: [{ dayOfWeek: 'asc' }, { id: 'asc' }],
  });
  return slots.map((slot) => ({
    id: slot.id,
    dayOfWeek: slot.dayOfWeek,
    startTime: slot.startTime,
    endTime: slot.endTime,
    assignmentId: slot.teachingAssignment.id,
    subjectName: slot.teachingAssignment.subject.name,
    className: slot.teachingAssignment.classGroup.name,
  }));
}

async function authorizedSlot(userId, slotId, dateString) {
  const teacher = await findTeacher(userId);
  const slot = await prisma.scheduleSlot.findUnique({
    where: { id: slotId },
    select: slotSelect,
  });
  if (!slot || !slot.teachingAssignment.semester.isActive) {
    throw new AppError(404, 'NOT_FOUND', 'Jadwal tidak ditemukan.');
  }
  const assignment = slot.teachingAssignment;
  if (dateString) {
    if (dayOfDate(dateString) !== slot.dayOfWeek) {
      throw new AppError(400, 'VALIDATION_ERROR', 'Tanggal tidak sesuai hari jadwal.');
    }
    const date = new Date(`${dateString}T00:00:00.000Z`);
    if (date < assignment.semester.startDate || date > assignment.semester.endDate) {
      throw new AppError(400, 'VALIDATION_ERROR', 'Tanggal di luar semester jadwal.');
    }
  }
  const change = dateString
    ? await prisma.scheduleChange.findUnique({
        where: { scheduleSlotId_date: {
          scheduleSlotId: slotId, date: new Date(`${dateString}T00:00:00.000Z`),
        } },
        select: { type: true, substituteTeacherId: true },
      })
    : null;
  const isOriginalTeacher = assignment.teacherId === teacher.id;
  const isSubstitute = change?.type === 'SUBSTITUTE'
    && change.substituteTeacherId === teacher.id;
  const mayWrite = change?.type !== 'CANCEL'
    && (change?.type === 'SUBSTITUTE' ? isSubstitute : isOriginalTeacher);
  const mayRead = isOriginalTeacher || isSubstitute;
  if (!mayRead) throw new AppError(404, 'NOT_FOUND', 'Jadwal tidak ditemukan.');
  return { slot, teacher, change, mayWrite };
}

async function classStudents(classGroupId) {
  return prisma.classMember.findMany({
    where: { classGroupId },
    select: { studentId: true, student: {
      select: { nis: true, user: { select: { name: true } } },
    } },
    orderBy: { studentId: 'asc' },
  });
}

async function listStudents(userId, slotId) {
  const { slot } = await authorizedSlot(userId, slotId);
  const members = await classStudents(slot.teachingAssignment.classGroup.id);
  return members.map((m) => ({
    id: m.studentId, name: m.student.user.name, nis: m.student.nis,
  }));
}

async function getSession(userId, slotId, dateString) {
  const { slot } = await authorizedSlot(userId, slotId, dateString);
  const date = new Date(`${dateString}T00:00:00.000Z`);
  const [members, session] = await Promise.all([
    classStudents(slot.teachingAssignment.classGroup.id),
    prisma.lessonSession.findUnique({
      where: { scheduleSlotId_date: { scheduleSlotId: slotId, date } },
      select: { id: true, journal: true, attendances: {
        select: { studentId: true, status: true },
      } },
    }),
  ]);
  const existing = new Map((session?.attendances ?? []).map((a) => [a.studentId, a.status]));
  return {
    slotId, date: dateString, journal: session?.journal ?? '',
    saved: Boolean(session),
    students: members.map((member) => ({
      id: member.studentId, name: member.student.user.name, nis: member.student.nis,
      status: existing.get(member.studentId) ?? null,
    })),
  };
}

async function saveSession(userId, slotId, input) {
  const { slot, mayWrite, change } = await authorizedSlot(userId, slotId, input.date);
  if (input.date > todayJakarta()) {
    throw new AppError(400, 'VALIDATION_ERROR', 'Presensi tidak boleh untuk tanggal mendatang.');
  }
  if (!mayWrite) {
    throw new AppError(403, 'FORBIDDEN',
      change?.type === 'CANCEL' ? 'Pelajaran dibatalkan.' : 'Anda bukan guru pada sesi ini.');
  }
  const date = new Date(`${input.date}T00:00:00.000Z`);
  const members = await classStudents(slot.teachingAssignment.classGroup.id);
  const expected = new Set(members.map((m) => m.studentId));
  const received = new Set(input.attendances.map((a) => a.studentId));
  if (expected.size !== received.size || [...expected].some((id) => !received.has(id))) {
    throw new AppError(400, 'VALIDATION_ERROR', 'Presensi harus memuat seluruh siswa kelas.');
  }

  // Status izin APPROVED diprioritaskan di atas input manual guru.
  const approved = await prisma.leaveRequest.findMany({
    where: {
      studentId: { in: [...expected] }, status: 'APPROVED',
      startDate: { lte: date }, endDate: { gte: date },
    },
    select: { studentId: true },
  });
  const excusedIds = new Set(approved.map((a) => a.studentId));

  const result = await prisma.$transaction(async (tx) => {
    const session = await tx.lessonSession.upsert({
      where: { scheduleSlotId_date: { scheduleSlotId: slotId, date } },
      create: { scheduleSlotId: slotId, date, journal: input.journal },
      update: { journal: input.journal },
      select: { id: true },
    });
    for (const item of input.attendances) {
      const status = excusedIds.has(item.studentId) ? 'EXCUSED' : item.status;
      await tx.attendance.upsert({
        where: { sessionId_studentId: {
          sessionId: session.id, studentId: item.studentId,
        } },
        create: { sessionId: session.id, studentId: item.studentId, status },
        update: { status },
      });
    }
    return session;
  });
  return { id: result.id, slotId, date: input.date, journal: input.journal,
    count: input.attendances.length, saved: true };
}

module.exports = { listSlots, listStudents, getSession, saveSession, dayOfDate, todayJakarta };
