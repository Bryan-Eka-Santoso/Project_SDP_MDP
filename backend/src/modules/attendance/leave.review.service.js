const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

function dateText(value) { return value.toISOString().slice(0, 10); }

async function homeroomTeacherId(userId) {
  const teacher = await prisma.teacher.findUnique({
    where: { userId }, select: { id: true },
  });
  if (!teacher) throw new AppError(403, 'FORBIDDEN', 'Akun bukan guru.');
  return teacher.id;
}

// Filter siswa hanya dari kelas yang wali kelasnya adalah guru login dan tahun ajaran aktif.
function homeroomScope(teacherId) {
  return { classMemberships: { some: { classGroup: {
    homeroomTeacherId: teacherId, academicYear: { isActive: true },
  } } } };
}

async function pending(userId, query) {
  const teacherId = await homeroomTeacherId(userId);
  const where = { status: 'PENDING', student: homeroomScope(teacherId) };
  const [total, requests] = await prisma.$transaction([
    prisma.leaveRequest.count({ where }),
    prisma.leaveRequest.findMany({
      where,
      select: {
        id: true, studentId: true, startDate: true, endDate: true,
        reason: true, attachmentUrl: true, status: true, createdAt: true,
        student: { select: { nis: true, user: { select: { name: true } } } },
      },
      orderBy: [{ createdAt: 'asc' }, { id: 'asc' }],
      skip: (query.page - 1) * query.limit, take: query.limit,
    }),
  ]);
  return {
    data: requests.map((r) => ({
      id: r.id, studentId: r.studentId, studentName: r.student.user.name,
      nis: r.student.nis, startDate: dateText(r.startDate),
      endDate: dateText(r.endDate), reason: r.reason,
      attachmentUrl: r.attachmentUrl, status: r.status, createdAt: r.createdAt,
    })),
    meta: { total, page: query.page, limit: query.limit },
  };
}

async function decide(userId, requestId, decision) {
  const teacherId = await homeroomTeacherId(userId);
  return prisma.$transaction(async (tx) => {
    const request = await tx.leaveRequest.findUnique({
      where: { id: requestId },
      select: {
        id: true, status: true, studentId: true, startDate: true, endDate: true,
        student: { select: { classMemberships: {
          where: { classGroup: { homeroomTeacherId: teacherId, academicYear: { isActive: true } } },
          select: { classGroupId: true },
        } } },
      },
    });
    if (!request || request.student.classMemberships.length === 0) {
      throw new AppError(404, 'NOT_FOUND', 'Pengajuan izin tidak ditemukan untuk kelas Anda.');
    }
    if (request.status !== 'PENDING') {
      throw new AppError(409, 'CONFLICT', 'Pengajuan izin sudah diproses.');
    }
    // Conditional update prevents two reviewers from deciding a PENDING request twice.
    const updated = await tx.leaveRequest.updateMany({
      where: { id: requestId, status: 'PENDING' },
      data: { status: decision, verifierId: teacherId },
    });
    if (updated.count !== 1) {
      throw new AppError(409, 'CONFLICT', 'Pengajuan izin sudah diproses.');
    }
    let attendanceUpdated = 0;
    if (decision === 'APPROVED') {
      const classIds = request.student.classMemberships.map((m) => m.classGroupId);
      // Sesi yang belum dibuat tetap akan ditangani oleh attendance.saveSession().
      // Ubah presensi yang sudah tersimpan agar persetujuan berlaku segera.
      const attendance = await tx.attendance.updateMany({
        where: {
          studentId: request.studentId,
          session: {
            date: { gte: request.startDate, lte: request.endDate },
            scheduleSlot: { teachingAssignment: { classGroupId: { in: classIds } } },
          },
        },
        data: { status: 'EXCUSED' },
      });
      attendanceUpdated = attendance.count;
    }
    return { id: requestId, status: decision, verifierId: teacherId, attendanceUpdated };
  });
}

module.exports = { pending, decide };
