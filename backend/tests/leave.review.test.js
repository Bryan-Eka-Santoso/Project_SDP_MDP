jest.mock('../src/lib/prisma', () => ({
  teacher: { findUnique: jest.fn() },
  leaveRequest: { count: jest.fn(), findMany: jest.fn() },
  $transaction: jest.fn(),
}));
jest.mock('../src/modules/notifications/notifications.service', () => ({ notify: jest.fn() }));
const prisma = require('../src/lib/prisma');
const { notify } = require('../src/modules/notifications/notifications.service');
const service = require('../src/modules/attendance/leave.review.service');
const { decisionBody, idParams, pendingQuery } = require('../src/modules/attendance/leave.review.validation');
const teacher = () => prisma.teacher.findUnique.mockResolvedValue({ id: 7 });
const date = (s) => new Date(`${s}T00:00:00.000Z`);
const request = (overrides = {}) => ({
  id: 42, status: 'PENDING', studentId: 10,
  startDate: date('2026-11-12'), endDate: date('2026-11-13'),
  guardian: { userId: 88 },
  student: { user: { name: 'Ani' }, classMemberships: [{ classGroupId: 5 }] }, ...overrides,
});

beforeEach(() => jest.clearAllMocks());

test('validasi menerima APPROVED dan REJECTED (wajib alasan penolakan)', () => {
  expect(decisionBody.parse({ decision: 'APPROVED' }).decision).toBe('APPROVED');
  expect(decisionBody.parse({ decision: 'REJECTED', rejectionReason: 'Surat kurang jelas' }).rejectionReason).toBe('Surat kurang jelas');
  expect(decisionBody.safeParse({ decision: 'REJECTED' }).success).toBe(false);
  expect(decisionBody.safeParse({ decision: 'REJECTED', rejectionReason: '   ' }).success).toBe(false);
  expect(decisionBody.safeParse({ decision: 'INVALID' }).success).toBe(false);
  expect(idParams.parse({ requestId: '42' }).requestId).toBe(42);
  expect(pendingQuery.parse({})).toEqual({ page: 1, limit: 20 });
});

test('user tanpa profil guru tidak dapat melihat daftar', async () => {
  prisma.teacher.findUnique.mockResolvedValue(null);
  await expect(service.pending(9, { page: 1, limit: 20 })).rejects.toMatchObject({ statusCode: 403 });
});

test('daftar pending hanya kelas wali dan tahun ajaran aktif', async () => {
  teacher();
  prisma.$transaction.mockResolvedValue([1, [{
    id: 42, studentId: 10, student: { nis: '100', user: { name: 'Ani' } },
    startDate: date('2026-11-12'), endDate: date('2026-11-13'),
    reason: 'Sakit', status: 'PENDING', attachmentUrl: null, createdAt: new Date(),
  }]]);
  const result = await service.pending(9, { page: 1, limit: 20 });
  expect(result.data[0].studentName).toBe('Ani');
  expect(prisma.leaveRequest.findMany).toHaveBeenCalledWith(expect.objectContaining({
    where: { status: 'PENDING', student: { classMemberships: { some: {
      classGroup: { homeroomTeacherId: 7, academicYear: { isActive: true } },
    } } } },
  }));
});

test('wali kelas lain tidak bisa approve', async () => {
  teacher();
  prisma.$transaction.mockImplementation(async (fn) => fn({
    leaveRequest: { findUnique: jest.fn().mockResolvedValue(request({ student: { classMemberships: [] } })) },
  }));
  await expect(service.decide(9, 42, 'APPROVED')).rejects.toMatchObject({ statusCode: 404 });
});

test('permohonan yang telah diputuskan tidak dapat diproses ulang', async () => {
  teacher();
  prisma.$transaction.mockImplementation(async (fn) => fn({
    leaveRequest: { findUnique: jest.fn().mockResolvedValue(request({ status: 'REJECTED' })) },
  }));
  await expect(service.decide(9, 42, 'APPROVED')).rejects.toMatchObject({ statusCode: 409 });
});

test('approve otomatis mengubah presensi yang sudah tersimpan menjadi EXCUSED', async () => {
  teacher();
  const updateMany = jest.fn().mockResolvedValue({ count: 1 });
  const updateAttendance = jest.fn().mockResolvedValue({ count: 3 });
  prisma.$transaction.mockImplementation(async (fn) => fn({
    leaveRequest: { findUnique: jest.fn().mockResolvedValue(request()), updateMany },
    attendance: { updateMany: updateAttendance },
  }));
  const result = await service.decide(9, 42, 'APPROVED');
  expect(result).toMatchObject({ status: 'APPROVED', verifierId: 7, attendanceUpdated: 3 });
  expect(updateAttendance).toHaveBeenCalledWith(expect.objectContaining({
    where: expect.objectContaining({ studentId: 10,
      session: expect.objectContaining({ scheduleSlot: {
        teachingAssignment: { classGroupId: { in: [5] } },
      } }),
    }),
    data: { status: 'EXCUSED' },
  }));
});

test('reject wajib menyertakan alasan penolakan, tidak mengubah presensi, dan mengirim notifikasi', async () => {
  teacher();
  const updateMany = jest.fn().mockResolvedValue({ count: 1 });
  const updateAttendance = jest.fn();
  prisma.$transaction.mockImplementation(async (fn) => fn({
    leaveRequest: { findUnique: jest.fn().mockResolvedValue(request()), updateMany },
    attendance: { updateMany: updateAttendance },
  }));
  const result = await service.decide(9, 42, 'REJECTED', 'Foto surat dokter buram');
  expect(result.attendanceUpdated).toBe(0);
  expect(result.rejectionReason).toBe('Foto surat dokter buram');
  expect(updateAttendance).not.toHaveBeenCalled();
  expect(notify).toHaveBeenCalledWith(
    [88],
    expect.objectContaining({
      type: 'LEAVE_REQUEST',
      title: 'Pengajuan Izin Ditolak',
      body: expect.stringContaining('Foto surat dokter buram'),
    }),
  );
});

test('update bersyarat yang gagal dianggap conflict', async () => {
  teacher();
  prisma.$transaction.mockImplementation(async (fn) => fn({
    leaveRequest: {
      findUnique: jest.fn().mockResolvedValue(request()),
      updateMany: jest.fn().mockResolvedValue({ count: 0 }),
    },
  }));
  await expect(service.decide(9, 42, 'APPROVED')).rejects.toMatchObject({ statusCode: 409 });
});
