process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  student: { findUnique: jest.fn(), findFirst: jest.fn() },
  studentGuardian: { findFirst: jest.fn() },
  classMember: { findFirst: jest.fn() },
  classGroup: { findMany: jest.fn() },
  bill: { findMany: jest.fn(), count: jest.fn() },
}));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const { parseDateOnly, todayLocal } = require('../src/utils/date');

const d = (ymd) => new Date(`${ymd}T00:00:00.000Z`);

function loginAs(role, id = 1) {
  prisma.user.findUnique.mockResolvedValue({ id, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: String(id), expiresIn: 900 })}`;
}

// Tanggal jauh di masa lalu/depan supaya status menunggak tidak bergantung pada hari tes dijalankan.
const bills = [
  { id: 3, billCode: 'SPP-2099-01', paymentType: 'SPP', amount: '850000', dueDate: d('2099-01-10'), status: 'UNPAID', paidDate: null },
  { id: 2, billCode: 'SPP-2020-02', paymentType: 'SPP', amount: '850000', dueDate: d('2020-02-10'), status: 'UNPAID', paidDate: null },
  { id: 1, billCode: 'SPP-2020-01', paymentType: 'SPP', amount: '850000', dueDate: d('2020-01-10'), status: 'PAID', paidDate: d('2020-01-08') },
];

beforeEach(() => {
  jest.resetAllMocks();
  prisma.bill.findMany.mockResolvedValue(bills);
  prisma.bill.count.mockResolvedValue(3);
});

describe('GET /api/v1/students/:studentId/bills', () => {
  it('siswa melihat tagihannya sendiri; status menunggak dihitung dari jatuh tempo (WIB)', async () => {
    prisma.student.findFirst.mockResolvedValue({ id: 7 });

    const res = await request(app).get('/api/v1/students/7/bills').set('Authorization', loginAs('STUDENT', 50));

    expect(res.status).toBe(200);
    expect(prisma.student.findFirst).toHaveBeenCalledWith({ where: { id: 7, userId: 50 }, select: { id: true } });
    expect(res.body.meta).toEqual({ page: 1, limit: 20, total: 3, totalPages: 1 });
    expect(res.body.data).toEqual([
      { id: 3, billCode: 'SPP-2099-01', paymentType: 'SPP', amount: 850000, dueDate: '2099-01-10', status: 'UNPAID', paidDate: null, isOverdue: false },
      { id: 2, billCode: 'SPP-2020-02', paymentType: 'SPP', amount: 850000, dueDate: '2020-02-10', status: 'UNPAID', paidDate: null, isOverdue: true },
      { id: 1, billCode: 'SPP-2020-01', paymentType: 'SPP', amount: 850000, dueDate: '2020-01-10', status: 'PAID', paidDate: '2020-01-08', isOverdue: false },
    ]);
    expect(prisma.bill.findMany.mock.calls[0][0].where).toEqual({ AND: [{ studentId: 7 }] });
  });

  it('tagihan yang jatuh temponya hari ini belum menunggak', async () => {
    prisma.student.findFirst.mockResolvedValue({ id: 7 });
    prisma.bill.findMany.mockResolvedValue([{ ...bills[0], dueDate: parseDateOnly(todayLocal()) }]);

    const res = await request(app).get('/api/v1/students/7/bills').set('Authorization', loginAs('STUDENT'));

    expect(res.body.data[0].isOverdue).toBe(false);
  });

  it('siswa lain: 404, bukan 403', async () => {
    prisma.student.findFirst.mockResolvedValue(null);
    const res = await request(app).get('/api/v1/students/8/bills').set('Authorization', loginAs('STUDENT'));
    expect(res.status).toBe(404);
    expect(prisma.bill.findMany).not.toHaveBeenCalled();
  });

  it('wali murid hanya untuk anaknya (StudentGuardian)', async () => {
    prisma.studentGuardian.findFirst.mockResolvedValue({ studentId: 7 });
    const ok = await request(app).get('/api/v1/students/7/bills').set('Authorization', loginAs('GUARDIAN', 60));
    expect(ok.status).toBe(200);
    expect(prisma.studentGuardian.findFirst).toHaveBeenCalledWith({
      where: { studentId: 7, guardian: { userId: 60 } },
      select: { studentId: true },
    });

    prisma.studentGuardian.findFirst.mockResolvedValue(null);
    const other = await request(app).get('/api/v1/students/9/bills').set('Authorization', loginAs('GUARDIAN', 60));
    expect(other.status).toBe(404);
  });

  it('guru hanya untuk siswa kelas binaannya pada tahun ajaran aktif', async () => {
    prisma.classMember.findFirst.mockResolvedValue({ studentId: 7 });
    const ok = await request(app).get('/api/v1/students/7/bills').set('Authorization', loginAs('TEACHER', 40));
    expect(ok.status).toBe(200);
    expect(prisma.classMember.findFirst.mock.calls[0][0].where).toEqual({
      studentId: 7,
      classGroup: { homeroomTeacher: { userId: 40 }, academicYear: { isActive: true } },
    });

    prisma.classMember.findFirst.mockResolvedValue(null);
    const other = await request(app).get('/api/v1/students/9/bills').set('Authorization', loginAs('TEACHER', 40));
    expect(other.status).toBe(404);
  });

  it('admin melihat siswa mana pun; siswa yang tidak ada 404', async () => {
    prisma.student.findUnique.mockResolvedValue({ id: 7 });
    expect((await request(app).get('/api/v1/students/7/bills').set('Authorization', loginAs('ADMIN'))).status).toBe(200);

    prisma.student.findUnique.mockResolvedValue(null);
    expect((await request(app).get('/api/v1/students/99/bills').set('Authorization', loginAs('ADMIN'))).status).toBe(404);
  });

  it('filter status, menunggak, dan rentang jatuh tempo masuk ke query', async () => {
    prisma.student.findUnique.mockResolvedValue({ id: 7 });
    const today = parseDateOnly(todayLocal());

    await request(app)
      .get('/api/v1/students/7/bills?status=UNPAID&overdue=true&dueFrom=2020-01-01&dueTo=2020-12-31')
      .set('Authorization', loginAs('ADMIN'));
    expect(prisma.bill.findMany.mock.calls[0][0].where.AND).toEqual([
      { studentId: 7 },
      { status: 'UNPAID' },
      { status: 'UNPAID', dueDate: { lt: today } },
      { dueDate: { gte: d('2020-01-01') } },
      { dueDate: { lte: d('2020-12-31') } },
    ]);

    await request(app).get('/api/v1/students/7/bills?overdue=false').set('Authorization', loginAs('ADMIN'));
    expect(prisma.bill.findMany.mock.calls[1][0].where.AND).toEqual([
      { studentId: 7 },
      { OR: [{ status: 'PAID' }, { dueDate: { gte: today } }] },
    ]);
  });

  it('400 untuk studentId, status, atau rentang tanggal yang tidak valid; 401 tanpa login', async () => {
    const auth = loginAs('ADMIN');
    expect((await request(app).get('/api/v1/students/abc/bills').set('Authorization', auth)).status).toBe(400);
    expect((await request(app).get('/api/v1/students/7/bills?status=LUNAS').set('Authorization', auth)).status).toBe(400);
    expect((await request(app).get('/api/v1/students/7/bills?dueFrom=2026-02-30').set('Authorization', auth)).status).toBe(400);
    expect((await request(app).get('/api/v1/students/7/bills?dueFrom=2026-05-01&dueTo=2026-04-01').set('Authorization', auth)).status).toBe(400);
    expect((await request(app).get('/api/v1/students/7/bills')).status).toBe(401);
  });
});

describe('GET /api/v1/bills', () => {
  const row = {
    ...bills[1],
    student: {
      id: 7,
      nis: '2026001',
      user: { name: 'Angela Putri' },
      classMemberships: [{ classGroup: { id: 10, name: 'X-1' } }],
    },
  };

  it('admin: daftar lintas siswa dengan identitas siswa dan kelas aktifnya', async () => {
    prisma.bill.findMany.mockResolvedValue([row]);
    prisma.bill.count.mockResolvedValue(1);

    const res = await request(app).get('/api/v1/bills?classGroupId=10&q=angela&status=UNPAID').set('Authorization', loginAs('ADMIN'));

    expect(res.status).toBe(200);
    expect(res.body.data[0]).toMatchObject({
      id: 2,
      isOverdue: true,
      student: { id: 7, nis: '2026001', name: 'Angela Putri', classGroups: [{ id: 10, name: 'X-1' }] },
    });
    expect(prisma.bill.findMany.mock.calls[0][0].where).toEqual({
      AND: [{ status: 'UNPAID' }],
      student: {
        classMemberships: { some: { classGroupId: { in: [10] } } },
        OR: [{ nis: { contains: 'angela' } }, { user: { name: { contains: 'angela' } } }],
      },
    });
    expect(prisma.classGroup.findMany).not.toHaveBeenCalled();
  });

  it('admin tanpa filter kelas/pencarian: tidak membatasi siswa', async () => {
    prisma.bill.findMany.mockResolvedValue([row]);
    await request(app).get('/api/v1/bills').set('Authorization', loginAs('ADMIN'));
    expect(prisma.bill.findMany.mock.calls[0][0].where).toEqual({ AND: [] });
  });

  it('wali kelas: otomatis dibatasi ke kelas binaan tahun ajaran aktif', async () => {
    prisma.classGroup.findMany.mockResolvedValue([{ id: 10 }, { id: 11 }]);
    prisma.bill.findMany.mockResolvedValue([row]);

    const res = await request(app).get('/api/v1/bills').set('Authorization', loginAs('TEACHER', 40));

    expect(res.status).toBe(200);
    expect(prisma.classGroup.findMany.mock.calls[0][0].where).toEqual({
      homeroomTeacher: { userId: 40 },
      academicYear: { isActive: true },
    });
    expect(prisma.bill.findMany.mock.calls[0][0].where.student).toEqual({
      classMemberships: { some: { classGroupId: { in: [10, 11] } } },
    });
  });

  it('wali kelas: classGroupId di luar kelas binaan → 404', async () => {
    prisma.classGroup.findMany.mockResolvedValue([{ id: 10 }]);
    const res = await request(app).get('/api/v1/bills?classGroupId=99').set('Authorization', loginAs('TEACHER'));
    expect(res.status).toBe(404);
    expect(prisma.bill.findMany).not.toHaveBeenCalled();
  });

  it('403 untuk guru yang bukan wali kelas, siswa, dan wali murid', async () => {
    prisma.classGroup.findMany.mockResolvedValue([]);
    expect((await request(app).get('/api/v1/bills').set('Authorization', loginAs('TEACHER'))).status).toBe(403);
    expect((await request(app).get('/api/v1/bills').set('Authorization', loginAs('STUDENT'))).status).toBe(403);
    expect((await request(app).get('/api/v1/bills').set('Authorization', loginAs('GUARDIAN'))).status).toBe(403);
    expect((await request(app).get('/api/v1/bills')).status).toBe(401);
  });

  it('paginasi mengikuti page dan limit', async () => {
    prisma.bill.findMany.mockResolvedValue([row]);
    prisma.bill.count.mockResolvedValue(45);

    const res = await request(app).get('/api/v1/bills?page=2&limit=10').set('Authorization', loginAs('ADMIN'));

    expect(res.body.meta).toEqual({ page: 2, limit: 10, total: 45, totalPages: 5 });
    expect(prisma.bill.findMany.mock.calls[0][0]).toMatchObject({ skip: 10, take: 10 });
  });
});
