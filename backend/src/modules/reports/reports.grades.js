// F-LAP-01: laporan nilai per siswa dan mata pelajaran; nilai akhir tidak disimpan.
const { z } = require('zod');
const prisma = require('../../lib/prisma');
const { activeSemesterId } = require('../schedules/schedules.service');

const id = z.coerce.number().int().positive();
const makeQuery = (extra) => z.object({ ...extra,
  semesterId: id.optional(), classGroupId: id.optional(), studentId: id.optional(),
  subjectId: id.optional(),
});

function calculate(components, scores, predicates) {
  const values = new Map(scores.map((score) => [score.gradeComponentId, Number(score.value)]));
  const complete = components.length > 0 &&
    components.reduce((sum, component) => sum + component.weight, 0) === 100 &&
    components.every((component) => values.has(component.id));
  const finalScore = complete
    ? Math.round(components.reduce((sum, component) => sum + values.get(component.id) * component.weight, 0)) / 100
    : null;
  const predicate = finalScore === null ? null : predicates.find((p) => Number(p.minScore) <= finalScore)?.label ?? null;
  return { complete, finalScore, predicate };
}

const columns = [
  { header: 'NIS', width: 18 }, { header: 'Siswa', width: 26 },
  { header: 'Kelas', width: 14 }, { header: 'Mata Pelajaran', width: 27 },
  { header: 'Nilai Akhir', width: 17 }, { header: 'Predikat', width: 12 },
  { header: 'Lengkap', width: 14 },
];
const toCells = (row) => [row.nis, row.studentName, row.className, row.subjectName,
  row.finalScore ?? '', row.predicate ?? '', row.complete ? 'Ya' : 'Tidak'];

async function load({ semesterId, classGroupId, studentId, subjectId }, { skip, take }) {
  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };
  const semester = await prisma.semester.findUnique({ where: { id: periodId }, select: { academicYearId: true } });
  if (!semester) return { items: [], total: 0 };
  const where = {
    semesterId: periodId,
    ...(classGroupId && { classGroupId }),
    ...(subjectId && { subjectId }),
    ...(studentId && { classGroup: { members: { some: { studentId } } } }),
  };
  const [assignments, predicates] = await Promise.all([
    prisma.teachingAssignment.findMany({
      where,
      select: {
        id: true,
        subject: { select: { name: true } },
        classGroup: { select: { name: true,
          members: { where: studentId ? { studentId } : undefined,
            select: { student: { select: { id: true, nis: true, user: { select: { name: true } } } } } },
        } },
        gradeComponents: { select: { id: true, weight: true } },
      },
      orderBy: { id: 'asc' },
    }),
    prisma.gradePredicate.findMany({
      where: { academicYearId: semester.academicYearId },
      select: { label: true, minScore: true }, orderBy: { minScore: 'desc' },
    }),
  ]);
  const studentIds = [...new Set(assignments.flatMap((a) => a.classGroup.members.map((m) => m.student.id)))];
  const assignmentIds = assignments.map((a) => a.id);
  const scores = studentIds.length && assignmentIds.length ? await prisma.score.findMany({
    where: { studentId: { in: studentIds }, teachingAssignmentId: { in: assignmentIds } },
    select: { studentId: true, teachingAssignmentId: true, gradeComponentId: true, value: true },
  }) : [];
  const keyedScores = new Map();
  for (const score of scores) {
    const key = `${score.teachingAssignmentId}:${score.studentId}`;
    if (!keyedScores.has(key)) keyedScores.set(key, []);
    keyedScores.get(key).push(score);
  }
  const items = [];
  for (const assignment of assignments) {
    for (const member of assignment.classGroup.members) {
      const student = member.student;
      const result = calculate(assignment.gradeComponents,
        keyedScores.get(`${assignment.id}:${student.id}`) ?? [], predicates);
      items.push({ studentId: student.id, nis: student.nis, studentName: student.user.name,
        teachingAssignmentId: assignment.id, className: assignment.classGroup.name,
        subjectName: assignment.subject.name, ...result });
    }
  }
  items.sort((a, b) => a.className.localeCompare(b.className, 'id') ||
    a.nis.localeCompare(b.nis, 'id') || a.subjectName.localeCompare(b.subjectName, 'id'));
  return { total: items.length, items: items.slice(skip, skip + take) };
}
module.exports = { code: 'F-LAP-01', title: 'Laporan Nilai', makeQuery, load, toCells, columns, calculate };
