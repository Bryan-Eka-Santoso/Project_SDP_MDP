const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

const {
  todayLocal,
  parseDateOnly,
  formatDateOnly,
} = require("../../utils/date");

function fail(status, code, message) {
  throw new AppError(status, code, message);
}

async function getStudent(userId) {
  const student = await prisma.student.findUnique({
    where: { userId },
    select: { id: true },
  });

  if (!student) {
    fail(403, "FORBIDDEN", "Akun siswa tidak ditemukan.");
  }

  return student;
}

function isOpen(survey) {
  const today = todayLocal();

  return (
    survey.status === "PUBLISHED" &&
    formatDateOnly(survey.openDate) <= today &&
    formatDateOnly(survey.closeDate) >= today
  );
}

function formatSurvey(survey, selectedSubjectIds = []) {
  return {
    id: survey.id,
    title: survey.title,
    academicYearName: survey.academicYear.name,
    gradeLevel: survey.gradeLevel,
    openDate: formatDateOnly(survey.openDate),
    closeDate: formatDateOnly(survey.closeDate),
    status: survey.status,
    canFill: isOpen(survey),
    hasSubmitted: selectedSubjectIds.length > 0,
    selectedSubjectIds,
  };
}

async function allowedSurvey(studentId, surveyId) {
  const survey = await prisma.electiveSurvey.findUnique({
    where: { id: surveyId },
    include: {
      academicYear: {
        select: { name: true },
      },
    },
  });

  if (!survey) {
    fail(404, "NOT_FOUND", "Survei tidak ditemukan atau tidak dapat diakses.");
  }

  const member = await prisma.classMember.findFirst({
    where: {
      studentId,
      classGroup: {
        academicYearId: survey.academicYearId,
        gradeLevel: survey.gradeLevel,
      },
    },
    select: { studentId: true },
  });

  if (!member) {
    fail(403, "FORBIDDEN", "Survei tidak sesuai dengan kelas siswa.");
  }

  return survey;
}

async function list(userId) {
  const student = await getStudent(userId);

  const memberships = await prisma.classMember.findMany({
    where: { studentId: student.id },
    select: {
      classGroup: {
        select: {
          academicYearId: true,
          gradeLevel: true,
        },
      },
    },
  });

  const allowed = memberships.map((m) => ({
    academicYearId: m.classGroup.academicYearId,
    gradeLevel: m.classGroup.gradeLevel,
  }));

  if (allowed.length === 0) return [];

  const surveys = await prisma.electiveSurvey.findMany({
    where: {
      // CLOSED tetap tampil (hanya baca) agar survei tidak hilang tanpa keterangan.
      status: { in: ["PUBLISHED", "CLOSED"] },
      OR: allowed,
    },
    include: {
      academicYear: {
        select: { name: true },
      },
      responses: {
        where: { studentId: student.id },
        select: { subjectId: true },
      },
    },
    orderBy: [{ openDate: "desc" }, { id: "desc" }],
  });

  return surveys.map((survey) =>
    formatSurvey(
      survey,
      survey.responses.map((r) => r.subjectId),
    ),
  );
}

async function detail(userId, surveyId) {
  const student = await getStudent(userId);
  const survey = await allowedSurvey(student.id, surveyId);

  if (survey.status === "DRAFT") {
    fail(404, "NOT_FOUND", "Survei belum diterbitkan.");
  }

  const [subjects, responses] = await Promise.all([
    prisma.subject.findMany({
      where: { type: "Pilihan" },
      select: {
        id: true,
        code: true,
        name: true,
      },
      orderBy: { name: "asc" },
    }),

    prisma.electiveSurveyResponse.findMany({
      where: {
        surveyId,
        studentId: student.id,
      },
      select: { subjectId: true },
    }),
  ]);

  return {
    survey: formatSurvey(
      survey,
      responses.map((r) => r.subjectId),
    ),
    subjects,
  };
}

async function submit(userId, surveyId, body) {
  const student = await getStudent(userId);
  const survey = await allowedSurvey(student.id, surveyId);

  if (!isOpen(survey)) {
    fail(409, "SURVEY_CLOSED", "Survei belum dibuka atau sudah ditutup.");
  }

  const subjects = await prisma.subject.findMany({
    where: {
      id: { in: body.subjectIds },
      type: "Pilihan",
    },
    select: { id: true },
  });

  if (subjects.length !== body.subjectIds.length) {
    fail(400, "INVALID_SUBJECT", "Terdapat mata pelajaran yang tidak valid.");
  }

  const today = parseDateOnly(todayLocal());

  await prisma.$transaction(
    async (tx) => {
      const current = await tx.electiveSurvey.findUnique({
        where: { id: surveyId },
      });

      if (!current || !isOpen(current)) {
        fail(409, "SURVEY_CLOSED", "Survei sudah tidak menerima jawaban.");
      }

      await tx.electiveSurveyResponse.deleteMany({
        where: {
          surveyId,
          studentId: student.id,
        },
      });

      await tx.electiveSurveyResponse.createMany({
        data: body.subjectIds.map((subjectId) => ({
          surveyId,
          studentId: student.id,
          subjectId,
          filledDate: today,
        })),
      });
    },
    {
      isolationLevel: "Serializable",
    },
  );

  return {
    surveyId,
    studentId: student.id,
    selectedSubjectIds: body.subjectIds,
    filledDate: todayLocal(),
    message: "Jawaban survei berhasil disimpan.",
  };
}

module.exports = {
  list,
  detail,
  submit,
};
