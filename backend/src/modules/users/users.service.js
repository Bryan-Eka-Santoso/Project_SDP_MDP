const bcrypt = require('bcrypt');
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { toPrismaPagination } = require('../../utils/pagination');

const BCRYPT_ROUNDS = 10;

const USER_SELECT = {
  id: true,
  email: true,
  name: true,
  role: true,
  status: true,
  createdAt: true,
  updatedAt: true,
};

const USER_DETAIL_SELECT = {
  ...USER_SELECT,
  teacher: {
    select: {
      id: true,
      employeeNumber: true,
      isHomeroomTeacher: true,
    },
  },
  student: {
    select: {
      id: true,
      nis: true,
      birthDate: true,
      phone: true,
    },
  },
  guardian: {
    select: {
      id: true,
      phone: true,
    },
  },
};

/**
 * F-MST-01: Mengambil daftar pengguna dengan filter dan paginasi.
 */
async function listUsers({ page, limit, q, role, status }) {
  const where = {};

  if (q) {
    where.OR = [
      { name: { contains: q } },
      { email: { contains: q } },
    ];
  }

  if (role) {
    where.role = role;
  }

  if (status) {
    where.status = status;
  }

  const [total, users] = await Promise.all([
    prisma.user.count({ where }),
    prisma.user.findMany({
      where,
      select: USER_SELECT,
      ...toPrismaPagination({ page, limit }),
      orderBy: { id: 'desc' },
    }),
  ]);

  return { users, total };
}

/**
 * F-MST-01: Mengambil profil detail pengguna berdasarkan ID.
 */
async function getUserById(id) {
  const user = await prisma.user.findUnique({
    where: { id },
    select: USER_DETAIL_SELECT,
  });

  if (!user) {
    throw new AppError(404, 'NOT_FOUND', 'Pengguna tidak ditemukan.');
  }

  return user;
}

/**
 * F-MST-01: Menambah pengguna baru dengan password awal yang di-hash bcrypt.
 */
async function createUser({ email, password, name, role }) {
  const existing = await prisma.user.findUnique({
    where: { email },
    select: { id: true },
  });

  if (existing) {
    throw new AppError(409, 'DUPLICATE', 'Email sudah terdaftar.', [
      { field: 'body.email', message: 'Email sudah terdaftar.' },
    ]);
  }

  const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);

  return prisma.user.create({
    data: {
      email,
      passwordHash,
      name,
      role,
      status: 'ACTIVE',
    },
    select: USER_SELECT,
  });
}

/**
 * F-MST-01: Mengubah profil dasar pengguna (nama atau email).
 */
async function updateUser(id, { name, email }) {
  const user = await prisma.user.findUnique({
    where: { id },
    select: { id: true, email: true },
  });

  if (!user) {
    throw new AppError(404, 'NOT_FOUND', 'Pengguna tidak ditemukan.');
  }

  if (email && email !== user.email) {
    const existing = await prisma.user.findUnique({
      where: { email },
      select: { id: true },
    });
    if (existing) {
      throw new AppError(409, 'DUPLICATE', 'Email sudah terdaftar.', [
        { field: 'body.email', message: 'Email sudah terdaftar.' },
      ]);
    }
  }

  return prisma.user.update({
    where: { id },
    data: {
      ...(name !== undefined && { name }),
      ...(email !== undefined && { email }),
    },
    select: USER_SELECT,
  });
}

/**
 * F-MST-02: Mengatur / mengganti role pengguna.
 */
async function updateRole(currentUserId, targetUserId, { role }) {
  const user = await prisma.user.findUnique({
    where: { id: targetUserId },
    include: {
      teacher: { select: { id: true } },
      student: { select: { id: true } },
      guardian: { select: { id: true } },
    },
  });

  if (!user) {
    throw new AppError(404, 'NOT_FOUND', 'Pengguna tidak ditemukan.');
  }

  // Admin tidak bisa mengubah role akunnya sendiri menjadi selain ADMIN
  if (currentUserId === targetUserId && role !== 'ADMIN') {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Admin tidak dapat mengubah role akunnya sendiri.',
    );
  }

  // Cek apakah user sudah memiliki profil yang bentrok dengan perubahan role
  if (user.role !== role) {
    if (user.student && role !== 'STUDENT') {
      throw new AppError(
        409,
        'INVALID_STATE',
        'Role tidak dapat diubah karena akun ini sudah terhubung ke data siswa.',
      );
    }
    if (user.teacher && role !== 'TEACHER') {
      throw new AppError(
        409,
        'INVALID_STATE',
        'Role tidak dapat diubah karena akun ini sudah terhubung ke data guru.',
      );
    }
    if (user.guardian && role !== 'GUARDIAN') {
      throw new AppError(
        409,
        'INVALID_STATE',
        'Role tidak dapat diubah karena akun ini sudah terhubung ke data wali murid.',
      );
    }
  }

  return prisma.$transaction(async (tx) => {
    const updated = await tx.user.update({
      where: { id: targetUserId },
      data: { role },
      select: USER_SELECT,
    });

    // Cabut semua refresh token agar user login ulang dengan role baru
    await tx.refreshToken.updateMany({
      where: { userId: targetUserId, revokedAt: null },
      data: { revokedAt: new Date() },
    });

    return updated;
  });
}

/**
 * F-MST-01: Memblokir atau membuka blokir pengguna.
 */
async function updateStatus(currentUserId, targetUserId, { status }) {
  const user = await prisma.user.findUnique({
    where: { id: targetUserId },
    select: { id: true, status: true },
  });

  if (!user) {
    throw new AppError(404, 'NOT_FOUND', 'Pengguna tidak ditemukan.');
  }

  // Admin tidak bisa memblokir akunnya sendiri (docs/BUSINESS_RULES.md bagian 11)
  if (currentUserId === targetUserId && status === 'BLOCKED') {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Admin tidak dapat memblokir akunnya sendiri.',
    );
  }

  return prisma.$transaction(async (tx) => {
    const updated = await tx.user.update({
      where: { id: targetUserId },
      data: { status },
      select: USER_SELECT,
    });

    // Jika diblokir, cabut seluruh refresh token dan hapus token FCM
    if (status === 'BLOCKED') {
      await tx.refreshToken.updateMany({
        where: { userId: targetUserId, revokedAt: null },
        data: { revokedAt: new Date() },
      });
      await tx.deviceToken.deleteMany({
        where: { userId: targetUserId },
      });
    }

    return updated;
  });
}

/**
 * F-MST-03: Reset password pengguna oleh admin.
 */
async function resetPassword(targetUserId, { newPassword }) {
  const user = await prisma.user.findUnique({
    where: { id: targetUserId },
    select: { id: true },
  });

  if (!user) {
    throw new AppError(404, 'NOT_FOUND', 'Pengguna tidak ditemukan.');
  }

  const passwordHash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);

  return prisma.$transaction(async (tx) => {
    await tx.user.update({
      where: { id: targetUserId },
      data: { passwordHash },
    });

    // Cabut semua sesi lama
    await tx.refreshToken.updateMany({
      where: { userId: targetUserId, revokedAt: null },
      data: { revokedAt: new Date() },
    });

    return { id: targetUserId, message: 'Password pengguna berhasil direset.' };
  });
}

/**
 * F-MST-01: Menghapus pengguna (hanya jika belum memiliki data akademik).
 */
async function deleteUser(currentUserId, targetUserId) {
  const user = await prisma.user.findUnique({
    where: { id: targetUserId },
    include: {
      teacher: { select: { id: true } },
      student: { select: { id: true } },
      guardian: { select: { id: true } },
      announcements: { select: { id: true }, take: 1 },
      facilityBookings: { select: { id: true }, take: 1 },
    },
  });

  if (!user) {
    throw new AppError(404, 'NOT_FOUND', 'Pengguna tidak ditemukan.');
  }

  // Admin tidak bisa menghapus akunnya sendiri (docs/BUSINESS_RULES.md bagian 11)
  if (currentUserId === targetUserId) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Admin tidak dapat menghapus akunnya sendiri.',
    );
  }

  // Tidak boleh menghapus user yang sudah memiliki data akademik / data terkait
  const hasAcademicData =
    Boolean(user.teacher) ||
    Boolean(user.student) ||
    Boolean(user.guardian) ||
    user.announcements.length > 0 ||
    user.facilityBookings.length > 0;

  if (hasAcademicData) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Tidak dapat menghapus pengguna yang sudah memiliki data profil atau data akademik. Silakan gunakan fitur blokir akun.',
    );
  }

  await prisma.user.delete({
    where: { id: targetUserId },
  });

  return true;
}

module.exports = {
  listUsers,
  getUserById,
  createUser,
  updateUser,
  updateRole,
  updateStatus,
  resetPassword,
  deleteUser,
};
