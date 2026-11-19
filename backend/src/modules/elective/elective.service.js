// Master paket mata pelajaran pilihan per tahun ajaran (F-MPP-03).
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { toPrismaPagination } = require('../../utils/pagination');

const PACKAGE_SELECT = {
  id: true,
  academicYearId: true,
  name: true,
  capacity: true,
  createdAt: true,
  updatedAt: true,
  academicYear: { select: { id: true, name: true, isActive: true } },
  items: {
    select: {
      subject: { select: { id: true, code: true, name: true, type: true } },
    },
    orderBy: { subject: { name: 'asc' } },
  },
  _count: { select: { selections: true } },
};

function mapPackage(pkg) {
  return {
    id: pkg.id,
    academicYearId: pkg.academicYearId,
    academicYear: pkg.academicYear,
    name: pkg.name,
    capacity: pkg.capacity,
    subjects: pkg.items.map((item) => item.subject),
    selectionCount: pkg._count?.selections ?? 0,
    createdAt: pkg.createdAt,
    updatedAt: pkg.updatedAt,
  };
}

async function resolveListAcademicYearId(academicYearId) {
  if (academicYearId !== undefined) return academicYearId;
  const active = await prisma.academicYear.findFirst({
    where: { isActive: true },
    select: { id: true },
  });
  return active?.id ?? null;
}

async function assertAcademicYearExists(academicYearId) {
  const year = await prisma.academicYear.findUnique({
    where: { id: academicYearId },
    select: { id: true },
  });
  if (!year) {
    throw new AppError(404, 'NOT_FOUND', 'Tahun ajaran tidak ditemukan.');
  }
}

async function assertSubjectsExist(subjectIds) {
  const found = await prisma.subject.findMany({
    where: { id: { in: subjectIds } },
    select: { id: true },
  });
  const foundIds = new Set(found.map((s) => s.id));
  const missing = subjectIds.filter((id) => !foundIds.has(id));
  if (missing.length > 0) {
    throw new AppError(400, 'VALIDATION_ERROR', 'Sebagian mata pelajaran tidak ditemukan.', [
      { field: 'body.subjectIds', message: `Mata pelajaran dengan ID ${missing.join(', ')} tidak ditemukan.` },
    ]);
  }
}

async function assertUniqueNameInYear(academicYearId, name, excludeId) {
  const existing = await prisma.subjectPackage.findMany({
    where: {
      academicYearId,
      ...(excludeId !== undefined && { id: { not: excludeId } }),
    },
    select: { id: true, name: true },
  });
  const duplicate = existing.some(
    (item) => item.name.trim().toLowerCase() === name.trim().toLowerCase(),
  );
  if (duplicate) {
    throw new AppError(
      409,
      'DUPLICATE',
      'Paket mata pelajaran dengan nama yang sama sudah ada pada tahun ajaran ini.',
      [{ field: 'body.name', message: 'Nama paket sudah digunakan pada tahun ajaran ini.' }],
    );
  }
}

async function listSubjectPackages({ page, limit, academicYearId, q }) {
  const resolvedYearId = await resolveListAcademicYearId(academicYearId);
  if (resolvedYearId === null) {
    return { packages: [], total: 0 };
  }

  const where = { academicYearId: resolvedYearId };
  if (q) where.name = { contains: q };

  const [total, items] = await Promise.all([
    prisma.subjectPackage.count({ where }),
    prisma.subjectPackage.findMany({
      where,
      select: PACKAGE_SELECT,
      ...toPrismaPagination({ page, limit }),
      orderBy: [{ name: 'asc' }, { id: 'asc' }],
    }),
  ]);

  return { packages: items.map(mapPackage), total };
}

async function getSubjectPackageById(id) {
  const pkg = await prisma.subjectPackage.findUnique({
    where: { id },
    select: PACKAGE_SELECT,
  });
  if (!pkg) {
    throw new AppError(404, 'NOT_FOUND', 'Paket mata pelajaran tidak ditemukan.');
  }
  return mapPackage(pkg);
}

async function createSubjectPackage({ academicYearId, name, capacity, subjectIds }) {
  await assertAcademicYearExists(academicYearId);
  await Promise.all([
    assertUniqueNameInYear(academicYearId, name),
    assertSubjectsExist(subjectIds),
  ]);

  const created = await prisma.$transaction(async (tx) => {
    return tx.subjectPackage.create({
      data: {
        academicYearId,
        name,
        capacity,
        items: {
          create: subjectIds.map((subjectId) => ({ subjectId })),
        },
      },
      select: PACKAGE_SELECT,
    });
  });

  return mapPackage(created);
}

async function updateSubjectPackage(id, { name, capacity, subjectIds }) {
  const existing = await prisma.subjectPackage.findUnique({
    where: { id },
    select: { id: true, academicYearId: true, name: true },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Paket mata pelajaran tidak ditemukan.');
  }

  const checks = [];
  if (name !== undefined && name.trim().toLowerCase() !== existing.name.trim().toLowerCase()) {
    checks.push(assertUniqueNameInYear(existing.academicYearId, name, id));
  }
  if (subjectIds !== undefined) {
    checks.push(assertSubjectsExist(subjectIds));
  }
  await Promise.all(checks);

  const updated = await prisma.$transaction(async (tx) => {
    if (subjectIds !== undefined) {
      await tx.subjectPackageItem.deleteMany({ where: { packageId: id } });
      await tx.subjectPackageItem.createMany({
        data: subjectIds.map((subjectId) => ({ packageId: id, subjectId })),
      });
    }
    return tx.subjectPackage.update({
      where: { id },
      data: {
        ...(name !== undefined && { name }),
        ...(capacity !== undefined && { capacity }),
      },
      select: PACKAGE_SELECT,
    });
  });

  return mapPackage(updated);
}

async function deleteSubjectPackage(id) {
  const existing = await prisma.subjectPackage.findUnique({
    where: { id },
    select: { id: true, _count: { select: { selections: true } } },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Paket mata pelajaran tidak ditemukan.');
  }
  if (existing._count.selections > 0) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Paket mata pelajaran tidak dapat dihapus karena sudah dipilih oleh siswa.',
    );
  }

  await prisma.$transaction(async (tx) => {
    await tx.subjectPackageItem.deleteMany({ where: { packageId: id } });
    await tx.subjectPackage.delete({ where: { id } });
  });
}

module.exports = {
  listSubjectPackages,
  getSubjectPackageById,
  createSubjectPackage,
  updateSubjectPackage,
  deleteSubjectPackage,
};
