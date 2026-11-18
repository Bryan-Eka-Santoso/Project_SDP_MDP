const prisma = require("../../lib/prisma");
const AppError = require("../../utils/AppError");

const {
  parseDateOnly,
  formatDateOnly,
  todayLocal,
} = require("../../utils/date");

function fail(status, code, message) {
  throw new AppError(status, code, message);
}

const include = {
  academicYear: {
    select: {
      name: true,
    },
  },
  _count: {
    select: {
      responses: true,
    },
  },
};

function formatData(item) {
  return {
    id: item.id,
    academicYearId: item.academicYearId,
    academicYearName: item.academicYear?.name || null,
    gradeLevel: item.gradeLevel,
    title: item.title,
    openDate: formatDateOnly(item.openDate),
    closeDate: formatDateOnly(item.closeDate),
    status: item.status,
    responseCount: item._count?.responses ?? 0,
  };
}

async function lookups() {
  const [academicYears, subjects] = await Promise.all([
    prisma.academicYear.findMany({
      select: {
        id: true,
        name: true,
        isActive: true,
      },
      orderBy: {
        startDate: "desc",
      },
    }),

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
  ]);

  return {
    academicYears,
    subjects,
  };
}

async function list() {
  const data = await prisma.electiveSurvey.findMany({
    include,
    orderBy: {
      createdAt: "desc",
    },
  });

  return data.map(formatData);
}

async function create(body) {
  let openDate;
  let closeDate;

  try {
    openDate = parseDateOnly(body.openDate);
    closeDate = parseDateOnly(body.closeDate);
  } catch {
    fail(400, "INVALID_DATE", "Tanggal survei tidak valid.");
  }

  const academicYear = await prisma.academicYear.findUnique({
    where: {
      id: body.academicYearId,
    },
  });

  if (!academicYear) {
    fail(404, "NOT_FOUND", "Tahun ajaran tidak ditemukan.");
  }

  if (openDate < academicYear.startDate || closeDate > academicYear.endDate) {
    fail(
      400,
      "INVALID_PERIOD",
      "Periode survei harus berada di dalam tahun ajaran.",
    );
  }

  const survey = await prisma.electiveSurvey.create({
    data: {
      academicYearId: body.academicYearId,
      gradeLevel: body.gradeLevel,
      title: body.title,
      openDate,
      closeDate,
      status: "DRAFT",
    },
    include,
  });

  return formatData(survey);
}

async function publish(id) {
  const survey = await prisma.electiveSurvey.findUnique({
    where: { id },
  });

  if (!survey) {
    fail(404, "NOT_FOUND", "Survei tidak ditemukan.");
  }

  if (survey.status !== "DRAFT") {
    fail(409, "INVALID_STATE", "Hanya draft yang dapat diterbitkan.");
  }

  if (survey.closeDate < parseDateOnly(todayLocal())) {
    fail(400, "INVALID_PERIOD", "Tanggal tutup survei sudah berlalu.");
  }

  const available = await prisma.subject.count({
    where: {
      type: "Pilihan",
    },
  });

  if (available === 0) {
    fail(409, "NO_SUBJECTS", "Belum ada mata pelajaran bertipe Pilihan.");
  }

  const result = await prisma.electiveSurvey.updateMany({
    where: {
      id,
      status: "DRAFT",
    },
    data: {
      status: "PUBLISHED",
    },
  });

  if (result.count !== 1) {
    fail(409, "INVALID_STATE", "Survei telah diproses.");
  }

  const updated = await prisma.electiveSurvey.findUnique({
    where: { id },
    include,
  });

  return formatData(updated);
}

async function close(id) {
  const result = await prisma.electiveSurvey.updateMany({
    where: {
      id,
      status: "PUBLISHED",
    },
    data: {
      status: "CLOSED",
    },
  });

  if (result.count !== 1) {
    fail(409, "INVALID_STATE", "Hanya survei terbit yang dapat ditutup.");
  }

  const updated = await prisma.electiveSurvey.findUnique({
    where: { id },
    include,
  });

  return formatData(updated);
}

async function recap(id) {
  const survey = await prisma.electiveSurvey.findUnique({
    where: { id },
    include,
  });

  if (!survey) {
    fail(404, "NOT_FOUND", "Survei tidak ditemukan.");
  }

  const responses = await prisma.electiveSurveyResponse.findMany({
    where: {
      surveyId: id,
    },
    select: {
      studentId: true,
      subjectId: true,
    },
  });

  const grouped = new Map();

  for (const response of responses) {
    if (!grouped.has(response.subjectId)) {
      grouped.set(response.subjectId, new Set());
    }

    grouped.get(response.subjectId).add(response.studentId);
  }

  const subjects = await prisma.subject.findMany({
    where: {
      id: {
        in: [...grouped.keys()],
      },
    },
    select: {
      id: true,
      code: true,
      name: true,
    },
  });

  const stats = subjects
    .map((subject) => ({
      ...subject,
      studentCount: grouped.get(subject.id)?.size || 0,
    }))
    .sort(
      (a, b) =>
        b.studentCount - a.studentCount || a.name.localeCompare(b.name, "id"),
    );

  return {
    survey: formatData(survey),
    respondentCount: new Set(responses.map((item) => item.studentId)).size,
    selectionCount: responses.length,
    subjects: stats,
  };
}

module.exports = {
  lookups,
  list,
  create,
  publish,
  close,
  recap,
};
