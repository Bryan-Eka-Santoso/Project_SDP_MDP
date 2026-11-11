// Cakupan data pembayaran (docs/PERMISSIONS.md: "Lihat pembayaran").
// Siswa = SELF, wali murid = CHILD, wali kelas = HOMEROOM, admin = ALL.
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

// 404, bukan 403: keberadaan siswa di luar cakupan tidak boleh bocor.
const studentNotFound = () => new AppError(404, 'NOT_FOUND', 'Data siswa tidak ditemukan.');

/** Pastikan `user` boleh melihat tagihan siswa `studentId`; melempar 404 jika di luar cakupan. */
async function assertCanViewStudentBills(user, studentId) {
  let allowed = null;

  if (user.role === 'ADMIN') {
    allowed = await prisma.student.findUnique({ where: { id: studentId }, select: { id: true } });
  } else if (user.role === 'STUDENT') {
    allowed = await prisma.student.findFirst({ where: { id: studentId, userId: user.id }, select: { id: true } });
  } else if (user.role === 'GUARDIAN') {
    allowed = await prisma.studentGuardian.findFirst({
      where: { studentId, guardian: { userId: user.id } },
      select: { studentId: true },
    });
  } else if (user.role === 'TEACHER') {
    // Wali kelas: homeroomTeacherId pada tahun ajaran aktif (bukan flag isHomeroomTeacher).
    allowed = await prisma.classMember.findFirst({
      where: { studentId, classGroup: { homeroomTeacher: { userId: user.id }, academicYear: { isActive: true } } },
      select: { studentId: true },
    });
  }

  if (!allowed) throw studentNotFound();
}

/**
 * Id kelas binaan guru pada tahun ajaran aktif. Guru yang bukan wali kelas tidak punya akses
 * ke daftar pembayaran (403).
 */
async function homeroomClassIds(user) {
  const classes = await prisma.classGroup.findMany({
    where: { homeroomTeacher: { userId: user.id }, academicYear: { isActive: true } },
    select: { id: true },
  });
  if (classes.length === 0) {
    throw new AppError(403, 'FORBIDDEN', 'Hanya wali kelas yang dapat melihat daftar pembayaran kelasnya.');
  }
  return classes.map((item) => item.id);
}

module.exports = { assertCanViewStudentBills, homeroomClassIds };
