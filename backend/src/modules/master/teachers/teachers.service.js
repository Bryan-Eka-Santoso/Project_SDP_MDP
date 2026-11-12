const bcrypt = require('bcrypt');
const prisma = require('../../../lib/prisma');
const AppError = require('../../../utils/AppError');
const { toPrismaPagination } = require('../../../utils/pagination');

const BCRYPT_ROUNDS = 10;

const TEACHER_SELECT = {
  id: true,
  employeeNumber: true,
  isHomeroomTeacher: true,
  createdAt: true,
  updatedAt: true,
  user: {
    select: {
      id: true,
      email: true,
      name: true,
      status: true,
    },
  },
};

/**
 * F-MST-05: Mengambil daftar guru dengan pencarian dan filter.
 */
async function listTeachers({ page, limit, q, isHomeroomTeacher }) {
  const where = {};

  if (q) {
    where.OR = [
      { employeeNumber: { contains: q } },
      { user: { name: { contains: q } } },
      { user: { email: { contains: q } } },
    ];
  }

  if (isHomeroomTeacher !== undefined) {
    where.isHomeroomTeacher = isHomeroomTeacher;
  }

  const [total, items] = await Promise.all([
    prisma.teacher.count({ where }),
    prisma.teacher.findMany({
      where,
      select: TEACHER_SELECT,
      ...toPrismaPagination({ page, limit }),
      orderBy: { employeeNumber: 'asc' },
    }),
  ]);

  return { teachers: items, total };
}

/**
 * F-MST-05: Mengambil profil lengkap guru berdasarkan ID.
 */
async function getTeacherById(id) {
  const teacher = await prisma.teacher.findUnique({
    where: { id },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          name: true,
          status: true,
        },
      },
      homeroomClasses: {
        select: {
          id: true,
          name: true,
          gradeLevel: true,
          academicYear: { select: { id: true, name: true, isActive: true } },
        },
      },
      teachingAssignments: {
        select: {
          id: true,
          subject: { select: { id: true, code: true, name: true } },
          classGroup: { select: { id: true, name: true } },
          semester: { select: { id: true, type: true, isActive: true } },
        },
      },
    },
  });

  if (!teacher) {
    throw new AppError(404, 'NOT_FOUND', 'Data guru tidak ditemukan.');
  }

  return teacher;
}

/**
 * F-MST-05: Menambah data guru baru beserta akun user-nya dalam 1 transaksi.
 */
async function createTeacher({ name, email, password, employeeNumber }) {
  // Cek keunikan email
  const existingUser = await prisma.user.findUnique({
    where: { email },
    select: { id: true },
  });
  if (existingUser) {
    throw new AppError(409, 'DUPLICATE', 'Email sudah terdaftar.', [
      { field: 'body.email', message: 'Email sudah terdaftar.' },
    ]);
  }

  // Cek keunikan NIP / kode guru
  const existingTeacher = await prisma.teacher.findUnique({
    where: { employeeNumber },
    select: { id: true },
  });
  if (existingTeacher) {
    throw new AppError(409, 'DUPLICATE', 'NIP / Kode guru sudah terdaftar.', [
      { field: 'body.employeeNumber', message: 'NIP / Kode guru sudah terdaftar.' },
    ]);
  }

  const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);

  return prisma.$transaction(async (tx) => {
    const user = await tx.user.create({
      data: {
        name,
        email,
        passwordHash,
        role: 'TEACHER',
        status: 'ACTIVE',
      },
    });

    const teacher = await tx.teacher.create({
      data: {
        userId: user.id,
        employeeNumber,
        isHomeroomTeacher: false,
      },
      select: TEACHER_SELECT,
    });

    return teacher;
  });
}

/**
 * F-MST-05: Mengubah data guru.
 */
async function updateTeacher(id, { name, email, employeeNumber }) {
  const teacher = await prisma.teacher.findUnique({
    where: { id },
    include: { user: { select: { id: true, email: true } } },
  });
  if (!teacher) {
    throw new AppError(404, 'NOT_FOUND', 'Data guru tidak ditemukan.');
  }

  if (email && email !== teacher.user.email) {
    const duplicateEmail = await prisma.user.findUnique({
      where: { email },
      select: { id: true },
    });
    if (duplicateEmail) {
      throw new AppError(409, 'DUPLICATE', 'Email sudah terdaftar.', [
        { field: 'body.email', message: 'Email sudah terdaftar.' },
      ]);
    }
  }

  if (employeeNumber && employeeNumber !== teacher.employeeNumber) {
    const duplicateNip = await prisma.teacher.findUnique({
      where: { employeeNumber },
      select: { id: true },
    });
    if (duplicateNip) {
      throw new AppError(409, 'DUPLICATE', 'NIP / Kode guru sudah terdaftar.', [
        { field: 'body.employeeNumber', message: 'NIP / Kode guru sudah terdaftar.' },
      ]);
    }
  }

  return prisma.$transaction(async (tx) => {
    if (name !== undefined || email !== undefined) {
      await tx.user.update({
        where: { id: teacher.user.id },
        data: {
          ...(name !== undefined && { name }),
          ...(email !== undefined && { email }),
        },
      });
    }

    if (employeeNumber !== undefined) {
      await tx.teacher.update({
        where: { id },
        data: { employeeNumber },
      });
    }

    return tx.teacher.findUnique({
      where: { id },
      select: TEACHER_SELECT,
    });
  });
}

/**
 * F-MST-05: Menghapus data guru (jika belum ada penugasan mengajar / riwayat).
 */
async function deleteTeacher(id) {
  const teacher = await prisma.teacher.findUnique({
    where: { id },
    include: {
      _count: {
        select: {
          homeroomClasses: true,
          teachingAssignments: true,
          reportedViolations: true,
          verifiedLeaveRequests: true,
        },
      },
    },
  });

  if (!teacher) {
    throw new AppError(404, 'NOT_FOUND', 'Data guru tidak ditemukan.');
  }

  if (
    teacher._count.homeroomClasses > 0 ||
    teacher._count.teachingAssignments > 0 ||
    teacher._count.reportedViolations > 0 ||
    teacher._count.verifiedLeaveRequests > 0
  ) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Data guru tidak dapat dihapus karena sudah memiliki riwayat mengajar, wali kelas, atau data akademik. Silakan gunakan fitur blokir akun.',
    );
  }

  return prisma.$transaction(async (tx) => {
    await tx.teacher.delete({ where: { id } });
    await tx.user.delete({ where: { id: teacher.userId } });
  });
}

module.exports = {
  listTeachers,
  getTeacherById,
  createTeacher,
  updateTeacher,
  deleteTeacher,
};
