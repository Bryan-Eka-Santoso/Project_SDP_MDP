// Helper mencari userId penerima notifikasi. Penerima dihitung saat dikirim (BUSINESS_RULES 2.4).
const prisma = require('../../lib/prisma');

const unique = (ids) => [...new Set(ids)];

// Akun siswa anggota kelas-kelas tersebut.
async function studentUserIdsOfClasses(classGroupIds) {
  const rows = await prisma.classMember.findMany({
    where: { classGroupId: { in: classGroupIds } },
    select: { student: { select: { userId: true } } },
  });
  return unique(rows.map((row) => row.student.userId));
}

// Akun wali murid dari siswa-siswa tersebut.
async function guardianUserIdsOfStudents(studentIds) {
  const rows = await prisma.studentGuardian.findMany({
    where: { studentId: { in: studentIds } },
    select: { guardian: { select: { userId: true } } },
  });
  return unique(rows.map((row) => row.guardian.userId));
}

// Akun wali murid dari semua siswa di kelas-kelas tersebut.
async function guardianUserIdsOfClasses(classGroupIds) {
  const rows = await prisma.studentGuardian.findMany({
    where: { student: { classMemberships: { some: { classGroupId: { in: classGroupIds } } } } },
    select: { guardian: { select: { userId: true } } },
  });
  return unique(rows.map((row) => row.guardian.userId));
}

// Semua akun aktif dengan role tertentu, mis. pengumuman sekolah.
async function activeUserIdsByRoles(roles) {
  const rows = await prisma.user.findMany({
    where: { role: { in: roles }, status: 'ACTIVE' },
    select: { id: true },
  });
  return rows.map((row) => row.id);
}

module.exports = {
  studentUserIdsOfClasses,
  guardianUserIdsOfStudents,
  guardianUserIdsOfClasses,
  activeUserIdsByRoles,
};
