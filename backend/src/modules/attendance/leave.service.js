const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const attachment = require('./leave.attachment');
const { notify } = require('../notifications/notifications.service');

async function guardianOf(userId) {
  const guardian = await prisma.guardian.findUnique({
    where: { userId }, select: { id: true },
  });
  if (!guardian) throw new AppError(403, 'FORBIDDEN', 'Akun ini bukan wali murid.');
  return guardian.id;
}

async function children(userId) {
  const guardianId = await guardianOf(userId);
  const relations = await prisma.studentGuardian.findMany({
    where: { guardianId },
    select: { relationship: true, student: {
      select: { id: true, nis: true, user: { select: { name: true } } },
    } },
    orderBy: { studentId: 'asc' },
  });
  return relations.map((relation) => ({
    id: relation.student.id,
    name: relation.student.user.name,
    nis: relation.student.nis,
    relationship: relation.relationship,
  }));
}

async function assertChild(guardianId, studentId) {
  const relation = await prisma.studentGuardian.findUnique({
    where: { studentId_guardianId: { studentId, guardianId } },
    select: { studentId: true },
  });
  if (!relation) throw new AppError(404, 'NOT_FOUND', 'Anak tidak ditemukan dalam akun wali murid.');
}

async function list(userId, query) {
  const guardianId = await guardianOf(userId);
  if (query.studentId) await assertChild(guardianId, query.studentId);
  const where = { guardianId, ...(query.studentId ? { studentId: query.studentId } : {}) };
  const [total, requests] = await prisma.$transaction([
    prisma.leaveRequest.count({ where }),
    prisma.leaveRequest.findMany({
      where,
      select: {
        id: true, studentId: true, startDate: true, endDate: true,
        reason: true, rejectionReason: true, attachmentUrl: true, status: true, createdAt: true,
        student: { select: { nis: true, user: { select: { name: true } } } },
      },
      orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
      skip: (query.page - 1) * query.limit,
      take: query.limit,
    }),
  ]);
  return {
    data: requests.map((item) => ({
      id: item.id, studentId: item.studentId,
      studentName: item.student.user.name,
      nis: item.student.nis,
      startDate: item.startDate.toISOString().slice(0, 10),
      endDate: item.endDate.toISOString().slice(0, 10),
      reason: item.reason,
      rejectionReason: item.rejectionReason ?? null,
      attachmentUrl: item.attachmentUrl,
      status: item.status,
      createdAt: item.createdAt,
    })),
    meta: { page: query.page, limit: query.limit, total },
  };
}

async function create(userId, data, file = null) {
  const guardianId = await guardianOf(userId);
  await assertChild(guardianId, data.studentId);
  const stored = await attachment.saveAttachment(file);
  let record;
  try {
    record = await prisma.leaveRequest.create({
      data: {
        studentId: data.studentId,
        guardianId,
        startDate: new Date(`${data.startDate}T00:00:00.000Z`),
        endDate: new Date(`${data.endDate}T00:00:00.000Z`),
        reason: data.reason,
        status: 'PENDING',
        attachmentUrl: stored?.filename || null,
      },
      select: {
        id: true, studentId: true, startDate: true, endDate: true,
        reason: true, rejectionReason: true, status: true, createdAt: true, attachmentUrl: true,
        student: {
          select: {
            user: { select: { name: true } },
            classMemberships: {
              where: { classGroup: { academicYear: { isActive: true }, homeroomTeacherId: { not: null } } },
              select: { classGroup: { select: { homeroomTeacher: { select: { userId: true } } } } },
            },
          },
        },
      },
    });
  } catch (error) {
    await attachment.deleteAttachment(stored?.filename);
    throw error;
  }

  const homeroomUserIds = [
    ...new Set(
      (record.student?.classMemberships ?? [])
        .map((m) => m.classGroup?.homeroomTeacher?.userId)
        .filter(Boolean),
    ),
  ];
  const startDate = record.startDate.toISOString().slice(0, 10);
  const endDate = record.endDate.toISOString().slice(0, 10);
  if (homeroomUserIds.length > 0) {
    const studentName = record.student?.user?.name ?? 'Siswa';
    const dateRange = startDate === endDate ? startDate : `${startDate} s.d. ${endDate}`;
    notify(homeroomUserIds, {
      type: 'LEAVE_REQUEST',
      title: 'Pengajuan Izin Baru',
      body: `${studentName} mengajukan izin (${dateRange}): ${record.reason}`,
      referenceId: record.id,
    });
  }

  return {
    id: record.id,
    studentId: record.studentId,
    startDate,
    endDate,
    reason: record.reason,
    rejectionReason: record.rejectionReason ?? null,
    status: record.status,
    createdAt: record.createdAt,
    attachmentUrl: record.attachmentUrl,
  };
}

async function findAttachment(userId, id) {
  const guardianId = await guardianOf(userId);
  const request = await prisma.leaveRequest.findFirst({
    where: { id, guardianId }, select: { attachmentUrl: true },
  });
  if (!request || !request.attachmentUrl)
    throw new AppError(404, 'NOT_FOUND', 'Lampiran tidak ditemukan.');
  const filename = request.attachmentUrl;
  const ext = filename.split('.').pop();
  const contentType = ext === 'pdf' ? 'application/pdf' : ext === 'png' ? 'image/png' : 'image/jpeg';
  return { path: attachment.absoluteFile(filename), contentType };
}
module.exports = { children, list, create, findAttachment };
