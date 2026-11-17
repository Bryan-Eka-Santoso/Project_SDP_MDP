process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';

jest.mock('../src/lib/prisma', () => ({
  user: { findUnique: jest.fn() },
  subjectPackage: { findUnique: jest.fn() },
  classGroup: { findMany: jest.fn(), create: jest.fn() },
  classMember: { createMany: jest.fn() },
  academicYear: { findMany: jest.fn() },
  $transaction: jest.fn(),
}));

const jwt = require('jsonwebtoken');
const request = require('supertest');
const prisma = require('../src/lib/prisma');
const app = require('../src/app');
const service = require('../src/modules/elective/elective.allocation.service');

const auth = (role = 'ADMIN') => `Bearer ${jwt.sign({ role }, process.env.JWT_ACCESS_SECRET, { subject: '1', expiresIn: 900 })}`;
const pkg = (n) => ({
  id: 5, name: 'Paket Sains', capacity: 36, academicYearId: 1,
  academicYear: { id: 1, name: '2026/2027' },
  selections: Array.from({ length: n }, (_, i) => ({
    studentId: i + 1,
    student: { nis: String(i + 1), user: { name: `Siswa ${i + 1}` }, classMemberships: [] },
  })),
});

beforeEach(() => {
  jest.resetAllMocks();
  prisma.user.findUnique.mockResolvedValue({ id: 1, role: 'ADMIN', status: 'ACTIVE' });
  prisma.$transaction.mockImplementation((fn) => fn(prisma));
});

test('pembagian 50 siswa kapasitas 36 menjadi 25 dan 25', () => {
  expect(service.balancedSizes(50, 36)).toEqual([25, 25]);
});
test('pembagian 80 siswa kapasitas 36 menjadi 27,27,26', () => {
  expect(service.balancedSizes(80, 36)).toEqual([27, 27, 26]);
});
test('tidak ada kelas untuk paket tanpa siswa', () => {
  expect(service.balancedSizes(0, 36)).toEqual([]);
});
test('pengguna anonim tidak boleh melihat preview', async () => {
  const res = await request(app).get('/api/v1/admin/elective/allocations/preview?packageId=5&gradeLevel=XI');
  expect(res.status).toBe(401);
});
test('guru tidak boleh mengelola MPP', async () => {
  prisma.user.findUnique.mockResolvedValue({ id: 1, role: 'TEACHER', status: 'ACTIVE' });
  const res = await request(app).get('/api/v1/admin/elective/package-trends').set('Authorization', auth('TEACHER'));
  expect(res.status).toBe(403);
});
test('preview 50 siswa dibagi merata tanpa menyimpan database', async () => {
  prisma.subjectPackage.findUnique.mockResolvedValue(pkg(50));
  const res = await request(app).get('/api/v1/admin/elective/allocations/preview?packageId=5&gradeLevel=XI').set('Authorization', auth());
  expect(res.status).toBe(200);
  expect(res.body.data.classes.map((c) => c.students.length)).toEqual([25, 25]);
  expect(prisma.classGroup.create).not.toHaveBeenCalled();
});
test('pemeriksaan gradeLevel menolak siswa lintas tingkat', async () => {
  const result = pkg(1);
  result.selections[0].student.classMemberships = [{ classGroup: { academicYearId: 1, gradeLevel: 'X' } }];
  prisma.subjectPackage.findUnique.mockResolvedValue(result);
  const res = await request(app).get('/api/v1/admin/elective/allocations/preview?packageId=5&gradeLevel=XI').set('Authorization', auth());
  expect(res.status).toBe(409);
});
test('apply menolak preview lama setelah pilihan siswa berubah', async () => {
  prisma.subjectPackage.findUnique.mockResolvedValue(pkg(2));
  const res = await request(app).post('/api/v1/admin/elective/allocations').set('Authorization', auth())
    .send({ packageId: 5, gradeLevel: 'XI', signature: 'a'.repeat(64) });
  expect(res.status).toBe(409);
  expect(prisma.classGroup.create).not.toHaveBeenCalled();
});
test('apply membuat ClassGroup dan ClassMember dengan transaction', async () => {
  prisma.subjectPackage.findUnique.mockResolvedValue(pkg(2));
  prisma.classGroup.findMany.mockResolvedValue([]);
  prisma.classGroup.create.mockResolvedValue({ id: 7, name: 'MPP-5-XI-1' });
  prisma.classMember.createMany.mockResolvedValue({ count: 2 });
  const preview = await service.preview({ packageId: 5, gradeLevel: 'XI' });
  const res = await request(app).post('/api/v1/admin/elective/allocations').set('Authorization', auth())
    .send({ packageId: 5, gradeLevel: 'XI', signature: preview.signature });
  expect(res.status).toBe(201);
  expect(prisma.classMember.createMany).toHaveBeenCalledWith({ data: [
    { studentId: 1, classGroupId: 7 }, { studentId: 2, classGroupId: 7 },
  ] });
});
test('apply menolak kelas bernama sama agar tidak duplikat', async () => {
  prisma.subjectPackage.findUnique.mockResolvedValue(pkg(2));
  prisma.classGroup.findMany.mockResolvedValue([{ name: 'MPP-5-XI-1' }]);
  const preview = await service.preview({ packageId: 5, gradeLevel: 'XI' });
  const res = await request(app).post('/api/v1/admin/elective/allocations').set('Authorization', auth())
    .send({ packageId: 5, gradeLevel: 'XI', signature: preview.signature });
  expect(res.status).toBe(409);
});
test('tren menggabungkan nama paket yang sama di beberapa tahun', async () => {
  prisma.academicYear.findMany.mockResolvedValue([
    { id: 1, name: '2025/2026', subjectPackages: [{ id: 2, name: 'Sains', _count: { selections: 20 } }] },
    { id: 3, name: '2026/2027', subjectPackages: [{ id: 4, name: 'Sains', _count: { selections: 30 } }] },
  ]);
  const res = await request(app).get('/api/v1/admin/elective/package-trends').set('Authorization', auth());
  expect(res.status).toBe(200);
  expect(res.body.data.packages[0].totalSelections).toBe(50);
  expect(res.body.data.packages[0].byYear.map((v) => v.selections)).toEqual([20, 30]);
});
