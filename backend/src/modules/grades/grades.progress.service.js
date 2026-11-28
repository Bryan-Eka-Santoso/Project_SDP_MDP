const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { toPrismaPagination } = require('../../utils/pagination');

const notFound = () => new AppError(404, 'NOT_FOUND', 'Catatan atau penugasan tidak ditemukan.');
const utcDate = (value) => new Date(`${value}T00:00:00.000Z`);
const dateText = (value) => value.toISOString().slice(0, 10);

function formatNote(row) {
  return {
    id: row.id,
    studentId: row.studentId,
    teachingAssignmentId: row.teachingAssignmentId,
    date: dateText(row.date),
    content: row.content,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    ...(row.student ? { studentName: row.student.user.name, nis: row.student.nis } : {}),
    ...(row.teachingAssignment ? {
      subjectName: row.teachingAssignment.subject.name,
      className: row.teachingAssignment.classGroup.name,
    } : {}),
  };
}

async function ownedAssignment(userId, assignmentId) {
  const assignment = await prisma.teachingAssignment.findFirst({
    where: { id: assignmentId, teacher: { userId }, semester: { isActive: true } },
    select: { id: true, classGroupId: true },
  });
  if (!assignment) throw notFound();
  return assignment;
}

// Membaca catatan: pengajar penugasan atau wali kelasnya. Menulis tetap lewat ownedAssignment.
async function readableAssignment(userId, assignmentId) {
  const assignment = await prisma.teachingAssignment.findFirst({
    where: {
      id: assignmentId,
      semester: { isActive: true },
      OR: [{ teacher: { userId } }, { classGroup: { homeroomTeacher: { userId } } }],
    },
    select: { id: true, classGroupId: true },
  });
  if (!assignment) throw notFound();
  return assignment;
}

async function assertMembership(studentId, classGroupId) {
  const member = await prisma.classMember.findUnique({
    where: { studentId_classGroupId: { studentId, classGroupId } },
    select: { studentId: true },
  });
  if (!member) throw notFound();
}

async function create(userId, body) {
  const assignment = await ownedAssignment(userId, body.teachingAssignmentId);
  await assertMembership(body.studentId, assignment.classGroupId);
  const row = await prisma.progressNote.create({
    data: {
      studentId: body.studentId,
      teachingAssignmentId: assignment.id,
      date: utcDate(body.date),
      content: body.content,
    },
  });
  return formatNote(row);
}

async function update(userId, noteId, body) {
  const existing = await prisma.progressNote.findUnique({
    where: { id: noteId },
    select: { id: true, teachingAssignmentId: true },
  });
  if (!existing) throw notFound();
  await ownedAssignment(userId, existing.teachingAssignmentId);
  const row = await prisma.progressNote.update({
    where: { id: noteId },
    data: {
      ...(body.date !== undefined ? { date: utcDate(body.date) } : {}),
      ...(body.content !== undefined ? { content: body.content } : {}),
    },
  });
  return formatNote(row);
}

async function listByAssignment(userId, assignmentId, query) {
  const assignment = await readableAssignment(userId, assignmentId);
  if (query.studentId) await assertMembership(query.studentId, assignment.classGroupId);
  const where = {
    teachingAssignmentId: assignment.id,
    ...(query.studentId ? { studentId: query.studentId } : {}),
  };
  const [total, rows] = await Promise.all([
    prisma.progressNote.count({ where }),
    prisma.progressNote.findMany({
      where,
      ...toPrismaPagination(query),
      orderBy: [{ date: 'desc' }, { id: 'desc' }],
      include: { student: { select: { nis: true, user: { select: { name: true } } } } },
    }),
  ]);
  return { total, data: rows.map(formatNote) };
}

async function assertStudentAccess(user, studentId) {
  if (user.role === 'STUDENT') {
    const student = await prisma.student.findFirst({
      where: { id: studentId, userId: user.id }, select: { id: true },
    });
    if (!student) throw notFound();
    return;
  }
  if (user.role === 'GUARDIAN') {
    const relation = await prisma.studentGuardian.findFirst({
      where: { studentId, guardian: { userId: user.id } }, select: { studentId: true },
    });
    if (!relation) throw notFound();
    return;
  }
  throw new AppError(403, 'FORBIDDEN', 'Tidak memiliki akses ke catatan siswa.');
}

async function listByStudent(user, studentId, query) {
  await assertStudentAccess(user, studentId);
  const where = {
    studentId,
    teachingAssignment: {
      semester: { isActive: true },
      classGroup: { members: { some: { studentId } } },
      ...(query.teachingAssignmentId ? { id: query.teachingAssignmentId } : {}),
    },
  };
  const [total, rows] = await Promise.all([
    prisma.progressNote.count({ where }),
    prisma.progressNote.findMany({
      where,
      ...toPrismaPagination(query),
      orderBy: [{ date: 'desc' }, { id: 'desc' }],
      include: {
        teachingAssignment: {
          select: {
            subject: { select: { name: true } },
            classGroup: { select: { name: true } },
          },
        },
      },
    }),
  ]);
  return { total, data: rows.map(formatNote) };
}

async function listOwn(user, query) {
  const student = await prisma.student.findUnique({
    where: { userId: user.id }, select: { id: true },
  });
  if (!student) throw notFound();
  return listByStudent(user, student.id, query);
}

module.exports = { create, update, listByAssignment, listByStudent, listOwn };
