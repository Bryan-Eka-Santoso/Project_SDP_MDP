const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { absoluteFile } = require('./leave.attachment');
const { notify } = require('../notifications/notifications.service');

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
        reason: true, rejectionReason: true, attachmentUrl: true, status: true, createdAt: true,
        student: { select: { nis: true, user: { select: { name: true } } } },
      },
      // Urutkan dari tanggal mulai izin paling dekat (tenggat paling mepet).
      orderBy: [{ startDate: 'asc' }, { createdAt: 'asc' }, { id: 'asc' }],
      skip: (query.page - 1) * query.limit, take: query.limit,
    }),
  ]);
  return {
    data: requests.map((r) => ({
      id: r.id, studentId: r.studentId, studentName: r.student.user.name,
      nis: r.student.nis, startDate: dateText(r.startDate),
      endDate: dateText(r.endDate), reason: r.reason,
      rejectionReason: r.rejectionReason ?? null,
      attachmentUrl: r.attachmentUrl, status: r.status, createdAt: r.createdAt,
    })),
    meta: { total, page: query.page, limit: query.limit },
  };
}

async function decide(userId, requestId, decision, rejectionReason = null) {
  const teacherId = await homeroomTeacherId(userId);
  const cleanedReason = decision === 'REJECTED' ? (rejectionReason?.trim() || null) : null;
  if (decision === 'REJECTED' && !cleanedReason) {
    throw new AppError(400, 'VALIDATION_ERROR', 'Alasan penolakan wajib diisi saat menolak pengajuan izin.', [
      { field: 'body.rejectionReason', message: 'Alasan penolakan wajib diisi.' },
    ]);
  }

  const outcome = await prisma.$transaction(async (tx) => {
    const request = await tx.leaveRequest.findUnique({
      where: { id: requestId },
      select: {
        id: true, status: true, studentId: true, startDate: true, endDate: true,
        guardian: { select: { userId: true } },
        student: {
          select: {
            user: { select: { name: true } },
            classMemberships: {
              where: { classGroup: { homeroomTeacherId: teacherId, academicYear: { isActive: true } } },
              select: { classGroupId: true },
            },
          },
        },
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
      data: { status: decision, verifierId: teacherId, rejectionReason: cleanedReason },
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
    return {
      result: {
        id: requestId,
        status: decision,
        verifierId: teacherId,
        rejectionReason: cleanedReason,
        attendanceUpdated,
      },
      guardianUserId: request.guardian?.userId ?? null,
      studentName: request.student?.user?.name ?? 'Anak Anda',
      startDate: dateText(request.startDate),
      endDate: dateText(request.endDate),
    };
  });

  if (outcome.guardianUserId) {
    const dateRange =
      outcome.startDate === outcome.endDate
        ? outcome.startDate
        : `${outcome.startDate} s.d. ${outcome.endDate}`;
    notify([outcome.guardianUserId], {
      type: 'LEAVE_REQUEST',
      title: decision === 'APPROVED' ? 'Pengajuan Izin Disetujui' : 'Pengajuan Izin Ditolak',
      body:
        decision === 'APPROVED'
          ? `Pengajuan izin ${outcome.studentName} (${dateRange}) telah disetujui wali kelas.`
          : `Pengajuan izin ${outcome.studentName} (${dateRange}) ditolak. Alasan: ${cleanedReason}`,
      referenceId: requestId,
    });
  }

  return outcome.result;
}

// Unduh hanya jika permohonan milik siswa dari kelas aktif yang diampu wali kelas ini.
async function findAttachmentForHomeroom(userId, requestId) {
  const teacherId = await homeroomTeacherId(userId);
  const request = await prisma.leaveRequest.findFirst({
    where: { id: requestId, student: homeroomScope(teacherId) },
    select: { attachmentUrl: true },
  });
  if (!request || !request.attachmentUrl)
    throw new AppError(404, 'NOT_FOUND', 'Lampiran tidak ditemukan untuk kelas Anda.');
  const filename = request.attachmentUrl;
  // Hanya file privat yang dihasilkan backend; jangan mengikuti URL/path arbitrer.
  const filePath = absoluteFile(filename);
  const ext = filename.split('.').pop();
  const mime = ext === 'pdf' ? 'application/pdf' : ext === 'png' ? 'image/png' : 'image/jpeg';
  return { path: filePath, mime };
}

module.exports = { pending, decide, findAttachmentForHomeroom };
