const { z } = require("zod");
const prisma = require("../../lib/prisma");
const { formatDateOnly } = require("../../utils/date");

const positiveId = z.coerce.number().int().positive();

const makeQuery = (extra) =>
  z.object({
    ...extra,
    surveyId: positiveId.optional(),
    academicYearId: positiveId.optional(),
    gradeLevel: z.string().trim().max(10).optional(),
    status: z.enum(["PUBLISHED", "CLOSED"]).optional(),
  });

const columns = [
  { header: "ID Survei", width: 14 },
  { header: "Judul Survei", width: 38 },
  { header: "Tahun Ajaran", width: 20 },
  { header: "Tingkat", width: 14 },
  { header: "Tanggal Buka", width: 18 },
  { header: "Tanggal Tutup", width: 18 },
  { header: "Status", width: 18 },
  { header: "Kode Mapel", width: 18 },
  { header: "Nama Mata Pelajaran", width: 36 },
  { header: "Jumlah Peminat", width: 18 },
  { header: "Total Responden", width: 20 },
  { header: "Persentase Peminat (%)", width: 24 },
];

function toCells(row) {
  return [
    row.surveyId,
    row.surveyTitle,
    row.academicYearName,
    row.gradeLevel,
    row.openDate,
    row.closeDate,
    row.status,
    row.subjectCode,
    row.subjectName,
    row.studentCount,
    row.respondentCount,
    row.percentage,
  ];
}

function formatPersentase(count, total) {
  if (total === 0) return 0;

  return Math.round((count / total) * 10000) / 100;
}

async function load(
  { surveyId, academicYearId, gradeLevel, status },
  { skip, take },
) {
  const surveyWhere = {
    status: status || {
      in: ["PUBLISHED", "CLOSED"],
    },
    ...(surveyId && { id: surveyId }),
    ...(academicYearId && { academicYearId }),
    ...(gradeLevel && { gradeLevel }),
  };

  const surveys = await prisma.electiveSurvey.findMany({
    where: surveyWhere,
    select: {
      id: true,
      title: true,
      gradeLevel: true,
      openDate: true,
      closeDate: true,
      status: true,
      academicYear: {
        select: {
          name: true,
        },
      },
    },
    orderBy: [{ openDate: "desc" }, { id: "desc" }],
  });

  if (surveys.length === 0) {
    return {
      items: [],
      total: 0,
    };
  }

  const surveyIds = surveys.map((item) => item.id);

  const [subjects, selectionCounts, respondentGroups] = await Promise.all([
    prisma.subject.findMany({
      where: {
        type: "Pilihan",
      },
      select: {
        id: true,
        code: true,
        name: true,
      },
      orderBy: {
        name: "asc",
      },
    }),

    prisma.electiveSurveyResponse.groupBy({
      by: ["surveyId", "subjectId"],
      where: {
        surveyId: { in: surveyIds },
      },
      _count: {
        _all: true,
      },
    }),

    prisma.electiveSurveyResponse.groupBy({
      by: ["surveyId", "studentId"],
      where: {
        surveyId: { in: surveyIds },
      },
      _count: {
        _all: true,
      },
    }),
  ]);

  // Jumlah siswa unik yang pernah menjawab setiap survei.
  const respondentCounts = new Map();

  for (const item of respondentGroups) {
    respondentCounts.set(
      item.surveyId,
      (respondentCounts.get(item.surveyId) || 0) + 1,
    );
  }

  // Jumlah peminat per pasangan survei dan mata pelajaran.
  const countMap = new Map();

  for (const item of selectionCounts) {
    const key = `${item.surveyId}:${item.subjectId}`;

    countMap.set(key, item._count._all);
  }

  // Sertakan mata pelajaran yang pernah dipilih,
  // meskipun sekarang jenisnya bukan lagi "Pilihan".
  const subjectIds = new Set(subjects.map((item) => item.id));

  const historicalIds = [
    ...new Set(selectionCounts.map((item) => item.subjectId)),
  ].filter((id) => !subjectIds.has(id));

  const historicalSubjects = historicalIds.length
    ? await prisma.subject.findMany({
        where: {
          id: { in: historicalIds },
        },
        select: {
          id: true,
          code: true,
          name: true,
        },
      })
    : [];

  const allSubjects = [...subjects, ...historicalSubjects].sort((a, b) =>
    a.name.localeCompare(b.name, "id"),
  );

  const rows = [];

  for (const survey of surveys) {
    const respondentCount = respondentCounts.get(survey.id) || 0;

    const subjectRows = allSubjects.map((subject) => {
      const key = `${survey.id}:${subject.id}`;
      const studentCount = countMap.get(key) || 0;

      return {
        surveyId: survey.id,
        surveyTitle: survey.title,
        academicYearName: survey.academicYear.name,
        gradeLevel: survey.gradeLevel,
        openDate: formatDateOnly(survey.openDate),
        closeDate: formatDateOnly(survey.closeDate),
        status: survey.status,
        subjectId: subject.id,
        subjectCode: subject.code,
        subjectName: subject.name,
        studentCount,
        respondentCount,
        percentage: formatPersentase(studentCount, respondentCount),
      };
    });

    subjectRows.sort(
      (a, b) =>
        b.studentCount - a.studentCount ||
        a.subjectName.localeCompare(b.subjectName, "id"),
    );

    rows.push(...subjectRows);
  }

  return {
    items: rows.slice(skip, skip + take),
    total: rows.length,
  };
}

module.exports = {
  code: "F-LAP-08",
  title: "Laporan Survei Mapel Pilihan",
  makeQuery,
  columns,
  toCells,
  load,
};
