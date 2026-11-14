const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { toPrismaPagination } = require('../../utils/pagination');

const forbidden = () => new AppError(404, 'NOT_FOUND', 'Penugasan atau data nilai tidak ditemukan.');
const invalidWeights = () => new AppError(409, 'GRADE_WEIGHTS_INVALID', 'Total bobot komponen harus tepat 100% sebelum menginput nilai.');

async function ownedAssignment(userId, assignmentId) {
  const assignment = await prisma.teachingAssignment.findFirst({
    where: { id: assignmentId, teacher: { userId }, semester: { isActive: true } },
    select: { id: true, classGroupId: true, semesterId: true },
  });
  if (!assignment) throw forbidden();
  return assignment;
}

async function listComponents(userId, assignmentId) {
  await ownedAssignment(userId, assignmentId);
  return prisma.gradeComponent.findMany({
    where: { teachingAssignmentId: assignmentId },
    select: { id: true, name: true, weight: true, teachingAssignmentId: true },
    orderBy: { id: 'asc' },
  });
}

async function createComponent(userId, assignmentId, body) {
  await ownedAssignment(userId, assignmentId);
  const current = await prisma.gradeComponent.aggregate({
    where: { teachingAssignmentId: assignmentId }, _sum: { weight: true },
  });
  if ((current._sum.weight || 0) + body.weight > 100) {
    throw new AppError(409, 'GRADE_WEIGHTS_INVALID', 'Total bobot komponen tidak boleh melebihi 100%.');
  }
  return prisma.gradeComponent.create({
    data: { teachingAssignmentId: assignmentId, ...body },
    select: { id: true, teachingAssignmentId: true, name: true, weight: true },
  });
}

async function updateComponent(userId, componentId, body) {
  const existing = await prisma.gradeComponent.findUnique({
    where: { id: componentId },
    select: { id: true, teachingAssignmentId: true, weight: true },
  });
  if (!existing) throw forbidden();
  await ownedAssignment(userId, existing.teachingAssignmentId);
  if (body.weight !== undefined) {
    const current = await prisma.gradeComponent.aggregate({
      where: { teachingAssignmentId: existing.teachingAssignmentId }, _sum: { weight: true },
    });
    if ((current._sum.weight || 0) - existing.weight + body.weight > 100) {
      throw new AppError(409, 'GRADE_WEIGHTS_INVALID', 'Total bobot komponen tidak boleh melebihi 100%.');
    }
  }
  return prisma.gradeComponent.update({
    where: { id: componentId }, data: body,
    select: { id: true, teachingAssignmentId: true, name: true, weight: true },
  });
}

async function saveScore(userId, body) {
  const assignment = await ownedAssignment(userId, body.teachingAssignmentId);
  const [components, membership] = await Promise.all([
    prisma.gradeComponent.findMany({
      where: { teachingAssignmentId: assignment.id },
      select: { id: true, weight: true },
    }),
    prisma.classMember.findUnique({
      where: { studentId_classGroupId: { studentId: body.studentId, classGroupId: assignment.classGroupId } },
      select: { studentId: true },
    }),
  ]);
  if (!membership || !components.some((component) => component.id === body.gradeComponentId)) {
    throw forbidden();
  }
  if (components.reduce((total, component) => total + component.weight, 0) !== 100) {
    throw invalidWeights();
  }
  const { studentId, gradeComponentId, teachingAssignmentId, value } = body;
  const row = await prisma.score.upsert({
    where: { studentId_gradeComponentId: { studentId, gradeComponentId } },
    create: { studentId, gradeComponentId, teachingAssignmentId, value, inputDate: new Date() },
    update: { value, inputDate: new Date() },
    select: { id: true, studentId: true, gradeComponentId: true, teachingAssignmentId: true, value: true, inputDate: true },
  });
  return { ...row, value: Number(row.value), inputDate: row.inputDate.toISOString().slice(0, 10) };
}

async function listScores(userId, query) {
  await ownedAssignment(userId, query.teachingAssignmentId);
  const where = { teachingAssignmentId: query.teachingAssignmentId };
  const [total, rows] = await Promise.all([
    prisma.score.count({ where }),
    prisma.score.findMany({
      where, ...toPrismaPagination(query), orderBy: { id: 'asc' },
      select: { id: true, studentId: true, gradeComponentId: true, teachingAssignmentId: true, value: true, inputDate: true },
    }),
  ]);
  return {
    total,
    data: rows.map((row) => ({ ...row, value: Number(row.value), inputDate: row.inputDate.toISOString().slice(0, 10) })),
  };
}

module.exports = { listComponents, createComponent, updateComponent, saveScore, listScores };
