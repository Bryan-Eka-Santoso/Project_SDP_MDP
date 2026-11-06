const crypto = require('node:crypto');
const bcrypt = require('bcrypt');
const prisma = require('../../lib/prisma');
const env = require('../../config/env');
const { signAccessToken } = require('../../lib/jwt');
const AppError = require('../../utils/AppError');
const { formatDateOnly, parseDuration } = require('../../utils/date');

const BCRYPT_ROUNDS = 10;

// Dipakai saat email tidak terdaftar agar waktu respons sama dengan password salah,
// sehingga penyerang tidak bisa menebak email mana yang terdaftar.
const DUMMY_HASH = bcrypt.hashSync('dummy-password-untuk-waktu-respons', BCRYPT_ROUNDS);

const PROFILE_SELECT = {
  id: true,
  email: true,
  name: true,
  role: true,
  teacher: { select: { id: true, employeeNumber: true, isHomeroomTeacher: true } },
  student: { select: { id: true, nis: true, birthDate: true } },
  guardian: {
    select: {
      id: true,
      phone: true,
      students: {
        select: {
          relationship: true,
          student: { select: { id: true, nis: true, user: { select: { name: true } } } },
        },
      },
    },
  },
};

const invalidSession = () =>
  new AppError(401, 'UNAUTHENTICATED', 'Sesi tidak valid. Silakan login kembali.');
const accountBlocked = () =>
  new AppError(403, 'ACCOUNT_BLOCKED', 'Akun Anda diblokir. Hubungi admin sekolah.');

function toProfile(user) {
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    role: user.role,
    teacher: user.teacher,
    student: user.student && {
      ...user.student,
      birthDate: formatDateOnly(user.student.birthDate),
    },
    guardian: user.guardian && {
      id: user.guardian.id,
      phone: user.guardian.phone,
      children: user.guardian.students.map(({ relationship, student }) => ({
        id: student.id,
        name: student.user.name,
        nis: student.nis,
        relationship,
      })),
    },
  };
}

// Refresh token berupa string acak (bukan JWT). Yang disimpan hanya hash SHA-256-nya,
// jadi kebocoran database tidak membocorkan token yang bisa dipakai.
function hashRefreshToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

async function issueTokens(db, user) {
  const refreshToken = crypto.randomBytes(48).toString('base64url');
  await db.refreshToken.create({
    data: {
      userId: user.id,
      tokenHash: hashRefreshToken(refreshToken),
      expiresAt: new Date(Date.now() + parseDuration(env.jwt.refreshTtl)),
    },
  });
  return { accessToken: signAccessToken(user), refreshToken };
}

async function login({ email, password }) {
  const user = await prisma.user.findUnique({
    where: { email },
    select: { ...PROFILE_SELECT, passwordHash: true, status: true },
  });

  const passwordValid = await bcrypt.compare(password, user ? user.passwordHash : DUMMY_HASH);
  if (!user || !passwordValid) {
    throw new AppError(401, 'INVALID_CREDENTIALS', 'Email atau password salah.');
  }
  // Dicek setelah password benar agar status akun tidak bocor ke orang yang tidak tahu password.
  if (user.status === 'BLOCKED') throw accountBlocked();

  const tokens = await issueTokens(prisma, user);
  return { ...tokens, user: toProfile(user) };
}

async function refresh({ refreshToken }) {
  const stored = await prisma.refreshToken.findUnique({
    where: { tokenHash: hashRefreshToken(refreshToken) },
    select: {
      id: true,
      userId: true,
      expiresAt: true,
      revokedAt: true,
      user: { select: { id: true, role: true, status: true } },
    },
  });

  if (!stored) throw invalidSession();

  if (stored.revokedAt) {
    // Token yang sudah dirotasi dipakai lagi: kemungkinan dicuri. Cabut semua sesi user ini.
    await prisma.refreshToken.updateMany({
      where: { userId: stored.userId, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    throw invalidSession();
  }
  if (stored.expiresAt <= new Date()) throw invalidSession();
  if (stored.user.status === 'BLOCKED') throw accountBlocked();

  return prisma.$transaction(async (tx) => {
    // `revokedAt: null` di where mencegah dua request refresh bersamaan memakai token yang sama.
    const { count } = await tx.refreshToken.updateMany({
      where: { id: stored.id, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    if (count !== 1) throw invalidSession();

    return issueTokens(tx, stored.user);
  });
}

// Selalu sukses (idempotent): token yang tidak dikenal atau sudah dicabut tidak dianggap error.
async function logout({ refreshToken, deviceToken }) {
  const stored = await prisma.refreshToken.findUnique({
    where: { tokenHash: hashRefreshToken(refreshToken) },
    select: { id: true, userId: true },
  });
  if (!stored) return;

  const operations = [
    prisma.refreshToken.updateMany({
      where: { id: stored.id, revokedAt: null },
      data: { revokedAt: new Date() },
    }),
  ];
  if (deviceToken) {
    // Hanya token milik user ini, agar orang lain tidak bisa menghapus token perangkat lain.
    operations.push(
      prisma.deviceToken.deleteMany({ where: { token: deviceToken, userId: stored.userId } }),
    );
  }
  await prisma.$transaction(operations);
}

async function getProfile(userId) {
  const user = await prisma.user.findUnique({ where: { id: userId }, select: PROFILE_SELECT });
  if (!user) throw invalidSession();
  return toProfile(user);
}

/**
 * Setelah ganti password, semua sesi lain dicabut (mis. HP yang hilang) dan perangkat ini
 * menerima token baru supaya tidak perlu login ulang.
 */
async function changePassword(userId, { currentPassword, newPassword }) {
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { id: true, role: true, passwordHash: true },
  });
  if (!user) throw invalidSession();

  if (!(await bcrypt.compare(currentPassword, user.passwordHash))) {
    throw new AppError(400, 'VALIDATION_ERROR', 'Password lama salah.', [
      { field: 'body.currentPassword', message: 'Password lama salah.' },
    ]);
  }

  const passwordHash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);

  return prisma.$transaction(async (tx) => {
    await tx.user.update({ where: { id: user.id }, data: { passwordHash } });
    await tx.refreshToken.updateMany({
      where: { userId: user.id, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    return issueTokens(tx, user);
  });
}

module.exports = { login, refresh, logout, getProfile, changePassword, hashRefreshToken };
