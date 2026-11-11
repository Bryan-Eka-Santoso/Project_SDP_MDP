process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  student: { findFirst: jest.fn() },
  studentGuardian: { findFirst: jest.fn() },
  classGroup: { findFirst: jest.fn() },
  scheduleSlot: { findMany: jest.fn() },
}));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { parseDateOnly, todayLocal } = require('../src/utils/date');

const d = (ymd) => new Date(`${ymd}T00:00:00.000Z`);
const t = (hm) => new Date(`1970-01-01T${hm}:00.000Z`);

function loginAs(role, id = 1) {
  prisma.user.findUnique.mockResolvedValue({ id, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: String(id), expiresIn: 900 })}`;
}

const semester = { startDate: d('2026-07-13'), endDate: d('2026-12-19') };

// 2026-11-16 dan 2026-11-23 adalah Senin; 2026-11-21 Sabtu.
function slot(overrides = {}) {
  return {
    id: 1,
    dayOfWeek: 'MONDAY',
    startTime: t('07:00'),
    endTime: t('08:30'),
    room: { id: 1, name: 'Ruang X-1' },
    teachingAssignment: {
      id: 10,
      semesterId: 1,
      teacher: { id: 5, userId: 40, user: { name: 'Yohanes' } },
      subject: { id: 2, code: 'MAT', name: 'Matematika' },
      classGroup: { id: 7, name: 'X-1' },
      semester,
    },
    changes: [],
    ...overrides,
  };
}

const change = (overrides) => ({
  id: 90,
  type: 'CANCEL',
  date: d('2026-11-16'),
  description: 'Rapat guru',
  substituteTeacher: null,
  newRoom: null,
  newStartTime: null,
  newEndTime: null,
  ...overrides,
});

const get = (url, auth) => request(app).get(url).set('Authorization', auth);
const query = (call = 0) => prisma.scheduleSlot.findMany.mock.calls[call][0];

beforeEach(() => {
  jest.resetAllMocks();
  prisma.scheduleSlot.findMany.mockResolvedValue([slot()]);
});

describe('GET /api/v1/schedule: siswa', () => {
  it('memuat jadwal kelasnya dan memperluas slot mingguan menjadi tanggal', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([
      slot(),
      slot({ id: 2, dayOfWeek: 'SATURDAY', startTime: t('09:00'), endTime: t('10:30') }),
    ]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-22', loginAs('STUDENT', 50));

    expect(res.status).toBe(200);
    expect(res.body.data).toEqual([
      {
        date: '2026-11-16',
        slotId: 1,
        dayOfWeek: 'MONDAY',
        startTime: '07:00',
        endTime: '08:30',
        room: { id: 1, name: 'Ruang X-1' },
        teacher: { id: 5, name: 'Yohanes' },
        subject: { id: 2, code: 'MAT', name: 'Matematika' },
        classGroup: { id: 7, name: 'X-1' },
        change: null,
      },
      expect.objectContaining({ date: '2026-11-21', slotId: 2, dayOfWeek: 'SATURDAY', startTime: '09:00' }),
    ]);
    expect(query().where.teachingAssignment.classGroup).toEqual({
      members: { some: { student: { userId: 50 } } },
    });
  });

  it('rentang dua minggu menghasilkan dua entri Senin; perubahan hanya menempel di tanggalnya', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([slot({ changes: [change()] })]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-23', loginAs('STUDENT'));

    expect(res.body.data.map((e) => [e.date, e.change?.type ?? null])).toEqual([
      ['2026-11-16', 'CANCEL'],
      ['2026-11-23', null],
    ]);
    expect(res.body.data[0].change).toEqual({ id: 90, type: 'CANCEL', description: 'Rapat guru' });
  });

  it('RESCHEDULE: jam dan ruangan efektif memakai nilai baru, aslinya di `original`', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([
      slot({
        changes: [
          change({
            type: 'RESCHEDULE',
            newRoom: { id: 3, name: 'Aula' },
            newStartTime: t('10:00'),
            newEndTime: t('11:30'),
          }),
        ],
      }),
    ]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-16', loginAs('STUDENT'));

    expect(res.body.data[0]).toMatchObject({
      startTime: '10:00',
      endTime: '11:30',
      room: { id: 3, name: 'Aula' },
      change: { type: 'RESCHEDULE' },
      original: { startTime: '07:00', endTime: '08:30', room: { id: 1, name: 'Ruang X-1' } },
    });
  });

  it('RESCHEDULE yang hanya pindah ruangan mempertahankan jam asli', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([
      slot({ changes: [change({ type: 'RESCHEDULE', newRoom: { id: 3, name: 'Aula' } })] }),
    ]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-16', loginAs('STUDENT'));

    expect(res.body.data[0]).toMatchObject({ startTime: '07:00', endTime: '08:30', room: { name: 'Aula' } });
  });

  it('SUBSTITUTE: guru yang tampil adalah guru pengganti', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([
      slot({
        changes: [
          change({ type: 'SUBSTITUTE', substituteTeacher: { id: 6, userId: 41, user: { name: 'Maria' } } }),
        ],
      }),
    ]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-16', loginAs('STUDENT'));

    expect(res.body.data[0].teacher).toEqual({ id: 6, name: 'Maria' });
    expect(res.body.data[0].original.teacher).toEqual({ id: 5, name: 'Yohanes' });
  });

  it('tanggal di luar semester slot tidak ditampilkan', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([
      slot({
        teachingAssignment: { ...slot().teachingAssignment, semester: { ...semester, endDate: d('2026-11-18') } },
      }),
    ]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-23', loginAs('STUDENT'));

    expect(res.body.data.map((e) => e.date)).toEqual(['2026-11-16']);
  });

  it('urut tanggal lalu jam mulai', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([
      slot({ id: 3, startTime: t('10:30'), endTime: t('12:00') }),
      slot({ id: 1 }),
      slot({ id: 2, startTime: t('08:45'), endTime: t('10:15') }),
    ]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-16', loginAs('STUDENT'));

    expect(res.body.data.map((e) => e.slotId)).toEqual([1, 2, 3]);
  });

  it('tanpa tanggal: tujuh hari mulai hari ini (WIB)', async () => {
    await get('/api/v1/schedule', loginAs('STUDENT'));

    const today = parseDateOnly(todayLocal());
    const { semester: range } = query().where.teachingAssignment;
    expect(range.endDate.gte).toEqual(today);
    expect(range.startDate.lte).toEqual(new Date(today.getTime() + 6 * 24 * 60 * 60 * 1000));
  });

  it('studentId milik sendiri boleh, milik siswa lain 404', async () => {
    prisma.student.findFirst.mockResolvedValue({ id: 9 });
    expect((await get('/api/v1/schedule?studentId=9', loginAs('STUDENT', 50))).status).toBe(200);
    expect(prisma.student.findFirst).toHaveBeenCalledWith({ where: { id: 9, userId: 50 }, select: { id: true } });

    prisma.student.findFirst.mockResolvedValue(null);
    expect((await get('/api/v1/schedule?studentId=8', loginAs('STUDENT', 50))).status).toBe(404);
  });

  it('classGroupId ditolak untuk siswa (400)', async () => {
    expect((await get('/api/v1/schedule?classGroupId=7', loginAs('STUDENT'))).status).toBe(400);
  });
});

describe('GET /api/v1/schedule: wali murid', () => {
  it('wajib memilih anak (400)', async () => {
    const res = await get('/api/v1/schedule', loginAs('GUARDIAN', 60));
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('query.studentId');
    expect(prisma.scheduleSlot.findMany).not.toHaveBeenCalled();
  });

  it('anak sendiri: jadwal kelas anak; anak orang lain: 404', async () => {
    prisma.studentGuardian.findFirst.mockResolvedValue({ studentId: 9 });
    const ok = await get('/api/v1/schedule?studentId=9', loginAs('GUARDIAN', 60));
    expect(ok.status).toBe(200);
    expect(prisma.studentGuardian.findFirst).toHaveBeenCalledWith({
      where: { studentId: 9, guardian: { userId: 60 } },
      select: { studentId: true },
    });
    expect(query().where.teachingAssignment.classGroup).toEqual({ members: { some: { studentId: 9 } } });

    prisma.studentGuardian.findFirst.mockResolvedValue(null);
    expect((await get('/api/v1/schedule?studentId=10', loginAs('GUARDIAN', 60))).status).toBe(404);
  });
});

describe('GET /api/v1/schedule: guru', () => {
  it('jadwal mengajar sendiri, ditambah slot guru lain yang ia gantikan pada tanggal itu', async () => {
    const own = slot();
    const covered = slot({
      id: 2,
      teachingAssignment: {
        ...slot().teachingAssignment,
        teacher: { id: 6, userId: 41, user: { name: 'Maria' } },
      },
      changes: [
        change({ type: 'SUBSTITUTE', substituteTeacher: { id: 5, userId: 40, user: { name: 'Yohanes' } } }),
      ],
    });
    const other = slot({
      id: 3,
      teachingAssignment: {
        ...slot().teachingAssignment,
        teacher: { id: 6, userId: 41, user: { name: 'Maria' } },
      },
    });
    prisma.scheduleSlot.findMany.mockResolvedValue([own, covered, other]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-23', loginAs('TEACHER', 40));

    // Slot 2 hanya muncul 16 Nov (saat ia mengganti); slot 3 milik guru lain tidak pernah.
    expect(res.body.data.map((e) => [e.date, e.slotId])).toEqual([
      ['2026-11-16', 1],
      ['2026-11-16', 2],
      ['2026-11-23', 1],
    ]);
    expect(query().where.OR).toEqual([
      { teachingAssignment: { teacher: { userId: 40 } } },
      {
        changes: {
          some: {
            date: { gte: d('2026-11-16'), lte: d('2026-11-23') },
            substituteTeacher: { userId: 40 },
          },
        },
      },
    ]);
  });

  it('slot miliknya yang digantikan orang lain tetap tampil dengan guru pengganti', async () => {
    prisma.scheduleSlot.findMany.mockResolvedValue([
      slot({
        changes: [
          change({ type: 'SUBSTITUTE', substituteTeacher: { id: 6, userId: 41, user: { name: 'Maria' } } }),
        ],
      }),
    ]);

    const res = await get('/api/v1/schedule?dateFrom=2026-11-16&dateTo=2026-11-16', loginAs('TEACHER', 40));

    expect(res.body.data[0]).toMatchObject({ teacher: { name: 'Maria' }, change: { type: 'SUBSTITUTE' } });
  });

  it('wali kelas: classGroupId harus kelas binaan tahun ajaran aktif', async () => {
    prisma.classGroup.findFirst.mockResolvedValue({ id: 7 });
    const ok = await get('/api/v1/schedule?classGroupId=7', loginAs('TEACHER', 40));
    expect(ok.status).toBe(200);
    expect(prisma.classGroup.findFirst).toHaveBeenCalledWith({
      where: { id: 7, homeroomTeacher: { userId: 40 }, academicYear: { isActive: true } },
      select: { id: true },
    });
    expect(query().where.teachingAssignment.classGroup).toEqual({ id: 7 });
    expect(query().where.OR).toBeUndefined();

    prisma.classGroup.findFirst.mockResolvedValue(null);
    expect((await get('/api/v1/schedule?classGroupId=8', loginAs('TEACHER', 40))).status).toBe(404);
  });

  it('studentId ditolak untuk guru (400)', async () => {
    expect((await get('/api/v1/schedule?studentId=9', loginAs('TEACHER'))).status).toBe(400);
  });
});

describe('GET /api/v1/schedule: akses dan validasi', () => {
  it('401 tanpa login; 403 untuk admin (memakai master jadwal)', async () => {
    expect((await request(app).get('/api/v1/schedule')).status).toBe(401);
    expect((await get('/api/v1/schedule', loginAs('ADMIN'))).status).toBe(403);
    expect(prisma.scheduleSlot.findMany).not.toHaveBeenCalled();
  });

  it('400 untuk tanggal tidak valid, urutan terbalik, tanpa dateFrom, atau lebih dari 31 hari', async () => {
    const auth = loginAs('STUDENT');
    expect((await get('/api/v1/schedule?dateFrom=2026-02-30', auth)).status).toBe(400);
    expect((await get('/api/v1/schedule?dateFrom=2026-11-20&dateTo=2026-11-10', auth)).status).toBe(400);
    expect((await get('/api/v1/schedule?dateTo=2026-11-10', auth)).status).toBe(400);
    expect((await get('/api/v1/schedule?dateFrom=2026-11-01&dateTo=2026-12-02', auth)).status).toBe(400);
    expect((await get('/api/v1/schedule?dateFrom=2026-11-01&dateTo=2026-12-01', auth)).status).toBe(200);
  });
});
