const prisma = require('../../../lib/prisma');
const AppError = require('../../../utils/AppError');
const { toPrismaPagination } = require('../../../utils/pagination');
const { formatDateOnly } = require('../../../utils/date');
const { syncHomeroomTeachers } = require('../master.helper');

function mapDates(item) {
  if (!item) return null;
  return {
    ...item,
    startDate: formatDateOnly(item.startDate),
    endDate: formatDateOnly(item.endDate),
  };
}

// ==========================================
// A. TAHUN AJARAN (ACADEMIC YEAR)
// ==========================================

async function listAcademicYears({ page, limit, q, isActive }) {
  const where = {};
  if (q) where.name = { contains: q };
  if (isActive !== undefined) where.isActive = isActive;

  const [total, items] = await Promise.all([
    prisma.academicYear.count({ where }),
    prisma.academicYear.findMany({
      where,
      select: {
        id: true,
        name: true,
        startDate: true,
        endDate: true,
        isActive: true,
        createdAt: true,
        updatedAt: true,
        _count: {
          select: { classGroups: true, semesters: true },
        },
      },
      ...toPrismaPagination({ page, limit }),
      orderBy: { startDate: 'desc' },
    }),
  ]);

  return { academicYears: items.map(mapDates), total };
}

async function getActiveAcademicYear() {
  const activeYear = await prisma.academicYear.findFirst({
    where: { isActive: true },
    include: {
      semesters: {
        where: { isActive: true },
        take: 1,
      },
    },
  });

  if (!activeYear) {
    throw new AppError(404, 'NOT_FOUND', 'Tidak ada tahun ajaran aktif.');
  }

  return {
    ...mapDates(activeYear),
    activeSemester: activeYear.semesters[0] ? mapDates(activeYear.semesters[0]) : null,
  };
}

async function getAcademicYearById(id) {
  const item = await prisma.academicYear.findUnique({
    where: { id },
    include: {
      semesters: {
        orderBy: { startDate: 'asc' },
      },
      _count: {
        select: { classGroups: true },
      },
    },
  });

  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Tahun ajaran tidak ditemukan.');
  }

  return {
    ...mapDates(item),
    semesters: item.semesters.map(mapDates),
  };
}

async function createAcademicYear({ name, startDate, endDate, isActive }) {
  const existing = await prisma.academicYear.findUnique({
    where: { name },
    select: { id: true },
  });
  if (existing) {
    throw new AppError(409, 'DUPLICATE', 'Tahun ajaran dengan nama ini sudah terdaftar.', [
      { field: 'body.name', message: 'Nama tahun ajaran sudah ada.' },
    ]);
  }

  return prisma.$transaction(async (tx) => {
    if (isActive) {
      await tx.academicYear.updateMany({
        where: { isActive: true },
        data: { isActive: false },
      });
    }

    const created = await tx.academicYear.create({
      data: {
        name,
        startDate: new Date(startDate),
        endDate: new Date(endDate),
        isActive,
      },
    });

    if (isActive) {
      await syncHomeroomTeachers(tx, created.id);
    }

    return mapDates(created);
  });
}

async function updateAcademicYear(id, { name, startDate, endDate }) {
  const existing = await prisma.academicYear.findUnique({
    where: { id },
    select: { id: true, name: true },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Tahun ajaran tidak ditemukan.');
  }

  if (name && name !== existing.name) {
    const duplicate = await prisma.academicYear.findUnique({
      where: { name },
      select: { id: true },
    });
    if (duplicate) {
      throw new AppError(409, 'DUPLICATE', 'Tahun ajaran dengan nama ini sudah terdaftar.', [
        { field: 'body.name', message: 'Nama tahun ajaran sudah ada.' },
      ]);
    }
  }

  const updated = await prisma.academicYear.update({
    where: { id },
    data: {
      ...(name !== undefined && { name }),
      ...(startDate !== undefined && { startDate: new Date(startDate) }),
      ...(endDate !== undefined && { endDate: new Date(endDate) }),
    },
  });

  return mapDates(updated);
}

async function setActiveAcademicYear(id) {
  const target = await prisma.academicYear.findUnique({
    where: { id },
    select: { id: true },
  });
  if (!target) {
    throw new AppError(404, 'NOT_FOUND', 'Tahun ajaran tidak ditemukan.');
  }

  return prisma.$transaction(async (tx) => {
    // Nonaktifkan tahun ajaran aktif sebelumnya
    await tx.academicYear.updateMany({
      where: { isActive: true },
      data: { isActive: false },
    });

    // Aktifkan tahun ajaran target
    const activated = await tx.academicYear.update({
      where: { id },
      data: { isActive: true },
    });

    // Hitung ulang status wali kelas guru untuk tahun ajaran aktif ini
    await syncHomeroomTeachers(tx, id);

    return mapDates(activated);
  });
}

async function deleteAcademicYear(id) {
  const item = await prisma.academicYear.findUnique({
    where: { id },
    include: {
      _count: {
        select: {
          classGroups: true,
          subjectPackages: true,
          electiveSurveys: true,
          gradePredicates: true,
        },
      },
    },
  });

  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Tahun ajaran tidak ditemukan.');
  }

  if (
    item._count.classGroups > 0 ||
    item._count.subjectPackages > 0 ||
    item._count.electiveSurveys > 0 ||
    item._count.gradePredicates > 0
  ) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Tahun ajaran tidak dapat dihapus karena sudah memiliki data kelas atau data akademik terkait.',
    );
  }

  return prisma.$transaction(async (tx) => {
    // Hapus semester jika ada
    await tx.semester.deleteMany({ where: { academicYearId: id } });
    await tx.academicYear.delete({ where: { id } });
  });
}

// ==========================================
// B. SEMESTER
// ==========================================

async function listSemesters({ page, limit, academicYearId, type, isActive }) {
  const where = {};
  if (academicYearId) where.academicYearId = academicYearId;
  if (type) where.type = type;
  if (isActive !== undefined) where.isActive = isActive;

  const [total, items] = await Promise.all([
    prisma.semester.count({ where }),
    prisma.semester.findMany({
      where,
      include: {
        academicYear: { select: { id: true, name: true } },
        _count: { select: { teachingAssignments: true } },
      },
      ...toPrismaPagination({ page, limit }),
      orderBy: { startDate: 'desc' },
    }),
  ]);

  return { semesters: items.map(mapDates), total };
}

async function getActiveSemester() {
  const activeSemester = await prisma.semester.findFirst({
    where: { isActive: true },
    include: {
      academicYear: { select: { id: true, name: true, isActive: true } },
    },
  });

  if (!activeSemester) {
    throw new AppError(404, 'NOT_FOUND', 'Tidak ada semester aktif.');
  }

  return mapDates(activeSemester);
}

async function getSemesterById(id) {
  const item = await prisma.semester.findUnique({
    where: { id },
    include: {
      academicYear: { select: { id: true, name: true } },
      _count: { select: { teachingAssignments: true } },
    },
  });

  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Semester tidak ditemukan.');
  }

  return mapDates(item);
}

async function createSemester({ academicYearId, type, startDate, endDate, isActive }) {
  const year = await prisma.academicYear.findUnique({
    where: { id: academicYearId },
    select: { id: true },
  });
  if (!year) {
    throw new AppError(404, 'NOT_FOUND', 'Tahun ajaran tidak ditemukan.');
  }

  const existing = await prisma.semester.findUnique({
    where: {
      academicYearId_type: { academicYearId, type },
    },
  });
  if (existing) {
    throw new AppError(409, 'DUPLICATE', `Semester ${type} sudah terdaftar pada tahun ajaran ini.`);
  }

  return prisma.$transaction(async (tx) => {
    if (isActive) {
      await tx.semester.updateMany({
        where: { isActive: true },
        data: { isActive: false },
      });
    }

    const created = await tx.semester.create({
      data: {
        academicYearId,
        type,
        startDate: new Date(startDate),
        endDate: new Date(endDate),
        isActive,
      },
      include: {
        academicYear: { select: { id: true, name: true } },
      },
    });

    return mapDates(created);
  });
}

async function updateSemester(id, { startDate, endDate }) {
  const existing = await prisma.semester.findUnique({
    where: { id },
    select: { id: true },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Semester tidak ditemukan.');
  }

  const updated = await prisma.semester.update({
    where: { id },
    data: {
      ...(startDate !== undefined && { startDate: new Date(startDate) }),
      ...(endDate !== undefined && { endDate: new Date(endDate) }),
    },
    include: {
      academicYear: { select: { id: true, name: true } },
    },
  });

  return mapDates(updated);
}

async function setActiveSemester(id) {
  const target = await prisma.semester.findUnique({
    where: { id },
    select: { id: true },
  });
  if (!target) {
    throw new AppError(404, 'NOT_FOUND', 'Semester tidak ditemukan.');
  }

  return prisma.$transaction(async (tx) => {
    await tx.semester.updateMany({
      where: { isActive: true },
      data: { isActive: false },
    });

    const activated = await tx.semester.update({
      where: { id },
      data: { isActive: true },
      include: {
        academicYear: { select: { id: true, name: true } },
      },
    });

    return mapDates(activated);
  });
}

async function deleteSemester(id) {
  const item = await prisma.semester.findUnique({
    where: { id },
    include: {
      _count: { select: { teachingAssignments: true } },
    },
  });

  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Semester tidak ditemukan.');
  }

  if (item._count.teachingAssignments > 0) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Semester tidak dapat dihapus karena sudah memiliki penugasan guru terkait.',
    );
  }

  await prisma.semester.delete({ where: { id } });
}

module.exports = {
  listAcademicYears,
  getActiveAcademicYear,
  getAcademicYearById,
  createAcademicYear,
  updateAcademicYear,
  setActiveAcademicYear,
  deleteAcademicYear,
  listSemesters,
  getActiveSemester,
  getSemesterById,
  createSemester,
  updateSemester,
  setActiveSemester,
  deleteSemester,
};
