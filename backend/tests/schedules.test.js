process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  semester: { findFirst: jest.fn() },
  teachingAssignment: { findUnique: jest.fn() },
  facility: { findUnique: jest.fn() },
  classMember: { findMany: jest.fn() },
  scheduleSlot: {
    findMany: jest.fn(),
    findUnique: jest.fn(),
    count: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
}));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { detectConflicts, overlaps } = require('../src/modules/schedules/schedules.conflicts');

const t = (hhmm) => new Date(`1970-01-01T${hhmm}:00.000Z`);

const tokenFor = (id, role) =>
  jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: String(id), expiresIn: 900 });

function loginAs(role) {
  prisma.user.findUnique.mockResolvedValue({ id: 1, role, status: 'ACTIVE' });
  return `Bearer ${tokenFor(1, role)}`;
}

// Baris slot dari Prisma (select di service dan di cek bentrok).
function slotRow({ id = 99, day = 'MONDAY', start = '07:00', end = '08:30', teacherId = 1, classGroupId = 10, roomId = 100 } = {}) {
  return {
    id,
    dayOfWeek: day,
    startTime: t(start),
    endTime: t(end),
    roomId,
    room: { id: roomId, name: `Ruang ${roomId}` },
    teachingAssignment: {
      id: 7,
      semesterId: 3,
      teacherId,
      classGroupId,
      teacher: { id: teacherId, user: { name: 'Bu Sari' } },
      subject: { id: 2, code: 'MAT', name: 'Matematika' },
      classGroup: { id: classGroupId, name: 'XI-2' },
    },
  };
}

const validBody = { teachingAssignmentId: 7, roomId: 100, dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '09:30' };

beforeEach(() => {
  jest.resetAllMocks();
  prisma.teachingAssignment.findUnique.mockResolvedValue({ id: 7, teacherId: 1, classGroupId: 10, semesterId: 3 });
  prisma.facility.findUnique.mockResolvedValue({ type: 'ROOM' });
  prisma.scheduleSlot.findMany.mockResolvedValue([]);
  prisma.classMember.findMany.mockResolvedValue([]);
  prisma.scheduleSlot.create.mockResolvedValue(slotRow({ id: 50, start: '08:00', end: '09:30' }));
});

describe('detectConflicts (BUSINESS_RULES 2.2)', () => {
  const base = { dayOfWeek: 'MONDAY', teacherId: 1, classGroupId: 10, roomId: 100 };
  const flat = (o) => ({
    id: 1, dayOfWeek: 'MONDAY', teacherId: 2, classGroupId: 20, roomId: 200,
    teacherName: 'Pak Budi', className: 'XI-1', roomName: 'R1', subjectName: 'Fisika', ...o,
  });
  const A = flat({ startTime: t('07:00'), endTime: t('08:30'), teacherId: 1 });

  it('contoh dokumen: B 08.00–09.30 guru sama dengan A → bentrok', () => {
    const B = { ...base, startTime: t('08:00'), endTime: t('09:30') };
    const details = detectConflicts(B, [A]);
    expect(details).toEqual([expect.objectContaining({ reason: 'TEACHER', field: 'teachingAssignmentId', slotId: 1 })]);
  });

  it('contoh dokumen: C 08.30–10.00 bersambung dengan A → tidak bentrok', () => {
    const C = { ...base, startTime: t('08:30'), endTime: t('10:00') };
    expect(overlaps(C, A)).toBe(false);
    expect(detectConflicts(C, [A])).toEqual([]);
  });

  it('hari berbeda tidak bentrok', () => {
    const B = { ...base, dayOfWeek: 'TUESDAY', startTime: t('07:00'), endTime: t('08:30') };
    expect(detectConflicts(B, [A])).toEqual([]);
  });

  it('kelas sama, ruangan sama, dan siswa sama masing-masing dilaporkan', () => {
    const cand = { ...base, teacherId: 9, startTime: t('07:30'), endTime: t('08:00') };
    const sameClassAndRoom = flat({ id: 2, startTime: t('07:00'), endTime: t('08:00'), classGroupId: 10, roomId: 100 });
    const sharedStudents = flat({ id: 3, startTime: t('07:00'), endTime: t('08:00'), classGroupId: 30 });
    const unrelated = flat({ id: 4, startTime: t('07:00'), endTime: t('08:00'), classGroupId: 40 });

    const details = detectConflicts(cand, [sameClassAndRoom, sharedStudents, unrelated], new Set([30]));

    expect(details.map((d) => [d.slotId, d.reason])).toEqual([
      [2, 'CLASS'],
      [2, 'ROOM'],
      [3, 'STUDENT'],
    ]);
    expect(details[1].field).toBe('roomId');
  });
});

describe('POST /api/v1/schedule-slots', () => {
  it('201: slot tersimpan dengan jam HH:mm', async () => {
    const res = await request(app).post('/api/v1/schedule-slots').set('Authorization', loginAs('ADMIN')).send(validBody);

    expect(res.status).toBe(201);
    expect(prisma.scheduleSlot.create.mock.calls[0][0].data).toEqual({
      teachingAssignmentId: 7, roomId: 100, dayOfWeek: 'MONDAY', startTime: t('08:00'), endTime: t('09:30'),
    });
    expect(res.body.data).toMatchObject({
      id: 50, semesterId: 3, startTime: '08:00', endTime: '09:30',
      teachingAssignment: { teacher: { id: 1, name: 'Bu Sari' }, classGroup: { name: 'XI-2' } },
    });
    // Cek bentrok hanya di semester penugasan, hari sama, jam tumpang tindih.
    expect(prisma.scheduleSlot.findMany.mock.calls[0][0].where).toEqual({
      dayOfWeek: 'MONDAY',
      startTime: { lt: t('09:30') },
      endTime: { gt: t('08:00') },
      teachingAssignment: { semesterId: 3 },
    });
  });

  it('409 SCHEDULE_CONFLICT jika guru sudah mengajar di jam itu', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([slotRow({ id: 12, classGroupId: 11, roomId: 101 })]);

    const res = await request(app).post('/api/v1/schedule-slots').set('Authorization', loginAs('ADMIN')).send(validBody);

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('SCHEDULE_CONFLICT');
    expect(res.body.error.details).toEqual([
      expect.objectContaining({ reason: 'TEACHER', slotId: 12, message: expect.stringContaining('Senin 07:00–08:30') }),
    ]);
    expect(prisma.scheduleSlot.create).not.toHaveBeenCalled();
  });

  it('409 jika ada siswa yang juga anggota kelas lain di jam itu', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([slotRow({ id: 13, teacherId: 5, classGroupId: 30, roomId: 101 })]);
    prisma.classMember.findMany.mockResolvedValue([{ classGroupId: 30 }]);

    const res = await request(app).post('/api/v1/schedule-slots').set('Authorization', loginAs('ADMIN')).send(validBody);

    expect(res.status).toBe(409);
    expect(res.body.error.details[0].reason).toBe('STUDENT');
    expect(prisma.classMember.findMany.mock.calls[0][0].where).toEqual({
      classGroupId: { in: [30] },
      student: { classMemberships: { some: { classGroupId: 10 } } },
    });
  });

  it('400 jika jam selesai tidak setelah jam mulai', async () => {
    const res = await request(app)
      .post('/api/v1/schedule-slots')
      .set('Authorization', loginAs('ADMIN'))
      .send({ ...validBody, startTime: '09:00', endTime: '09:00' });
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body.endTime');
  });

  it('400 jika format jam salah', async () => {
    const res = await request(app)
      .post('/api/v1/schedule-slots')
      .set('Authorization', loginAs('ADMIN'))
      .send({ ...validBody, startTime: '7:00' });
    expect(res.status).toBe(400);
  });

  it('400 jika fasilitas bukan ruangan', async () => {
    prisma.facility.findUnique.mockResolvedValue({ type: 'ITEM' });
    const res = await request(app).post('/api/v1/schedule-slots').set('Authorization', loginAs('ADMIN')).send(validBody);
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body.roomId');
  });

  it('400 jika penugasan tidak ada', async () => {
    prisma.teachingAssignment.findUnique.mockResolvedValue(null);
    const res = await request(app).post('/api/v1/schedule-slots').set('Authorization', loginAs('ADMIN')).send(validBody);
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body.teachingAssignmentId');
  });

  it('401 tanpa login', async () => {
    const res = await request(app).post('/api/v1/schedule-slots').send(validBody);
    expect(res.status).toBe(401);
  });

  it('403 untuk guru', async () => {
    const res = await request(app).post('/api/v1/schedule-slots').set('Authorization', loginAs('TEACHER')).send(validBody);
    expect(res.status).toBe(403);
  });
});

describe('PATCH /api/v1/schedule-slots/:id', () => {
  it('cek bentrok memakai gabungan data lama + baru dan mengecualikan slot itu sendiri', async () => {
    prisma.scheduleSlot.findUnique.mockResolvedValue({
      teachingAssignmentId: 7, roomId: 100, dayOfWeek: 'MONDAY', startTime: t('07:00'), endTime: t('08:30'),
    });
    prisma.scheduleSlot.update.mockResolvedValue(slotRow({ id: 8, day: 'TUESDAY' }));

    const res = await request(app)
      .patch('/api/v1/schedule-slots/8')
      .set('Authorization', loginAs('ADMIN'))
      .send({ dayOfWeek: 'TUESDAY' });

    expect(res.status).toBe(200);
    expect(prisma.scheduleSlot.findMany.mock.calls[0][0].where).toMatchObject({
      id: { not: 8 }, dayOfWeek: 'TUESDAY', startTime: { lt: t('08:30') },
    });
  });

  it('400 jika jam mulai baru melewati jam selesai lama', async () => {
    prisma.scheduleSlot.findUnique.mockResolvedValue({
      teachingAssignmentId: 7, roomId: 100, dayOfWeek: 'MONDAY', startTime: t('07:00'), endTime: t('08:30'),
    });
    const res = await request(app)
      .patch('/api/v1/schedule-slots/8')
      .set('Authorization', loginAs('ADMIN'))
      .send({ startTime: '09:00' });
    expect(res.status).toBe(400);
    expect(prisma.scheduleSlot.update).not.toHaveBeenCalled();
  });

  it('400 jika body kosong', async () => {
    const res = await request(app).patch('/api/v1/schedule-slots/8').set('Authorization', loginAs('ADMIN')).send({});
    expect(res.status).toBe(400);
  });

  it('404 jika slot tidak ada', async () => {
    prisma.scheduleSlot.findUnique.mockResolvedValue(null);
    const res = await request(app)
      .patch('/api/v1/schedule-slots/8')
      .set('Authorization', loginAs('ADMIN'))
      .send({ roomId: 101 });
    expect(res.status).toBe(404);
  });
});

describe('DELETE /api/v1/schedule-slots/:id', () => {
  it('204 jika belum dipakai', async () => {
    prisma.scheduleSlot.findUnique.mockResolvedValue({ _count: { sessions: 0, changes: 0 } });
    const res = await request(app).delete('/api/v1/schedule-slots/8').set('Authorization', loginAs('ADMIN'));
    expect(res.status).toBe(204);
    expect(prisma.scheduleSlot.delete).toHaveBeenCalledWith({ where: { id: 8 } });
  });

  it('409 INVALID_STATE jika sudah punya sesi mengajar', async () => {
    prisma.scheduleSlot.findUnique.mockResolvedValue({ _count: { sessions: 3, changes: 0 } });
    const res = await request(app).delete('/api/v1/schedule-slots/8').set('Authorization', loginAs('ADMIN'));
    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('INVALID_STATE');
    expect(prisma.scheduleSlot.delete).not.toHaveBeenCalled();
  });
});

describe('GET /api/v1/schedule-slots', () => {
  it('default semester aktif + paginasi', async () => {
    prisma.semester.findFirst.mockResolvedValue({ id: 3 });
    prisma.scheduleSlot.findMany.mockResolvedValue([slotRow()]);
    prisma.scheduleSlot.count.mockResolvedValue(21);

    const res = await request(app)
      .get('/api/v1/schedule-slots?classGroupId=10&limit=1')
      .set('Authorization', loginAs('ADMIN'));

    expect(res.status).toBe(200);
    expect(res.body.meta).toEqual({ page: 1, limit: 1, total: 21, totalPages: 21 });
    expect(prisma.scheduleSlot.findMany.mock.calls[0][0].where).toEqual({
      teachingAssignment: { semesterId: 3, classGroupId: 10 },
    });
    expect(res.body.data[0]).toMatchObject({ dayOfWeek: 'MONDAY', startTime: '07:00', endTime: '08:30' });
  });

  it('daftar kosong jika belum ada semester aktif', async () => {
    prisma.semester.findFirst.mockResolvedValue(null);
    const res = await request(app).get('/api/v1/schedule-slots').set('Authorization', loginAs('ADMIN'));
    expect(res.status).toBe(200);
    expect(res.body.data).toEqual([]);
    expect(prisma.scheduleSlot.findMany).not.toHaveBeenCalled();
  });

  it('403 untuk siswa', async () => {
    const res = await request(app).get('/api/v1/schedule-slots').set('Authorization', loginAs('STUDENT'));
    expect(res.status).toBe(403);
  });
});
