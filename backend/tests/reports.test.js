process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  semester: { findFirst: jest.fn() },
  teacher: { findMany: jest.fn() },
  scheduleSlot: { findMany: jest.fn() },
  scheduleChange: { findMany: jest.fn(), count: jest.fn() },
}));

const ExcelJS = require('exceljs');
const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { parseDateOnly, todayLocal } = require('../src/utils/date');

const d = (ymd) => new Date(`${ymd}T00:00:00.000Z`);
const t = (hm) => new Date(`1970-01-01T${hm}:00.000Z`);

function loginAs(role) {
  prisma.user.findUnique.mockResolvedValue({ id: 1, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: '1', expiresIn: 900 })}`;
}

const get = (url, auth = loginAs('ADMIN')) => request(app).get(url).set('Authorization', auth);

// Ambil respons biner (xlsx) apa adanya.
const getBinary = (url) =>
  get(url)
    .buffer(true)
    .parse((res, callback) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => callback(null, Buffer.concat(chunks)));
    });

async function readWorkbook(buffer) {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(buffer);
  return workbook.worksheets[0];
}

const teacher = (id, name) => ({ id, user: { name } });
const slotInfo = {
  dayOfWeek: 'MONDAY',
  startTime: t('07:00'),
  endTime: t('08:30'),
  room: { id: 1, name: 'Ruang X-1' },
  teachingAssignment: {
    teacher: teacher(5, 'Yohanes'),
    subject: { id: 2, code: 'MAT', name: 'Matematika' },
    classGroup: { id: 7, name: 'X-1' },
  },
};

const substitute = {
  id: 90,
  type: 'SUBSTITUTE',
  date: d('2026-11-16'),
  description: 'Guru sakit',
  substituteTeacher: teacher(6, 'Maria'),
  newRoom: null,
  newStartTime: null,
  newEndTime: null,
  scheduleSlot: slotInfo,
};

const reschedule = {
  ...substitute,
  id: 91,
  type: 'RESCHEDULE',
  date: d('2026-11-23'),
  description: '=1+1',
  substituteTeacher: null,
  newRoom: { id: 3, name: 'Aula' },
  newStartTime: t('10:00'),
  newEndTime: t('11:30'),
};

beforeEach(() => {
  jest.resetAllMocks();
  prisma.semester.findFirst.mockResolvedValue({ id: 1 });
  prisma.scheduleChange.findMany.mockResolvedValue([substitute, reschedule]);
  prisma.scheduleChange.count.mockResolvedValue(2);
});

describe('GET /api/v1/reports/schedule-changes (F-LAP-07)', () => {
  it('baris laporan memakai semester aktif, urut tanggal', async () => {
    const res = await get('/api/v1/reports/schedule-changes');

    expect(res.status).toBe(200);
    expect(res.body.meta).toEqual({ page: 1, limit: 20, total: 2, totalPages: 1 });
    expect(res.body.data[0]).toEqual({
      id: 90,
      date: '2026-11-16',
      dayOfWeek: 'MONDAY',
      classGroup: { id: 7, name: 'X-1' },
      subject: { id: 2, code: 'MAT', name: 'Matematika' },
      originalTeacher: { id: 5, name: 'Yohanes' },
      type: 'SUBSTITUTE',
      substituteTeacher: { id: 6, name: 'Maria' },
      originalRoom: { id: 1, name: 'Ruang X-1' },
      originalStartTime: '07:00',
      originalEndTime: '08:30',
      newRoom: null,
      newStartTime: null,
      newEndTime: null,
      description: 'Guru sakit',
    });
    expect(res.body.data[1]).toMatchObject({ type: 'RESCHEDULE', newStartTime: '10:00', newEndTime: '11:30' });

    const call = prisma.scheduleChange.findMany.mock.calls[0][0];
    expect(call.where.AND).toEqual([{ scheduleSlot: { teachingAssignment: { semesterId: 1 } } }]);
    expect(call.orderBy).toEqual([{ date: 'asc' }, { id: 'asc' }]);
    expect(call).toMatchObject({ skip: 0, take: 20 });
  });

  it('filter dan paginasi masuk ke query', async () => {
    await get(
      '/api/v1/reports/schedule-changes?semesterId=3&classGroupId=7&teacherId=5&type=CANCEL&dateFrom=2026-11-01&dateTo=2026-11-30&page=2&limit=10',
    );

    expect(prisma.semester.findFirst).not.toHaveBeenCalled();
    const call = prisma.scheduleChange.findMany.mock.calls[0][0];
    expect(call.where.AND).toEqual([
      { scheduleSlot: { teachingAssignment: { semesterId: 3 } } },
      { scheduleSlot: { teachingAssignment: { classGroupId: 7 } } },
      { OR: [{ scheduleSlot: { teachingAssignment: { teacherId: 5 } } }, { substituteTeacherId: 5 }] },
      { type: 'CANCEL' },
      { date: { gte: parseDateOnly('2026-11-01') } },
      { date: { lte: parseDateOnly('2026-11-30') } },
    ]);
    expect(call).toMatchObject({ skip: 10, take: 10 });
  });

  it('tanpa semester aktif: kosong', async () => {
    prisma.semester.findFirst.mockResolvedValue(null);
    const res = await get('/api/v1/reports/schedule-changes');
    expect(res.body.data).toEqual([]);
    expect(prisma.scheduleChange.findMany).not.toHaveBeenCalled();
  });
});

describe('GET /api/v1/reports/teaching-load (F-LAP-09)', () => {
  const teachers = [
    { id: 6, employeeNumber: '198203152006042002', user: { name: 'Maria' } },
    { id: 5, employeeNumber: '198001012005011001', user: { name: 'Yohanes' } },
  ];
  const slot = (dayOfWeek, start, end, classGroupId, subject) => ({
    dayOfWeek,
    startTime: t(start),
    endTime: t(end),
    teachingAssignment: { teacherId: 5, classGroupId, subject },
  });
  const mat = { id: 2, code: 'MAT', name: 'Matematika' };
  const fis = { id: 4, code: 'FIS', name: 'Fisika' };

  beforeEach(() => {
    prisma.teacher.findMany.mockResolvedValue(teachers);
    prisma.scheduleSlot.findMany.mockResolvedValue([
      slot('MONDAY', '07:00', '08:30', 7, mat),
      slot('MONDAY', '08:45', '10:15', 8, mat),
      slot('SATURDAY', '09:00', '10:00', 7, fis),
    ]);
  });

  it('jumlah jam, pertemuan, kelas, mapel, dan sebaran per hari; guru tanpa jadwal tetap tampil', async () => {
    const res = await get('/api/v1/reports/teaching-load');

    expect(res.status).toBe(200);
    expect(res.body.meta.total).toBe(2);
    // Urut nama: Maria lalu Yohanes.
    expect(res.body.data.map((r) => r.teacher.name)).toEqual(['Maria', 'Yohanes']);
    expect(res.body.data[0]).toMatchObject({ sessionsPerWeek: 0, totalMinutes: 0, totalHours: 0, classCount: 0, subjects: [] });

    const yohanes = res.body.data[1];
    expect(yohanes).toMatchObject({
      teacher: { id: 5, name: 'Yohanes', employeeNumber: '198001012005011001' },
      sessionsPerWeek: 3,
      totalMinutes: 240,
      totalHours: 4,
      classCount: 2,
      subjects: [mat, fis],
    });
    expect(yohanes.perDay.MONDAY).toEqual({ sessions: 2, minutes: 180 });
    expect(yohanes.perDay.SATURDAY).toEqual({ sessions: 1, minutes: 60 });
    expect(yohanes.perDay.TUESDAY).toEqual({ sessions: 0, minutes: 0 });
    expect(prisma.scheduleSlot.findMany.mock.calls[0][0].where).toEqual({ teachingAssignment: { semesterId: 1 } });
  });

  it('teacherId membatasi guru dan slot; paginasi memotong daftar', async () => {
    const res = await get('/api/v1/reports/teaching-load?teacherId=5&page=2&limit=1');

    expect(prisma.teacher.findMany.mock.calls[0][0].where).toEqual({ id: 5 });
    expect(prisma.scheduleSlot.findMany.mock.calls[0][0].where).toEqual({
      teachingAssignment: { semesterId: 1, teacherId: 5 },
    });
    expect(res.body.meta).toMatchObject({ page: 2, limit: 1, total: 2, totalPages: 2 });
    expect(res.body.data.map((r) => r.teacher.name)).toEqual(['Yohanes']);
  });
});

describe('GET /api/v1/reports/:type/export (F-LAP-11)', () => {
  it('berkas Excel berisi header dan data yang sama dengan tampilan', async () => {
    const res = await getBinary('/api/v1/reports/schedule-changes/export?classGroupId=7');

    expect(res.status).toBe(200);
    expect(res.headers['content-type']).toContain('spreadsheetml.sheet');
    expect(res.headers['content-disposition']).toBe(
      `attachment; filename="laporan-schedule-changes-${todayLocal()}.xlsx"`,
    );

    const sheet = await readWorkbook(res.body);
    expect(sheet.name).toBe('Perubahan Jadwal');
    expect(sheet.getRow(1).values.slice(1)).toEqual([
      'Tanggal', 'Hari', 'Kelas', 'Mata Pelajaran', 'Guru Asli', 'Jenis Perubahan', 'Guru Pengganti',
      'Jam Asli', 'Jam Baru', 'Ruangan Asli', 'Ruangan Baru', 'Keterangan',
    ]);
    expect(sheet.getRow(2).values.slice(1)).toEqual([
      '2026-11-16', 'Senin', 'X-1', 'Matematika', 'Yohanes', 'Ganti guru', 'Maria',
      '07:00–08:30', '', 'Ruang X-1', '', 'Guru sakit',
    ]);
    expect(sheet.getRow(3).values.slice(1, 10)).toEqual([
      '2026-11-23', 'Senin', 'X-1', 'Matematika', 'Yohanes', 'Pindah jadwal', '', '07:00–08:30', '10:00–11:30',
    ]);
    // Isi "=1+1" tersimpan sebagai teks, bukan rumus.
    const cell = sheet.getRow(3).getCell(12);
    expect(cell.value).toBe('=1+1');
    expect(cell.formula).toBeUndefined();

    // Ekspor tidak berpaginasi: semua baris (dengan batas), filter ikut.
    const call = prisma.scheduleChange.findMany.mock.calls[0][0];
    expect(call).toMatchObject({ skip: 0, take: 20001 });
    expect(call.where.AND).toContainEqual({ scheduleSlot: { teachingAssignment: { classGroupId: 7 } } });
  });

  it('ekspor beban mengajar guru', async () => {
    prisma.teacher.findMany.mockResolvedValue([
      { id: 5, employeeNumber: '198001012005011001', user: { name: 'Yohanes' } },
    ]);
    prisma.scheduleSlot.findMany.mockResolvedValue([
      {
        dayOfWeek: 'MONDAY',
        startTime: t('07:00'),
        endTime: t('08:30'),
        teachingAssignment: { teacherId: 5, classGroupId: 7, subject: { id: 2, code: 'MAT', name: 'Matematika' } },
      },
    ]);

    const res = await getBinary('/api/v1/reports/teaching-load/export');
    const sheet = await readWorkbook(res.body);

    expect(sheet.getRow(1).values.slice(1, 7)).toEqual([
      'Nama Guru', 'NIP', 'Pertemuan per Minggu', 'Total Jam per Minggu', 'Jumlah Kelas', 'Mata Pelajaran',
    ]);
    expect(sheet.getRow(1).getCell(7).value).toBe('Senin (jam)');
    expect(sheet.getRow(2).values.slice(1)).toEqual([
      'Yohanes', '198001012005011001', 1, 1.5, 1, 'Matematika', 1.5, 0, 0, 0, 0, 0,
    ]);
  });

  it('terlalu banyak baris: 422 EXPORT_TOO_LARGE', async () => {
    prisma.scheduleChange.findMany.mockResolvedValue(new Array(20001).fill(substitute));
    const res = await get('/api/v1/reports/schedule-changes/export');
    expect(res.status).toBe(422);
    expect(res.body.error.code).toBe('EXPORT_TOO_LARGE');
  });

  it('page dan limit tidak dipakai pada ekspor', async () => {
    const res = await getBinary('/api/v1/reports/schedule-changes/export?page=3&limit=5');
    expect(res.status).toBe(200);
    expect(prisma.scheduleChange.findMany.mock.calls[0][0]).toMatchObject({ skip: 0, take: 20001 });
  });
});

describe('akses dan validasi laporan', () => {
  it('401 tanpa login; 403 untuk selain admin (tampilan dan ekspor)', async () => {
    expect((await request(app).get('/api/v1/reports/schedule-changes')).status).toBe(401);
    expect((await request(app).get('/api/v1/reports/schedule-changes/export')).status).toBe(401);
    for (const role of ['STUDENT', 'TEACHER', 'GUARDIAN']) {
      const auth = loginAs(role);
      expect((await get('/api/v1/reports/schedule-changes', auth)).status).toBe(403);
      expect((await get('/api/v1/reports/teaching-load/export', auth)).status).toBe(403);
    }
    expect(prisma.scheduleChange.findMany).not.toHaveBeenCalled();
  });

  it('jenis laporan tidak dikenal: 404 (termasuk nama bawaan objek)', async () => {
    expect((await get('/api/v1/reports/tidak-ada')).status).toBe(404);
    expect((await get('/api/v1/reports/constructor')).status).toBe(404);
    expect((await get('/api/v1/reports/tidak-ada/export')).status).toBe(404);
  });

  it('400 untuk filter tidak valid', async () => {
    expect((await get('/api/v1/reports/schedule-changes?type=LIBUR')).status).toBe(400);
    expect((await get('/api/v1/reports/schedule-changes?dateFrom=2026-12-01&dateTo=2026-11-01')).status).toBe(400);
    expect((await get('/api/v1/reports/schedule-changes/export?dateFrom=2026-02-30')).status).toBe(400);
    expect((await get('/api/v1/reports/teaching-load?teacherId=abc')).status).toBe(400);
  });
});
