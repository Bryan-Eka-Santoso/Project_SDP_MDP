process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  semester: { findFirst: jest.fn() },
  teacher: { findUnique: jest.fn() },
  facility: { findUnique: jest.fn() },
  classMember: { findMany: jest.fn() },
  scheduleSlot: { findMany: jest.fn(), findUnique: jest.fn() },
  scheduleChange: {
    findMany: jest.fn(),
    findUnique: jest.fn(),
    count: jest.fn(),
    upsert: jest.fn(),
    delete: jest.fn(),
  },
}));
jest.mock('../src/modules/notifications/notifications.service', () => ({ notify: jest.fn() }));
jest.mock('../src/modules/notifications/notifications.recipients', () => ({
  studentUserIdsOfClasses: jest.fn(),
  guardianUserIdsOfClasses: jest.fn(),
}));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { notify } = require('../src/modules/notifications/notifications.service');
const recipients = require('../src/modules/notifications/notifications.recipients');
const { applyChange } = require('../src/modules/schedules/schedules.conflicts');
const { dayOfWeekOf } = require('../src/modules/schedules/schedules.changes.service');

const t = (hhmm) => new Date(`1970-01-01T${hhmm}:00.000Z`);
const d = (ymd) => new Date(`${ymd}T00:00:00.000Z`);
// 9 November 2026 = Senin.
const MONDAY = '2026-11-09';

function loginAs(role) {
  prisma.user.findUnique.mockResolvedValue({ id: 1, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: '1', expiresIn: 900 })}`;
}

// Slot yang diubah: Matematika X-1, Senin 07:00–08:30, guru id 1 (akun 101), ruang 100.
const targetSlot = {
  id: 5,
  dayOfWeek: 'MONDAY',
  startTime: t('07:00'),
  endTime: t('08:30'),
  roomId: 100,
  room: { name: 'Ruang X-1' },
  teachingAssignment: {
    semesterId: 3,
    teacherId: 1,
    classGroupId: 10,
    teacher: { userId: 101 },
    subject: { name: 'Matematika' },
    classGroup: { name: 'X-1' },
    semester: { startDate: d('2026-07-13'), endDate: d('2026-12-18') },
  },
};

// Baris hasil select perubahan (changeSelect) setelah upsert.
function changeRow(overrides = {}) {
  return {
    id: 40,
    scheduleSlotId: 5,
    type: 'SUBSTITUTE',
    date: d(MONDAY),
    substituteTeacher: { id: 2, userId: 102, user: { name: 'Pak Budi' } },
    newRoom: null,
    newStartTime: null,
    newEndTime: null,
    description: 'Bu Sari dinas luar',
    createdAt: new Date(),
    updatedAt: new Date(),
    scheduleSlot: {
      id: 5,
      dayOfWeek: 'MONDAY',
      startTime: t('07:00'),
      endTime: t('08:30'),
      room: { id: 100, name: 'Ruang X-1' },
      teachingAssignment: {
        id: 7,
        semesterId: 3,
        teacher: { id: 1, userId: 101, user: { name: 'Bu Sari' } },
        subject: { id: 1, code: 'MAT', name: 'Matematika' },
        classGroup: { id: 10, name: 'X-1' },
      },
    },
    ...overrides,
  };
}

// Slot lain di hari yang sama, dengan perubahan di tanggal itu (opsional).
function otherSlot({ id = 9, start = '07:00', end = '08:30', teacherId = 2, classGroupId = 11, roomId = 101, change } = {}) {
  return {
    id,
    dayOfWeek: 'MONDAY',
    startTime: t(start),
    endTime: t(end),
    roomId,
    room: { name: `Ruang ${roomId}` },
    teachingAssignment: {
      teacherId,
      classGroupId,
      teacher: { user: { name: 'Pak Budi' } },
      subject: { name: 'Fisika' },
      classGroup: { name: 'X-2' },
    },
    changes: change ? [change] : [],
  };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

const substituteBody = {
  scheduleSlotId: 5,
  date: MONDAY,
  type: 'SUBSTITUTE',
  substituteTeacherId: 2,
  description: 'Bu Sari dinas luar',
};

beforeEach(() => {
  jest.resetAllMocks();
  prisma.scheduleSlot.findUnique.mockResolvedValue(targetSlot);
  prisma.scheduleSlot.findMany.mockResolvedValue([]);
  prisma.classMember.findMany.mockResolvedValue([]);
  prisma.teacher.findUnique.mockResolvedValue({ id: 2, user: { name: 'Pak Budi', status: 'ACTIVE' } });
  prisma.facility.findUnique.mockResolvedValue({ type: 'ROOM' });
  prisma.scheduleChange.findUnique.mockResolvedValue(null);
  prisma.scheduleChange.upsert.mockResolvedValue(changeRow());
  recipients.studentUserIdsOfClasses.mockResolvedValue([201, 202]);
  recipients.guardianUserIdsOfClasses.mockResolvedValue([301]);
});

describe('helper', () => {
  it('dayOfWeekOf: 9 Nov 2026 Senin, 8 Nov 2026 Minggu (bukan hari sekolah)', () => {
    expect(dayOfWeekOf(d(MONDAY))).toBe('MONDAY');
    expect(dayOfWeekOf(d('2026-11-08'))).toBeNull();
  });

  it('applyChange menerapkan perubahan slot lain di tanggal itu', () => {
    const slot = { teacherId: 1, teacherName: 'A', startTime: t('07:00'), endTime: t('08:30'), roomId: 1, roomName: 'R1' };
    expect(applyChange(slot, { type: 'CANCEL' })).toBeNull();
    expect(applyChange(slot, { type: 'SUBSTITUTE', substituteTeacherId: 9, substituteTeacher: { user: { name: 'B' } } }))
      .toMatchObject({ teacherId: 9, teacherName: 'B' });
    expect(applyChange(slot, { type: 'RESCHEDULE', newStartTime: t('10:00'), newEndTime: t('11:00'), newRoomId: null }))
      .toMatchObject({ startTime: t('10:00'), endTime: t('11:00'), roomId: 1 });
  });
});

describe('POST /api/v1/schedule-changes', () => {
  it('201: guru pengganti tersimpan dan pihak terdampak dinotifikasi', async () => {
    const res = await request(app).post('/api/v1/schedule-changes').set('Authorization', loginAs('ADMIN')).send(substituteBody);

    expect(res.status).toBe(201);
    expect(prisma.scheduleChange.upsert.mock.calls[0][0]).toMatchObject({
      where: { scheduleSlotId_date: { scheduleSlotId: 5, date: d(MONDAY) } },
      update: {
        type: 'SUBSTITUTE', substituteTeacherId: 2, newRoomId: null, newStartTime: null, newEndTime: null,
      },
    });
    expect(res.body.data).toMatchObject({ date: MONDAY, substituteTeacher: { id: 2, name: 'Pak Budi' }, slot: { startTime: '07:00' } });

    await flush();
    const [userIds, payload] = notify.mock.calls[0];
    expect(userIds).toEqual(expect.arrayContaining([201, 202, 301, 101, 102]));
    expect(payload).toMatchObject({ type: 'SCHEDULE_CHANGE', title: 'Perubahan jadwal', referenceId: 40 });
    expect(payload.body).toBe('Matematika X-1, Senin, 9 November 2026: diajar guru pengganti Pak Budi. Bu Sari dinas luar');
  });

  it('200 saat tanggal yang sama sudah punya perubahan; guru pengganti lama ikut diberi tahu', async () => {
    prisma.scheduleChange.findUnique.mockResolvedValue({ substituteTeacher: { userId: 103 } });

    const res = await request(app).post('/api/v1/schedule-changes').set('Authorization', loginAs('ADMIN')).send(substituteBody);

    expect(res.status).toBe(200);
    await flush();
    expect(notify.mock.calls[0][0]).toContain(103);
  });

  it('409 jika guru pengganti sudah mengajar di jam itu', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([otherSlot({ teacherId: 2 })]);

    const res = await request(app).post('/api/v1/schedule-changes').set('Authorization', loginAs('ADMIN')).send(substituteBody);

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('SCHEDULE_CONFLICT');
    expect(res.body.error.details[0]).toMatchObject({ reason: 'TEACHER', slotId: 9 });
    expect(prisma.scheduleSlot.findMany.mock.calls[0][0].select.changes.where).toEqual({ date: d(MONDAY) });
    expect(prisma.scheduleChange.upsert).not.toHaveBeenCalled();
    expect(notify).not.toHaveBeenCalled();
  });

  it('tidak bentrok jika slot guru pengganti di jam itu dibatalkan pada tanggal tersebut', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([otherSlot({ teacherId: 2, change: { type: 'CANCEL' } })]);
    const res = await request(app).post('/api/v1/schedule-changes').set('Authorization', loginAs('ADMIN')).send(substituteBody);
    expect(res.status).toBe(201);
  });

  it('RESCHEDULE bentrok dengan slot lain yang sudah dipindah ke ruangan yang sama di tanggal itu', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([
      otherSlot({
        start: '10:00', end: '11:30', roomId: 101,
        change: { type: 'RESCHEDULE', newStartTime: t('12:00'), newEndTime: t('13:00'), newRoomId: 102, newRoom: { name: 'Lab' } },
      }),
    ]);

    const res = await request(app)
      .post('/api/v1/schedule-changes')
      .set('Authorization', loginAs('ADMIN'))
      .send({ scheduleSlotId: 5, date: MONDAY, type: 'RESCHEDULE', newRoomId: 102, newStartTime: '12:30', newEndTime: '14:00', description: 'Praktikum' });

    expect(res.status).toBe(409);
    expect(res.body.error.details[0]).toMatchObject({ reason: 'ROOM', message: expect.stringContaining('Lab') });
  });

  it('CANCEL tidak perlu cek bentrok', async () => {
    prisma.scheduleChange.upsert.mockResolvedValue(changeRow({ type: 'CANCEL', substituteTeacher: null }));
    const res = await request(app)
      .post('/api/v1/schedule-changes')
      .set('Authorization', loginAs('ADMIN'))
      .send({ scheduleSlotId: 5, date: MONDAY, type: 'CANCEL', description: 'Rapat guru' });

    expect(res.status).toBe(201);
    expect(prisma.scheduleSlot.findMany).not.toHaveBeenCalled();
    await flush();
    expect(notify.mock.calls[0][1].body).toContain('dibatalkan');
  });

  it('400 jika tanggal bukan hari slot', async () => {
    const res = await request(app)
      .post('/api/v1/schedule-changes')
      .set('Authorization', loginAs('ADMIN'))
      .send({ ...substituteBody, date: '2026-11-10' });
    expect(res.status).toBe(400);
    expect(res.body.error.details[0]).toMatchObject({ field: 'body.date', message: expect.stringContaining('Senin') });
  });

  it('400 jika tanggal di luar semester', async () => {
    const res = await request(app)
      .post('/api/v1/schedule-changes')
      .set('Authorization', loginAs('ADMIN'))
      .send({ ...substituteBody, date: '2027-01-04' });
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].message).toContain('di luar semester');
  });

  it('400 jika guru pengganti sama dengan guru asli', async () => {
    const res = await request(app)
      .post('/api/v1/schedule-changes')
      .set('Authorization', loginAs('ADMIN'))
      .send({ ...substituteBody, substituteTeacherId: 1 });
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body.substituteTeacherId');
  });

  it('400 jika RESCHEDULE tanpa jam/ruangan baru atau hanya satu jam', async () => {
    const base = { scheduleSlotId: 5, date: MONDAY, type: 'RESCHEDULE', description: 'x' };
    const empty = await request(app).post('/api/v1/schedule-changes').set('Authorization', loginAs('ADMIN')).send(base);
    const half = await request(app)
      .post('/api/v1/schedule-changes')
      .set('Authorization', loginAs('ADMIN'))
      .send({ ...base, newStartTime: '10:00' });
    expect(empty.status).toBe(400);
    expect(half.status).toBe(400);
  });

  it('400 jika jam dan ruangan baru sama dengan aslinya', async () => {
    const res = await request(app)
      .post('/api/v1/schedule-changes')
      .set('Authorization', loginAs('ADMIN'))
      .send({ scheduleSlotId: 5, date: MONDAY, type: 'RESCHEDULE', newRoomId: 100, newStartTime: '07:00', newEndTime: '08:30', description: 'x' });
    expect(res.status).toBe(400);
  });

  it('401 tanpa login, 403 untuk guru', async () => {
    expect((await request(app).post('/api/v1/schedule-changes').send(substituteBody)).status).toBe(401);
    const res = await request(app).post('/api/v1/schedule-changes').set('Authorization', loginAs('TEACHER')).send(substituteBody);
    expect(res.status).toBe(403);
  });
});

describe('DELETE /api/v1/schedule-changes/:id', () => {
  it('204 dan memberi tahu bahwa jadwal kembali normal', async () => {
    prisma.scheduleChange.findUnique.mockResolvedValue(changeRow());

    const res = await request(app).delete('/api/v1/schedule-changes/40').set('Authorization', loginAs('ADMIN'));

    expect(res.status).toBe(204);
    expect(prisma.scheduleChange.delete).toHaveBeenCalledWith({ where: { id: 40 } });
    await flush();
    const [userIds, payload] = notify.mock.calls[0];
    expect(userIds).toEqual(expect.arrayContaining([201, 301, 101, 102]));
    expect(payload.body).toBe('Matematika X-1, Senin, 9 November 2026: kembali sesuai jadwal biasa.');
  });

  it('404 jika tidak ada', async () => {
    prisma.scheduleChange.findUnique.mockResolvedValue(null);
    const res = await request(app).delete('/api/v1/schedule-changes/40').set('Authorization', loginAs('ADMIN'));
    expect(res.status).toBe(404);
  });
});

describe('GET /api/v1/schedule-changes', () => {
  it('filter guru mencakup guru asli dan guru pengganti, default semester aktif', async () => {
    prisma.semester.findFirst.mockResolvedValue({ id: 3 });
    prisma.scheduleChange.findMany.mockResolvedValue([changeRow()]);
    prisma.scheduleChange.count.mockResolvedValue(1);

    const res = await request(app)
      .get('/api/v1/schedule-changes?teacherId=2&dateFrom=2026-11-01&dateTo=2026-11-30')
      .set('Authorization', loginAs('ADMIN'));

    expect(res.status).toBe(200);
    expect(prisma.scheduleChange.findMany.mock.calls[0][0].where).toEqual({
      AND: [
        { scheduleSlot: { teachingAssignment: { semesterId: 3 } } },
        { OR: [{ scheduleSlot: { teachingAssignment: { teacherId: 2 } } }, { substituteTeacherId: 2 }] },
        { date: { gte: d('2026-11-01') } },
        { date: { lte: d('2026-11-30') } },
      ],
    });
    expect(res.body.data[0]).toMatchObject({ id: 40, type: 'SUBSTITUTE', date: MONDAY });
  });

  it('400 jika dateTo sebelum dateFrom', async () => {
    const res = await request(app)
      .get('/api/v1/schedule-changes?dateFrom=2026-11-30&dateTo=2026-11-01')
      .set('Authorization', loginAs('ADMIN'));
    expect(res.status).toBe(400);
  });
});
