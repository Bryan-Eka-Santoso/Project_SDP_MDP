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
  // Empat komponen dasar (Tugas, Ulangan, UTS, UAS) + paling banyak satu Lainnya.
  const components = await prisma.gradeComponent.findMany({
    where: { teachingAssignmentId: assignmentId },
    select: { name: true, weight: true },
  });
  const baseNames = new Set(['tugas', 'ulangan', 'uts', 'uas']);
  const normalizedName = body.name.trim().toLowerCase();
  if (components.length >= 5) {
    throw new AppError(409, 'GRADE_COMPONENT_LIMIT', 'Maksimal satu komponen Lainnya diperbolehkan.');
  }
  if (baseNames.has(normalizedName) || components.some((c) => c.name.trim().toLowerCase() === normalizedName)) {
    throw new AppError(409, 'GRADE_COMPONENT_DUPLICATE', 'Nama komponen sudah dipakai atau termasuk komponen default.');
  }
  const current = { _sum: { weight: components.reduce((sum, c) => sum + c.weight, 0) } };
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
    select: { id: true, teachingAssignmentId: true, name: true, weight: true },
  });
  if (!existing) throw forbidden();
  await ownedAssignment(userId, existing.teachingAssignmentId);
  if (body.name !== undefined) {
    const next = body.name.trim().toLowerCase();
    const baseNames = new Set(['tugas', 'ulangan', 'uts', 'uas']);
    if (baseNames.has(existing.name.trim().toLowerCase()) && existing.name.trim().toLowerCase() !== next) {
      throw new AppError(409, 'GRADE_COMPONENT_FIXED', 'Nama komponen default tidak bisa diganti.');
    }
    const others = await prisma.gradeComponent.findMany({
      where: { teachingAssignmentId: existing.teachingAssignmentId, id: { not: componentId } },
      select: { name: true },
    });
    if ((baseNames.has(next) && next !== existing.name.trim().toLowerCase()) || others.some((c) => c.name.trim().toLowerCase() === next)) {
      throw new AppError(409, 'GRADE_COMPONENT_DUPLICATE', 'Nama komponen sudah digunakan.');
    }
  }
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
  const detailedCount = await prisma.gradeAssessment.count({ where: { gradeComponentId: body.gradeComponentId } });
  if (detailedCount > 0) {
    throw new AppError(409, 'GRADE_USE_ASSESSMENTS', 'Gunakan nilai Tugas/Ulangan per penilaian; rata-rata dihitung otomatis.');
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

// Detail nilai dibuat per penugasan kelas, sehingga semua siswa memiliki daftar tugas yang sama.
async function listAssessments(userId, assignmentId) {
  await ownedAssignment(userId, assignmentId);
  const rows = await prisma.gradeAssessment.findMany({
    where: { teachingAssignmentId: assignmentId },
    select: { id: true, gradeComponentId: true, title: true, sequence: true,
      scores: { select: { studentId: true, value: true } } },
    orderBy: [{ gradeComponentId: 'asc' }, { sequence: 'asc' }],
  });
  return rows.map((r) => ({ ...r, scores: r.scores.map((x) => ({ studentId: x.studentId, value: Number(x.value) })) }));
}

async function createAssessment(userId, assignmentId, body) {
  await ownedAssignment(userId, assignmentId);
  const component = await prisma.gradeComponent.findFirst({
    where: { id: body.gradeComponentId, teachingAssignmentId: assignmentId },
    select: { id: true, name: true },
  });
  if (!component || !['tugas', 'ulangan', 'ulangan harian'].includes(component.name.trim().toLowerCase())) {
    throw new AppError(400, 'ASSESSMENT_COMPONENT_INVALID', 'Hanya Tugas dan Ulangan yang dapat memiliki beberapa nilai.');
  }
  // Transaksi serial per assignment menghindari nomor urut yang sama pada pembuatan berdekatan.
  return prisma.$transaction(async (tx) => {
    const last = await tx.gradeAssessment.findFirst({
      where: { gradeComponentId: component.id }, orderBy: { sequence: 'desc' },
      select: { sequence: true },
    });
    const sequence = (last?.sequence || 0) + 1;
    const title = body.title || `${component.name} ${sequence}`;
    return tx.gradeAssessment.create({
      data: { teachingAssignmentId: assignmentId, gradeComponentId: component.id, sequence, title },
      select: { id: true, gradeComponentId: true, title: true, sequence: true },
    });
  });
}

async function saveAssessmentScore(userId, assessmentId, body) {
  const assessment = await prisma.gradeAssessment.findUnique({
    where: { id: assessmentId },
    select: { id: true, gradeComponentId: true, teachingAssignmentId: true },
  });
  if (!assessment) throw forbidden();
  const assignment = await ownedAssignment(userId, assessment.teachingAssignmentId);
  const membership = await prisma.classMember.findUnique({
    where: { studentId_classGroupId: { studentId: body.studentId, classGroupId: assignment.classGroupId } },
    select: { studentId: true },
  });
  if (!membership) throw forbidden();
  return prisma.$transaction(async (tx) => {
    const detail = await tx.gradeAssessmentScore.upsert({
      where: { assessmentId_studentId: { assessmentId, studentId: body.studentId } },
      create: { assessmentId, studentId: body.studentId, value: body.value },
      update: { value: body.value },
      select: { id: true, assessmentId: true, studentId: true, value: true },
    });
    const [count, rows] = await Promise.all([
      tx.gradeAssessment.count({ where: { gradeComponentId: assessment.gradeComponentId } }),
      tx.gradeAssessmentScore.findMany({
        where: { studentId: body.studentId, assessment: { gradeComponentId: assessment.gradeComponentId } },
        select: { value: true },
      }),
    ]);
    // Selama belum semua Tugas/Ulangan terisi, tidak memalsukan nilai rata-rata menjadi lengkap.
    // Nilai legacy lama tidak disentuh selama proses migrasi awal hingga seluruh detail lengkap.
    let average = null;
    if (rows.length === count && count > 0) {
      average = Math.round((rows.reduce((sum, row) => sum + Number(row.value), 0) / count + Number.EPSILON) * 100) / 100;
      await tx.score.upsert({
        where: { studentId_gradeComponentId: { studentId: body.studentId, gradeComponentId: assessment.gradeComponentId } },
        create: { studentId: body.studentId, gradeComponentId: assessment.gradeComponentId, teachingAssignmentId: assignment.id, value: average, inputDate: new Date() },
        update: { value: average, inputDate: new Date() },
      });
    }
    return { ...detail, value: Number(detail.value), average, complete: rows.length === count };
  });
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

module.exports = { listComponents, createComponent, updateComponent, saveScore, listScores, listAssessments, createAssessment, saveAssessmentScore };
