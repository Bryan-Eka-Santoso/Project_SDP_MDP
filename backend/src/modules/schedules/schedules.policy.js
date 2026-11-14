// Cakupan data jadwal (docs/PERMISSIONS.md: "Lihat jadwal").
// Siswa = SELF, wali murid = CHILD, guru = jadwal mengajarnya, wali kelas = HOMEROOM.
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

// 404, bukan 403: keberadaan siswa atau kelas di luar cakupan tidak boleh bocor.
const notFound = (message) => new AppError(404, 'NOT_FOUND', message);

function invalidQuery(field, message) {
  return new AppError(400, 'VALIDATION_ERROR', 'Data yang dikirim tidak valid.', [
    { field: `query.${field}`, message },
  ]);
}

/**
 * Tentukan batas data jadwal untuk `user`.
 * Hasil: `{ classWhere }` (filter pada TeachingAssignment, untuk jadwal berbasis kelas) atau
 * `{ teacherUserId }` (jadwal mengajar guru, termasuk yang ia gantikan pada tanggal tertentu).
 */
async function resolveViewScope(user, { studentId, classGroupId }) {
  if (user.role === 'STUDENT') {
    if (classGroupId) throw invalidQuery('classGroupId', 'classGroupId hanya untuk wali kelas.');
    if (studentId) {
      const self = await prisma.student.findFirst({
        where: { id: studentId, userId: user.id },
        select: { id: true },
      });
      if (!self) throw notFound('Data siswa tidak ditemukan.');
    }
    return { classWhere: { members: { some: { student: { userId: user.id } } } } };
  }

  if (user.role === 'GUARDIAN') {
    if (classGroupId) throw invalidQuery('classGroupId', 'classGroupId hanya untuk wali kelas.');
    if (!studentId) throw invalidQuery('studentId', 'Pilih anak yang jadwalnya ingin dilihat.');
    const child = await prisma.studentGuardian.findFirst({
      where: { studentId, guardian: { userId: user.id } },
      select: { studentId: true },
    });
    if (!child) throw notFound('Data siswa tidak ditemukan.');
    return { classWhere: { members: { some: { studentId } } } };
  }

  if (user.role === 'TEACHER') {
    if (studentId) throw invalidQuery('studentId', 'studentId hanya untuk wali murid dan siswa.');
    if (!classGroupId) return { teacherUserId: user.id };
    // Wali kelas: homeroomTeacherId pada tahun ajaran aktif (bukan flag isHomeroomTeacher).
    const homeroom = await prisma.classGroup.findFirst({
      where: { id: classGroupId, homeroomTeacher: { userId: user.id }, academicYear: { isActive: true } },
      select: { id: true },
    });
    if (!homeroom) throw notFound('Kelas tidak ditemukan.');
    return { classWhere: { id: classGroupId } };
  }

  throw new AppError(403, 'FORBIDDEN', 'Anda tidak memiliki akses ke fitur ini.');
}

module.exports = { resolveViewScope };
