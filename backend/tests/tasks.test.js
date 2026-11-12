process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  semester: { findFirst: jest.fn() },
  teachingAssignment: { findFirst: jest.fn(), findMany: jest.fn() },
  task: {
    create: jest.fn(),
    findMany: jest.fn(),
    findFirst: jest.fn(),
    count: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
}));
jest.mock('../src/modules/notifications/notifications.service', () => ({ notify: jest.fn() }));
jest.mock('../src/modules/notifications/notifications.recipients', () => ({
  studentUserIdsOfClasses: jest.fn(),
}));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { notify } = require('../src/modules/notifications/notifications.service');
const recipients = require('../src/modules/notifications/notifications.recipients');
const { parseDateOnly, todayLocal } = require('../src/utils/date');

const d = (ymd) => new Date(`${ymd}T00:00:00.000Z`);
const flush = () => new Promise((resolve) => setImmediate(resolve));

function loginAs(role, id = 1) {
  prisma.user.findUnique.mockResolvedValue({ id, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: String(id), expiresIn: 900 })}`;
}

const row = {
  id: 1,
  title: 'Kerjakan LKS bab 3',
  description: 'Dikerjakan di buku tulis.',
  dueDate: d('2099-01-20'),
  createdAt: new Date('2026-11-09T01:00:00.000Z'),
  updatedAt: new Date('2026-11-09T01:00:00.000Z'),
  teachingAssignment: {
    id: 10,
    semesterId: 1,
    teacher: { id: 5, user: { name: 'Yohanes' } },
    subject: { id: 2, code: 'MAT', name: 'Matematika' },
    classGroup: { id: 7, name: 'X-1' },
  },
};

const dto = {
  id: 1,
  title: 'Kerjakan LKS bab 3',
  description: 'Dikerjakan di buku tulis.',
  dueDate: '2099-01-20',
  createdAt: '2026-11-09T01:00:00.000Z',
  updatedAt: '2026-11-09T01:00:00.000Z',
  teachingAssignment: {
    id: 10,
    teacher: { id: 5, name: 'Yohanes' },
    subject: { id: 2, code: 'MAT', name: 'Matematika' },
    classGroup: { id: 7, name: 'X-1' },
  },
};

const post = (body, auth) => request(app).post('/api/v1/tasks').set('Authorization', auth).send(body);
const valid = { teachingAssignmentId: 10, title: 'Kerjakan LKS bab 3', dueDate: '2099-01-20' };

beforeEach(() => {
  jest.resetAllMocks();
  prisma.semester.findFirst.mockResolvedValue({ id: 1 });
  prisma.teachingAssignment.findFirst.mockResolvedValue({ id: 10 });
  prisma.task.create.mockResolvedValue(row);
  prisma.task.findMany.mockResolvedValue([row]);
  prisma.task.count.mockResolvedValue(1);
  prisma.task.findFirst.mockResolvedValue(row);
  prisma.task.update.mockResolvedValue(row);
  recipients.studentUserIdsOfClasses.mockResolvedValue([201, 202]);
});

describe('POST /api/v1/tasks', () => {
  it('guru membuat tugas untuk penugasannya dan siswa kelas diberi notifikasi TASK', async () => {
    const res = await post({ ...valid, description: 'Dikerjakan di buku tulis.' }, loginAs('TEACHER', 40));

    expect(res.status).toBe(201);
    expect(res.body.data).toEqual(dto);
    expect(prisma.teachingAssignment.findFirst).toHaveBeenCalledWith({
      where: { id: 10, teacher: { userId: 40 } },
      select: { id: true },
    });
    expect(prisma.task.create.mock.calls[0][0].data).toEqual({
      teachingAssignmentId: 10,
      title: 'Kerjakan LKS bab 3',
      description: 'Dikerjakan di buku tulis.',
      dueDate: d('2099-01-20'),
    });

    await flush();
    expect(recipients.studentUserIdsOfClasses).toHaveBeenCalledWith([7]);
    expect(notify).toHaveBeenCalledTimes(1);
    expect(notify).toHaveBeenCalledWith([201, 202], {
      type: 'TASK',
      title: 'Tugas baru',
      body: expect.stringContaining('Matematika X-1: Kerjakan LKS bab 3.'),
      referenceId: 1,
    });
  });

  it('catatan kosong disimpan null', async () => {
    await post({ ...valid, description: '   ' }, loginAs('TEACHER'));
    expect(prisma.task.create.mock.calls[0][0].data.description).toBeNull();
  });

  it('penugasan milik guru lain atau tidak ada: 400 pada field teachingAssignmentId', async () => {
    prisma.teachingAssignment.findFirst.mockResolvedValue(null);
    const res = await post(valid, loginAs('TEACHER'));
    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body.teachingAssignmentId');
    expect(prisma.task.create).not.toHaveBeenCalled();
    expect(notify).not.toHaveBeenCalled();
  });

  it('tenggat sebelum hari ini ditolak; hari ini boleh', async () => {
    const past = await post({ ...valid, dueDate: '2020-01-01' }, loginAs('TEACHER'));
    expect(past.status).toBe(400);
    expect(past.body.error.details[0].field).toBe('body.dueDate');

    const today = await post({ ...valid, dueDate: todayLocal() }, loginAs('TEACHER'));
    expect(today.status).toBe(201);
  });

  it('400 untuk judul kosong, judul terlalu panjang, dan tanggal tidak ada', async () => {
    const auth = loginAs('TEACHER');
    expect((await post({ ...valid, title: '  ' }, auth)).status).toBe(400);
    expect((await post({ ...valid, title: 'x'.repeat(151) }, auth)).status).toBe(400);
    expect((await post({ ...valid, dueDate: '2099-02-30' }, auth)).status).toBe(400);
    expect((await post({ title: 'Tanpa penugasan', dueDate: '2099-01-20' }, auth)).status).toBe(400);
  });

  it('401 tanpa login; 403 untuk siswa, wali murid, dan admin', async () => {
    expect((await request(app).post('/api/v1/tasks').send(valid)).status).toBe(401);
    for (const role of ['STUDENT', 'GUARDIAN', 'ADMIN']) {
      expect((await post(valid, loginAs(role))).status).toBe(403);
    }
    expect(prisma.task.create).not.toHaveBeenCalled();
  });
});

describe('GET /api/v1/tasks', () => {
  it('siswa: tugas kelasnya pada semester aktif, urut tenggat', async () => {
    const res = await request(app).get('/api/v1/tasks').set('Authorization', loginAs('STUDENT', 50));

    expect(res.status).toBe(200);
    expect(res.body.data).toEqual([dto]);
    expect(res.body.meta).toEqual({ page: 1, limit: 20, total: 1, totalPages: 1 });
    const call = prisma.task.findMany.mock.calls[0][0];
    expect(call.where.AND).toEqual([
      { teachingAssignment: { classGroup: { members: { some: { student: { userId: 50 } } } } } },
      { teachingAssignment: { semesterId: 1 } },
    ]);
    expect(call.orderBy).toEqual([{ dueDate: 'asc' }, { id: 'asc' }]);
  });

  it('guru: hanya penugasan miliknya, dengan filter', async () => {
    await request(app)
      .get('/api/v1/tasks?teachingAssignmentId=10&subjectId=2&classGroupId=7&dueFrom=2099-01-01&dueTo=2099-01-31&semesterId=3')
      .set('Authorization', loginAs('TEACHER', 40));

    expect(prisma.semester.findFirst).not.toHaveBeenCalled();
    expect(prisma.task.findMany.mock.calls[0][0].where.AND).toEqual([
      { teachingAssignment: { teacher: { userId: 40 } } },
      { teachingAssignment: { semesterId: 3 } },
      { teachingAssignmentId: 10 },
      { teachingAssignment: { subjectId: 2 } },
      { teachingAssignment: { classGroupId: 7 } },
      { dueDate: { gte: parseDateOnly('2099-01-01') } },
      { dueDate: { lte: parseDateOnly('2099-01-31') } },
    ]);
  });

  it('tanpa semester aktif: daftar kosong', async () => {
    prisma.semester.findFirst.mockResolvedValue(null);
    const res = await request(app).get('/api/v1/tasks').set('Authorization', loginAs('STUDENT'));
    expect(res.body.data).toEqual([]);
    expect(res.body.meta.total).toBe(0);
    expect(prisma.task.findMany).not.toHaveBeenCalled();
  });

  it('403 untuk wali murid dan admin; 401 tanpa login; 400 rentang terbalik', async () => {
    expect((await request(app).get('/api/v1/tasks').set('Authorization', loginAs('GUARDIAN'))).status).toBe(403);
    expect((await request(app).get('/api/v1/tasks').set('Authorization', loginAs('ADMIN'))).status).toBe(403);
    expect((await request(app).get('/api/v1/tasks')).status).toBe(401);
    const bad = await request(app)
      .get('/api/v1/tasks?dueFrom=2099-02-01&dueTo=2099-01-01')
      .set('Authorization', loginAs('TEACHER'));
    expect(bad.status).toBe(400);
  });
});

describe('GET /api/v1/tasks/:id', () => {
  it('siswa kelas itu dan guru pemiliknya boleh; di luar cakupan 404', async () => {
    const student = await request(app).get('/api/v1/tasks/1').set('Authorization', loginAs('STUDENT', 50));
    expect(student.status).toBe(200);
    expect(prisma.task.findFirst.mock.calls[0][0].where).toEqual({
      AND: [{ id: 1 }, { teachingAssignment: { classGroup: { members: { some: { student: { userId: 50 } } } } } }],
    });

    const teacher = await request(app).get('/api/v1/tasks/1').set('Authorization', loginAs('TEACHER', 40));
    expect(teacher.status).toBe(200);
    expect(prisma.task.findFirst.mock.calls[1][0].where.AND[1]).toEqual({
      teachingAssignment: { teacher: { userId: 40 } },
    });

    prisma.task.findFirst.mockResolvedValue(null);
    expect((await request(app).get('/api/v1/tasks/9').set('Authorization', loginAs('STUDENT'))).status).toBe(404);
  });

  it('400 untuk id tidak valid', async () => {
    expect((await request(app).get('/api/v1/tasks/abc').set('Authorization', loginAs('TEACHER'))).status).toBe(400);
  });
});

describe('PATCH /api/v1/tasks/:id', () => {
  const patch = (body, auth) => request(app).patch('/api/v1/tasks/1').set('Authorization', auth).send(body);

  it('guru pemilik memperbarui sebagian field; tidak mengirim notifikasi', async () => {
    const res = await patch({ title: 'Judul baru', dueDate: '2020-01-01' }, loginAs('TEACHER', 40));

    expect(res.status).toBe(200);
    expect(prisma.task.findFirst).toHaveBeenCalledWith({
      where: { id: 1, teachingAssignment: { teacher: { userId: 40 } } },
      select: { id: true },
    });
    // Tenggat hanya dicek saat tugas dibuat.
    expect(prisma.task.update.mock.calls[0][0].data).toEqual({ title: 'Judul baru', dueDate: d('2020-01-01') });
    await flush();
    expect(notify).not.toHaveBeenCalled();
  });

  it('catatan dikosongkan menjadi null', async () => {
    await patch({ description: '' }, loginAs('TEACHER'));
    expect(prisma.task.update.mock.calls[0][0].data).toEqual({ description: null });
  });

  it('tugas guru lain 404; body kosong 400; siswa 403', async () => {
    prisma.task.findFirst.mockResolvedValue(null);
    expect((await patch({ title: 'x' }, loginAs('TEACHER'))).status).toBe(404);
    expect(prisma.task.update).not.toHaveBeenCalled();
    expect((await patch({}, loginAs('TEACHER'))).status).toBe(400);
    expect((await patch({ title: 'x' }, loginAs('STUDENT'))).status).toBe(403);
  });
});

describe('DELETE /api/v1/tasks/:id', () => {
  const del = (auth) => request(app).delete('/api/v1/tasks/1').set('Authorization', auth);

  it('guru pemilik menghapus (204)', async () => {
    const res = await del(loginAs('TEACHER', 40));
    expect(res.status).toBe(204);
    expect(prisma.task.delete).toHaveBeenCalledWith({ where: { id: 1 } });
  });

  it('tugas guru lain 404; siswa 403; tanpa login 401', async () => {
    prisma.task.findFirst.mockResolvedValue(null);
    expect((await del(loginAs('TEACHER'))).status).toBe(404);
    expect(prisma.task.delete).not.toHaveBeenCalled();
    expect((await del(loginAs('STUDENT'))).status).toBe(403);
    expect((await request(app).delete('/api/v1/tasks/1')).status).toBe(401);
  });
});

describe('GET /api/v1/me/teaching-assignments', () => {
  const get = (auth) => request(app).get('/api/v1/me/teaching-assignments').set('Authorization', auth);

  it('guru: penugasan miliknya pada semester aktif, urut kelas lalu mapel', async () => {
    const rows = [
      { id: 10, subject: { id: 2, code: 'MAT', name: 'Matematika' }, classGroup: { id: 7, name: 'X-1' } },
      { id: 11, subject: { id: 3, code: 'FIS', name: 'Fisika' }, classGroup: { id: 8, name: 'X-2' } },
    ];
    prisma.teachingAssignment.findMany.mockResolvedValue(rows);

    const res = await get(loginAs('TEACHER', 40));

    expect(res.status).toBe(200);
    expect(res.body.data).toEqual(rows);
    const call = prisma.teachingAssignment.findMany.mock.calls[0][0];
    expect(call.where).toEqual({ semesterId: 1, teacher: { userId: 40 } });
    expect(call.orderBy).toEqual([{ classGroup: { name: 'asc' } }, { subject: { name: 'asc' } }]);
    // hanya data pilihan form; tanpa data guru lain atau siswa
    expect(call.select).toEqual({
      id: true,
      subject: { select: { id: true, code: true, name: true } },
      classGroup: { select: { id: true, name: true } },
    });
  });

  it('tanpa semester aktif: daftar kosong tanpa query penugasan', async () => {
    prisma.semester.findFirst.mockResolvedValue(null);

    const res = await get(loginAs('TEACHER'));

    expect(res.status).toBe(200);
    expect(res.body.data).toEqual([]);
    expect(prisma.teachingAssignment.findMany).not.toHaveBeenCalled();
  });

  it('siswa, wali murid, admin 403; tanpa login 401', async () => {
    for (const role of ['STUDENT', 'GUARDIAN', 'ADMIN']) {
      expect((await get(loginAs(role))).status).toBe(403);
    }
    expect((await request(app).get('/api/v1/me/teaching-assignments')).status).toBe(401);
    expect(prisma.teachingAssignment.findMany).not.toHaveBeenCalled();
  });
});
