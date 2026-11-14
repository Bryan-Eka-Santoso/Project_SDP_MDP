const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

const notFound = () => new AppError(404, 'NOT_FOUND', 'Data nilai tidak ditemukan.');

// Tidak menyimpan nilai akhir. Jika satu komponen kosong, hasil tidak ditampilkan.
function calculateGrade(components, scoreByComponent) {
  const totalWeight = components.reduce((sum, c) => sum + c.weight, 0);
  const complete = components.length > 0 && totalWeight === 100 &&
    components.every((c) => scoreByComponent.has(c.id));
  const finalScore = complete
    ? components.reduce((sum, c) => sum + Number(scoreByComponent.get(c.id)) * c.weight, 0) / 100
    : null;
  return {
    complete,
    totalWeight,
    finalScore: finalScore === null ? null : Math.round((finalScore + Number.EPSILON) * 100) / 100,
    components: components.map((c) => ({
      id: c.id,
      name: c.name,
      weight: c.weight,
      value: scoreByComponent.has(c.id) ? Number(scoreByComponent.get(c.id)) : null,
    })),
  };
}

// Mengambil predikat dari skala tahun ajaran, tanpa mengarang ambang nilai.
function findPredicate(finalScore, predicates) {
  if (finalScore === null || finalScore === undefined) return null;
  const match = predicates
    .filter((item) => Number(item.minScore) <= finalScore)
    .sort((a, b) => Number(b.minScore) - Number(a.minScore))[0];
  return match?.label ?? null;
}

async function loadPredicates(academicYearId) {
  return prisma.gradePredicate.findMany({
    where: { academicYearId },
    select: { label: true, minScore: true },
    orderBy: { minScore: 'desc' },
  });
}

async function assertStudentScope(user, studentId) {
  if (user.role === 'STUDENT') {
    const match = await prisma.student.findFirst({
      where: { id: studentId, userId: user.id }, select: { id: true },
    });
    if (!match) throw notFound();
    return;
  }
  if (user.role === 'GUARDIAN') {
    const match = await prisma.studentGuardian.findFirst({
      where: { studentId, guardian: { userId: user.id } },
      select: { studentId: true },
    });
    if (!match) throw notFound();
    return;
  }
  throw new AppError(403, 'FORBIDDEN', 'Tidak memiliki akses ke nilai siswa.');
}

async function studentScores(user, studentId) {
  await assertStudentScope(user, studentId);
  const activeSemester = await prisma.semester.findFirst({
    where: { isActive: true }, select: { id: true, academicYearId: true },
  });
  if (!activeSemester) return [];

  const predicates = await loadPredicates(activeSemester.academicYearId);

  const assignments = await prisma.teachingAssignment.findMany({
    where: {
      semesterId: activeSemester.id,
      classGroup: { members: { some: { studentId } } },
    },
    select: {
      id: true,
      subject: { select: { name: true } },
      classGroup: { select: { name: true } },
      gradeComponents: {
        select: {
          id: true, name: true, weight: true,
          scores: { where: { studentId }, select: { value: true } },
        },
        orderBy: { id: 'asc' },
      },
    },
    orderBy: { id: 'asc' },
  });

  return assignments.map((a) => {
    const components = a.gradeComponents;
    const values = new Map(components.filter((c) => c.scores.length > 0)
      .map((c) => [c.id, c.scores[0].value]));
    const grade = calculateGrade(components, values);
    return {
      teachingAssignmentId: a.id,
      subjectName: a.subject.name,
      className: a.classGroup.name,
      ...grade,
      predicate: findPredicate(grade.finalScore, predicates),
    };
  });
}

async function ownStudentScores(user) {
  const student = await prisma.student.findUnique({
    where: { userId: user.id }, select: { id: true },
  });
  if (!student) throw notFound();
  return studentScores(user, student.id);
}

async function classSummary(user, assignmentId, page, limit) {
  const assignment = await prisma.teachingAssignment.findFirst({
    where: {
      id: assignmentId,
      semester: { isActive: true },
      OR: [
        { teacher: { userId: user.id } },
        { classGroup: { homeroomTeacher: { userId: user.id } } },
      ],
    },
    select: {
      id: true, classGroupId: true,
      semester: { select: { academicYearId: true } },
      subject: { select: { name: true } },
      classGroup: { select: { name: true } },
      gradeComponents: { select: { id: true, name: true, weight: true }, orderBy: { id: 'asc' } },
    },
  });
  if (!assignment) throw notFound();

  const predicates = await loadPredicates(assignment.semester.academicYearId);

  const [total, members] = await Promise.all([
    prisma.classMember.count({ where: { classGroupId: assignment.classGroupId } }),
    prisma.classMember.findMany({
      where: { classGroupId: assignment.classGroupId },
      orderBy: { studentId: 'asc' }, skip: (page - 1) * limit, take: limit,
      select: { student: { select: { id: true, nis: true, user: { select: { name: true } } } } },
    }),
  ]);
  const studentIds = members.map((m) => m.student.id);
  const scores = studentIds.length === 0 ? [] : await prisma.score.findMany({
    where: { teachingAssignmentId: assignment.id, studentId: { in: studentIds } },
    select: { studentId: true, gradeComponentId: true, value: true },
  });
  const valueMap = new Map();
  for (const score of scores) {
    if (!valueMap.has(score.studentId)) valueMap.set(score.studentId, new Map());
    valueMap.get(score.studentId).set(score.gradeComponentId, score.value);
  }
  return {
    label: `${assignment.subject.name} - ${assignment.classGroup.name}`,
    total,
    data: members.map(({ student }) => {
      const grade = calculateGrade(
        assignment.gradeComponents,
        valueMap.get(student.id) || new Map(),
      );
      return {
        studentId: student.id,
        name: student.user.name,
        nis: student.nis,
        ...grade,
        predicate: findPredicate(grade.finalScore, predicates),
      };
    }),
  };
}

module.exports = { calculateGrade, findPredicate, studentScores, ownStudentScores, classSummary };
