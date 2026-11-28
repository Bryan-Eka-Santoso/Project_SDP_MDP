process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  student: { findUnique: jest.fn() },
  academicYear: { findFirst: jest.fn() },
  classMember: { findFirst: jest.fn() },
  subjectPackage: { findMany: jest.fn(), findFirst: jest.fn() },
  packageSelection: {
    findFirst: jest.fn(),
    findMany: jest.fn(),
    deleteMany: jest.fn(),
    create: jest.fn(),
    count: jest.fn(),
  },
  $transaction: jest.fn(),
}));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');

const auth = (role = 'STUDENT') =>
  `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: '1', expiresIn: 900 })}`;
const URL = '/api/v1/student-subject-packages';

const packageRow = (selectionCount, capacity = 5) => ({
  id: 7,
  name: 'Paket Sains',
  capacity,
  items: [{ subject: { id: 1, code: 'BIO', name: 'Biologi' } }],
  _count: { selections: selectionCount, items: 1 },
});

beforeEach(() => {
  jest.resetAllMocks();
  prisma.user.findUnique.mockResolvedValue({ id: 1, role: 'STUDENT', status: 'ACTIVE' });
  prisma.student.findUnique.mockResolvedValue({ id: 3 });
  prisma.academicYear.findFirst.mockResolvedValue({ id: 1, name: '2026/2027' });
  prisma.classMember.findFirst.mockResolvedValue({ studentId: 3 });
  prisma.packageSelection.findFirst.mockResolvedValue(null);
  prisma.packageSelection.findMany.mockResolvedValue([]);
  prisma.$transaction.mockImplementation((fn) => fn(prisma));
});

describe('F-MPP-04: kapasitas paket adalah kapasitas per kelas (BR 8.4)', () => {
  it('daftar paket menampilkan perkiraan jumlah kelas, bukan sisa kuota', async () => {
    prisma.subjectPackage.findMany.mockResolvedValue([packageRow(11, 5)]);

    const res = await request(app).get(URL).set('Authorization', auth());

    expect(res.status).toBe(200);
    expect(res.body.data.packages[0]).toMatchObject({
      capacity: 5,
      selectionCount: 11,
      classCount: 3,
    });
    expect(res.body.data.packages[0]).not.toHaveProperty('remainingCapacity');
  });

  it('paket tanpa peminat belum membutuhkan kelas', async () => {
    prisma.subjectPackage.findMany.mockResolvedValue([packageRow(0, 36)]);

    const res = await request(app).get(URL).set('Authorization', auth());

    expect(res.body.data.packages[0].classCount).toBe(0);
  });

  it('siswa ke-6 tetap bisa memilih paket berkapasitas 5', async () => {
    prisma.subjectPackage.findFirst.mockResolvedValue(packageRow(5, 5));
    prisma.packageSelection.count.mockResolvedValue(5);

    const res = await request(app).post(URL).set('Authorization', auth()).send({ packageId: 7 });

    expect(res.status).toBe(200);
    expect(prisma.packageSelection.create).toHaveBeenCalled();
  });

  it('400 jika packageId tidak dikirim', async () => {
    const res = await request(app).post(URL).set('Authorization', auth()).send({});
    expect(res.status).toBe(400);
  });

  it('401 tanpa token dan 403 untuk role lain', async () => {
    expect((await request(app).get(URL)).status).toBe(401);

    prisma.user.findUnique.mockResolvedValue({ id: 1, role: 'TEACHER', status: 'ACTIVE' });
    const res = await request(app).get(URL).set('Authorization', auth('TEACHER'));
    expect(res.status).toBe(403);
  });

  it('403 dengan penjelasan jika siswa belum punya kelas', async () => {
    prisma.classMember.findFirst.mockResolvedValue(null);

    const res = await request(app).get(URL).set('Authorization', auth());

    expect(res.status).toBe(403);
    expect(res.body.error.message).toBe('Anda belum terdaftar pada kelas tahun ajaran aktif.');
  });
});
