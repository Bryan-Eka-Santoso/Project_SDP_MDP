// Cakupan akses pengumuman (docs/PERMISSIONS.md & docs/BUSINESS_RULES.md bagian 7).
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

async function buildReadWhere(user, { scope, classGroupId, studentId, q }) {
  if (studentId !== undefined && user.role !== 'GUARDIAN') {
    throw new AppError(400, 'VALIDATION_ERROR', 'Filter studentId hanya berlaku untuk wali murid.', [
      { field: 'query.studentId', message: 'Parameter studentId hanya untuk wali murid.' },
    ]);
  }

  const filters = [];
  if (scope) filters.push({ scope });
  if (classGroupId) filters.push({ classGroupId });
  if (q) {
    filters.push({
      OR: [
        { title: { contains: q } },
        { content: { contains: q } },
      ],
    });
  }

  if (user.role === 'ADMIN') {
    return filters.length > 0 ? { AND: filters } : {};
  }

  let scopeClause;
  if (user.role === 'STUDENT') {
    scopeClause = {
      OR: [
        { scope: 'SCHOOL' },
        {
          scope: 'CLASS',
          classGroup: {
            academicYear: { isActive: true },
            members: { some: { student: { userId: user.id } } },
          },
        },
      ],
    };
  } else if (user.role === 'GUARDIAN') {
    if (studentId !== undefined) {
      const relation = await prisma.studentGuardian.findFirst({
        where: { studentId, guardian: { userId: user.id } },
        select: { studentId: true },
      });
      if (!relation) {
        throw new AppError(404, 'NOT_FOUND', 'Data siswa tidak ditemukan.');
      }
    }
    const childFilter = studentId !== undefined
      ? { studentId }
      : { student: { guardians: { some: { guardian: { userId: user.id } } } } };

    scopeClause = {
      OR: [
        { scope: 'SCHOOL' },
        {
          scope: 'CLASS',
          classGroup: {
            academicYear: { isActive: true },
            members: { some: childFilter },
          },
        },
      ],
    };
  } else if (user.role === 'TEACHER') {
    scopeClause = {
      OR: [
        { scope: 'SCHOOL' },
        {
          scope: 'CLASS',
          classGroup: {
            academicYear: { isActive: true },
            homeroomTeacher: { userId: user.id },
          },
        },
        {
          scope: 'CLASS',
          classGroup: {
            teachingAssignments: {
              some: {
                semester: { academicYear: { isActive: true } },
                teacher: { userId: user.id },
              },
            },
          },
        },
      ],
    };
  } else {
    throw new AppError(403, 'FORBIDDEN', 'Tidak memiliki akses ke pengumuman.');
  }

  return filters.length > 0 ? { AND: [scopeClause, ...filters] } : scopeClause;
}

async function assertCanUploadAttachment(user) {
  if (user.role === 'ADMIN') return;
  if (user.role === 'TEACHER') {
    const homeroom = await prisma.classGroup.findFirst({
      where: {
        homeroomTeacher: { userId: user.id },
        academicYear: { isActive: true },
      },
      select: { id: true },
    });
    if (homeroom) return;
  }
  throw new AppError(
    403,
    'FORBIDDEN',
    'Hanya admin atau wali kelas aktif yang dapat mengunggah lampiran pengumuman.',
  );
}

async function assertCanCreate(user, { scope, classGroupId }) {
  if (scope === 'SCHOOL') {
    if (user.role !== 'ADMIN') {
      throw new AppError(403, 'FORBIDDEN', 'Hanya admin yang dapat mengirim pengumuman sekolah.');
    }
    return null;
  }

  if (user.role !== 'ADMIN' && user.role !== 'TEACHER') {
    throw new AppError(403, 'FORBIDDEN', 'Hanya admin atau wali kelas yang dapat mengirim pengumuman kelas.');
  }

  const classGroup = await prisma.classGroup.findUnique({
    where: { id: classGroupId },
    select: {
      id: true,
      name: true,
      academicYear: { select: { isActive: true } },
      homeroomTeacher: { select: { userId: true } },
    },
  });
  if (!classGroup) {
    throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
  }
  if (!classGroup.academicYear.isActive) {
    throw new AppError(
      400,
      'VALIDATION_ERROR',
      'Pengumuman kelas hanya dapat dikirim ke kelas pada tahun ajaran aktif.',
    );
  }
  if (user.role === 'TEACHER' && classGroup.homeroomTeacher?.userId !== user.id) {
    throw new AppError(
      403,
      'FORBIDDEN',
      'Anda hanya dapat mengirim pengumuman ke kelas binaan Anda di tahun ajaran aktif.',
    );
  }
  return classGroup;
}

async function assertCanManage(user, existing) {
  if (user.role === 'ADMIN') {
    return;
  }

  if (existing.scope === 'SCHOOL') {
    throw new AppError(403, 'FORBIDDEN', 'Hanya admin yang dapat mengelola pengumuman sekolah.');
  }

  if (user.role === 'TEACHER' && existing.classGroupId) {
    const isHomeroom = await prisma.classGroup.findFirst({
      where: {
        id: existing.classGroupId,
        academicYear: { isActive: true },
        homeroomTeacher: { userId: user.id },
      },
      select: { id: true },
    });
    if (isHomeroom) return;
  }

  throw new AppError(403, 'FORBIDDEN', 'Anda tidak berhak mengubah atau menghapus pengumuman ini.');
}

module.exports = {
  buildReadWhere,
  assertCanUploadAttachment,
  assertCanCreate,
  assertCanManage,
};
