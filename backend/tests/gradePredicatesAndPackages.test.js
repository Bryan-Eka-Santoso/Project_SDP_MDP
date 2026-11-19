process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => {
  const db = {
    user: { findUnique: jest.fn() },
    academicYear: { findUnique: jest.fn(), findFirst: jest.fn() },
    gradePredicate: {
      findMany: jest.fn(),
      deleteMany: jest.fn(),
      createMany: jest.fn(),
    },
    subject: { findMany: jest.fn() },
    subjectPackage: {
      count: jest.fn(),
      findMany: jest.fn(),
      findUnique: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    subjectPackageItem: {
      deleteMany: jest.fn(),
      createMany: jest.fn(),
    },
    $transaction: jest.fn(),
  };
  db.$transaction.mockImplementation((arg) =>
    typeof arg === 'function' ? arg(db) : Promise.all(arg),
  );
  return db;
});

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');

function auth(role = 'ADMIN') {
  prisma.user.findUnique.mockResolvedValue({ id: 1, role, status: 'ACTIVE' });
  return `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: '1', expiresIn: 900 })}`;
}

beforeEach(() => {
  jest.clearAllMocks();
  prisma.$transaction.mockImplementation((arg) =>
    typeof arg === 'function' ? arg(prisma) : Promise.all(arg),
  );
});

describe('F-NIL-06: Skala predikat nilai per tahun ajaran', () => {
  it('GET /academic-years/:id/grade-predicates mengembalikan daftar terurut dengan minScore angka', async () => {
    prisma.academicYear.findUnique.mockResolvedValue({ id: 5, name: '2026/2027', isActive: true });
    prisma.gradePredicate.findMany.mockResolvedValue([
      { id: 1, academicYearId: 5, label: 'A', minScore: '90', description: 'Sangat Baik' },
      { id: 2, academicYearId: 5, label: 'B', minScore: '79.5', description: null },
      { id: 3, academicYearId: 5, label: 'D', minScore: '0', description: 'Perlu Bimbingan' },
    ]);

    const res = await request(app)
      .get('/api/v1/academic-years/5/grade-predicates')
      .set('Authorization', auth('ADMIN'));

    expect(res.status).toBe(200);
    expect(res.body.data).toEqual({
      academicYear: { id: 5, name: '2026/2027', isActive: true },
      predicates: [
        { id: 1, academicYearId: 5, label: 'A', minScore: 90, description: 'Sangat Baik' },
        { id: 2, academicYearId: 5, label: 'B', minScore: 79.5, description: null },
        { id: 3, academicYearId: 5, label: 'D', minScore: 0, description: 'Perlu Bimbingan' },
      ],
    });
  });

  it('GET /academic-years/:id/grade-predicates mengembalikan 404 bila tahun ajaran tidak ada', async () => {
    prisma.academicYear.findUnique.mockResolvedValue(null);

    const res = await request(app)
      .get('/api/v1/academic-years/99/grade-predicates')
      .set('Authorization', auth('ADMIN'));

    expect(res.status).toBe(404);
  });

  it('PATCH /academic-years/:id/grade-predicates mengganti skala secara atomik saat valid', async () => {
    prisma.academicYear.findUnique.mockResolvedValue({ id: 5, name: '2026/2027', isActive: true });
    prisma.gradePredicate.deleteMany.mockResolvedValue({ count: 2 });
    prisma.gradePredicate.createMany.mockResolvedValue({ count: 3 });
    prisma.gradePredicate.findMany.mockResolvedValue([
      { id: 10, academicYearId: 5, label: 'A', minScore: '85.5', description: 'Tuntas Tinggi' },
      { id: 11, academicYearId: 5, label: 'B', minScore: '75', description: null },
      { id: 12, academicYearId: 5, label: 'C', minScore: '0', description: 'Remedial' },
    ]);

    const res = await request(app)
      .patch('/api/v1/academic-years/5/grade-predicates')
      .set('Authorization', auth('ADMIN'))
      .send({
        predicates: [
          { label: 'C', minScore: 0, description: 'Remedial' },
          { label: 'A', minScore: 85.5, description: 'Tuntas Tinggi' },
          { label: 'B', minScore: 75, description: '' },
        ],
      });

    expect(res.status).toBe(200);
    expect(prisma.gradePredicate.deleteMany).toHaveBeenCalledWith({ where: { academicYearId: 5 } });
    expect(prisma.gradePredicate.createMany).toHaveBeenCalledWith({
      data: [
        { academicYearId: 5, label: 'C', minScore: 0, description: 'Remedial' },
        { academicYearId: 5, label: 'A', minScore: 85.5, description: 'Tuntas Tinggi' },
        { academicYearId: 5, label: 'B', minScore: 75, description: null },
      ],
    });
    expect(res.body.data.predicates[0].minScore).toBe(85.5);
  });

  it('PATCH menolak tanpa baris minScore 0, duplikat label/minScore, >2 desimal, dan non-admin', async () => {
    const tokenTeacher = auth('TEACHER');
    const forbidden = await request(app)
      .patch('/api/v1/academic-years/5/grade-predicates')
      .set('Authorization', tokenTeacher)
      .send({ predicates: [{ label: 'D', minScore: 0 }] });
    expect(forbidden.status).toBe(403);

    const tokenAdmin = auth('ADMIN');
    const missingZero = await request(app)
      .patch('/api/v1/academic-years/5/grade-predicates')
      .set('Authorization', tokenAdmin)
      .send({ predicates: [{ label: 'A', minScore: 90 }] });
    expect(missingZero.status).toBe(400);

    const dupLabel = await request(app)
      .patch('/api/v1/academic-years/5/grade-predicates')
      .set('Authorization', tokenAdmin)
      .send({
        predicates: [
          { label: 'A', minScore: 90 },
          { label: 'a', minScore: 0 },
        ],
      });
    expect(dupLabel.status).toBe(400);

    const dupScore = await request(app)
      .patch('/api/v1/academic-years/5/grade-predicates')
      .set('Authorization', tokenAdmin)
      .send({
        predicates: [
          { label: 'A', minScore: 0 },
          { label: 'B', minScore: 0 },
        ],
      });
    expect(dupScore.status).toBe(400);

    const badDecimal = await request(app)
      .patch('/api/v1/academic-years/5/grade-predicates')
      .set('Authorization', tokenAdmin)
      .send({
        predicates: [
          { label: 'A', minScore: 85.125 },
          { label: 'D', minScore: 0 },
        ],
      });
    expect(badDecimal.status).toBe(400);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });
});

describe('F-MPP-03: Master paket mata pelajaran pilihan', () => {
  const pkgRow = (overrides = {}) => ({
    id: 10,
    academicYearId: 5,
    name: 'Paket MIPA Lanjut',
    capacity: 36,
    createdAt: new Date('2026-07-01T00:00:00.000Z'),
    updatedAt: new Date('2026-07-01T00:00:00.000Z'),
    academicYear: { id: 5, name: '2026/2027', isActive: true },
    items: [
      { subject: { id: 3, code: 'BIO', name: 'Biologi', type: 'Pilihan' } },
      { subject: { id: 4, code: 'FIS', name: 'Fisika', type: 'Pilihan' } },
    ],
    _count: { selections: 0 },
    ...overrides,
  });

  it('GET /subject-packages memakai tahun ajaran aktif bila academicYearId tidak dikirim', async () => {
    prisma.academicYear.findFirst.mockResolvedValue({ id: 5 });
    prisma.subjectPackage.count.mockResolvedValue(1);
    prisma.subjectPackage.findMany.mockResolvedValue([pkgRow()]);

    const res = await request(app)
      .get('/api/v1/subject-packages?q=MIPA')
      .set('Authorization', auth('ADMIN'));

    expect(res.status).toBe(200);
    expect(res.body.meta.total).toBe(1);
    expect(res.body.data[0].subjects.map((s) => s.code)).toEqual(['BIO', 'FIS']);
    expect(prisma.subjectPackage.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { academicYearId: 5, name: { contains: 'MIPA' } },
      }),
    );
  });

  it('GET /subject-packages mengembalikan list kosong jika belum ada tahun ajaran aktif', async () => {
    prisma.academicYear.findFirst.mockResolvedValue(null);

    const res = await request(app)
      .get('/api/v1/subject-packages')
      .set('Authorization', auth('ADMIN'));

    expect(res.status).toBe(200);
    expect(res.body.data).toEqual([]);
    expect(res.body.meta.total).toBe(0);
  });

  it('POST /subject-packages membuat paket + daftar mapel (201), dan menolak duplikat nama / mapel tidak valid / non-admin', async () => {
    prisma.academicYear.findUnique.mockResolvedValue({ id: 5 });
    prisma.subjectPackage.findMany.mockResolvedValue([]);
    prisma.subject.findMany.mockResolvedValue([{ id: 3 }, { id: 4 }]);
    prisma.subjectPackage.create.mockResolvedValue(pkgRow());

    const created = await request(app)
      .post('/api/v1/subject-packages')
      .set('Authorization', auth('ADMIN'))
      .send({
        academicYearId: 5,
        name: 'Paket MIPA Lanjut',
        capacity: 36,
        subjectIds: [3, 4],
      });

    expect(created.status).toBe(201);
    expect(created.body.data.name).toBe('Paket MIPA Lanjut');

    prisma.subjectPackage.findMany.mockResolvedValue([{ id: 10, name: 'Paket MIPA Lanjut' }]);
    const duplicate = await request(app)
      .post('/api/v1/subject-packages')
      .set('Authorization', auth('ADMIN'))
      .send({
        academicYearId: 5,
        name: 'paket mipa lanjut',
        capacity: 30,
        subjectIds: [3],
      });
    expect(duplicate.status).toBe(409);

    prisma.subjectPackage.findMany.mockResolvedValue([]);
    prisma.subject.findMany.mockResolvedValue([{ id: 3 }]);
    const missingSubject = await request(app)
      .post('/api/v1/subject-packages')
      .set('Authorization', auth('ADMIN'))
      .send({
        academicYearId: 5,
        name: 'Paket Baru',
        capacity: 30,
        subjectIds: [3, 999],
      });
    expect(missingSubject.status).toBe(400);

    const forbidden = await request(app)
      .post('/api/v1/subject-packages')
      .set('Authorization', auth('STUDENT'))
      .send({
        academicYearId: 5,
        name: 'Paket Baru',
        capacity: 30,
        subjectIds: [3],
      });
    expect(forbidden.status).toBe(403);
  });

  it('PATCH /subject-packages/:id memperbarui nama, kapasitas, dan daftar mapel secara atomik', async () => {
    prisma.subjectPackage.findUnique.mockResolvedValue({
      id: 10,
      academicYearId: 5,
      name: 'Paket Lama',
    });
    prisma.subjectPackage.findMany.mockResolvedValue([]);
    prisma.subject.findMany.mockResolvedValue([{ id: 5 }]);
    prisma.subjectPackage.update.mockResolvedValue(
      pkgRow({
        name: 'Paket IPS',
        capacity: 32,
        items: [{ subject: { id: 5, code: 'EKO', name: 'Ekonomi', type: 'Pilihan' } }],
      }),
    );

    const res = await request(app)
      .patch('/api/v1/subject-packages/10')
      .set('Authorization', auth('ADMIN'))
      .send({ name: 'Paket IPS', capacity: 32, subjectIds: [5] });

    expect(res.status).toBe(200);
    expect(prisma.subjectPackageItem.deleteMany).toHaveBeenCalledWith({ where: { packageId: 10 } });
    expect(prisma.subjectPackageItem.createMany).toHaveBeenCalledWith({
      data: [{ packageId: 10, subjectId: 5 }],
    });
    expect(res.body.data.capacity).toBe(32);
  });

  it('DELETE /subject-packages/:id menolak jika sudah dipilih siswa (409), dan menghapus bersih jika kosong (204)', async () => {
    prisma.subjectPackage.findUnique.mockResolvedValue({ id: 10, _count: { selections: 4 } });
    const blocked = await request(app)
      .delete('/api/v1/subject-packages/10')
      .set('Authorization', auth('ADMIN'));
    expect(blocked.status).toBe(409);

    prisma.subjectPackage.findUnique.mockResolvedValue({ id: 10, _count: { selections: 0 } });
    const deleted = await request(app)
      .delete('/api/v1/subject-packages/10')
      .set('Authorization', auth('ADMIN'));
    expect(deleted.status).toBe(204);
    expect(prisma.subjectPackageItem.deleteMany).toHaveBeenCalledWith({ where: { packageId: 10 } });
    expect(prisma.subjectPackage.delete).toHaveBeenCalledWith({ where: { id: 10 } });
  });
});
