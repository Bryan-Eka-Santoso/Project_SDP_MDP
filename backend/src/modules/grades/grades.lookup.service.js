const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

async function assignments(userId) {
  const rows = await prisma.teachingAssignment.findMany({
    where: { teacher: { userId }, semester: { isActive: true } },
    select: {
      id: true, classGroupId: true,
      subject: { select: { name: true } },
      classGroup: { select: { name: true } },
      semester: { select: { type: true } },
    },
    orderBy: { id: 'asc' },
  });
  return rows.map((row) => ({
    id: row.id,
    classGroupId: row.classGroupId,
    label: `${row.subject.name} - ${row.classGroup.name} (${row.semester.type})`,
  }));
}

async function students(userId, assignmentId) {
  const assignment = await prisma.teachingAssignment.findFirst({
    where: { id: assignmentId, teacher: { userId }, semester: { isActive: true } },
    select: { classGroupId: true },
  });
  if (!assignment) throw new AppError(404, 'NOT_FOUND', 'Penugasan tidak ditemukan.');
  const members = await prisma.classMember.findMany({
    where: { classGroupId: assignment.classGroupId },
    select: { student: { select: { id: true, nis: true, user: { select: { name: true } } } } },
    orderBy: { studentId: 'asc' },
  });
  return members.map(({ student }) => ({ id: student.id, name: student.user.name, nis: student.nis }));
}

module.exports = { assignments, students };
