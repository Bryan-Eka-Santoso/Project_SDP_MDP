jest.mock('../src/lib/prisma', () => ({
  teachingAssignment: { findFirst: jest.fn() },
  classMember: { findUnique: jest.fn() },
  progressNote: {
    create: jest.fn(), update: jest.fn(), findUnique: jest.fn(),
    findMany: jest.fn(), count: jest.fn(),
  },
  student: { findFirst: jest.fn(), findUnique: jest.fn() },
  studentGuardian: { findFirst: jest.fn() },
}));

const prisma = require('../src/lib/prisma');
const service = require('../src/modules/grades/grades.progress.service');
const schemas = require('../src/modules/grades/grades.progress.validation');

const note = {
  id: 5, studentId: 8, teachingAssignmentId: 3,
  date: new Date('2026-11-12T00:00:00Z'), content: 'Aktif dalam diskusi',
  createdAt: new Date('2026-11-12T00:00:00Z'), updatedAt: new Date('2026-11-12T00:00:00Z'),
};

beforeEach(() => {
  jest.resetAllMocks();
  prisma.teachingAssignment.findFirst.mockResolvedValue({ id: 3, classGroupId: 7 });
  prisma.classMember.findUnique.mockResolvedValue({ studentId: 8 });
  prisma.progressNote.create.mockResolvedValue(note);
  prisma.progressNote.update.mockResolvedValue(note);
  prisma.progressNote.findUnique.mockResolvedValue({ id: 5, teachingAssignmentId: 3 });
  prisma.progressNote.count.mockResolvedValue(1);
  prisma.progressNote.findMany.mockResolvedValue([note]);
  prisma.student.findFirst.mockResolvedValue({ id: 8 });
  prisma.student.findUnique.mockResolvedValue({ id: 8 });
  prisma.studentGuardian.findFirst.mockResolvedValue({ studentId: 8 });
});

test('guru dapat menambahkan catatan pada siswa dalam penugasan aktif miliknya', async () => {
  const result = await service.create(42, {
    teachingAssignmentId: 3, studentId: 8, date: '2026-11-12', content: 'Aktif dalam diskusi',
  });
  expect(result.date).toBe('2026-11-12');
  expect(prisma.progressNote.create).toHaveBeenCalledWith(expect.objectContaining({
    data: expect.objectContaining({ studentId: 8, teachingAssignmentId: 3 }),
  }));
});

test('guru tidak dapat membuat catatan untuk penugasan guru lain', async () => {
  prisma.teachingAssignment.findFirst.mockResolvedValue(null);
  await expect(service.create(42, {
    teachingAssignmentId: 3, studentId: 8, date: '2026-11-12', content: 'Isi',
  })).rejects.toMatchObject({ statusCode: 404 });
  expect(prisma.progressNote.create).not.toHaveBeenCalled();
});

test('guru tidak dapat membuat catatan untuk siswa di luar kelas', async () => {
  prisma.classMember.findUnique.mockResolvedValue(null);
  await expect(service.create(42, {
    teachingAssignmentId: 3, studentId: 99, date: '2026-11-12', content: 'Isi',
  })).rejects.toMatchObject({ statusCode: 404 });
  expect(prisma.progressNote.create).not.toHaveBeenCalled();
});

test('guru tidak dapat mengubah catatan guru lain', async () => {
  prisma.teachingAssignment.findFirst.mockResolvedValue(null);
  await expect(service.update(42, 5, { content: 'Perubahan' })).rejects.toMatchObject({ statusCode: 404 });
  expect(prisma.progressNote.update).not.toHaveBeenCalled();
});

test('guru dapat mengedit isi catatan miliknya', async () => {
  await service.update(42, 5, { content: 'Perubahan' });
  expect(prisma.progressNote.update).toHaveBeenCalledWith({
    where: { id: 5 }, data: { content: 'Perubahan' },
  });
});

test('wali murid tidak dapat membaca catatan anak lain', async () => {
  prisma.studentGuardian.findFirst.mockResolvedValue(null);
  await expect(service.listByStudent({ id: 61, role: 'GUARDIAN' }, 8, { page: 1, limit: 20 }))
    .rejects.toMatchObject({ statusCode: 404 });
  expect(prisma.progressNote.findMany).not.toHaveBeenCalled();
});

test('siswa tidak dapat membaca catatan siswa lain', async () => {
  prisma.student.findFirst.mockResolvedValue(null);
  await expect(service.listByStudent({ id: 70, role: 'STUDENT' }, 8, { page: 1, limit: 20 }))
    .rejects.toMatchObject({ statusCode: 404 });
});

test('wali yang sah dapat membaca catatan anak dengan pagination', async () => {
  const result = await service.listByStudent({ id: 61, role: 'GUARDIAN' }, 8, { page: 1, limit: 20 });
  expect(result.total).toBe(1);
  expect(result.data[0].content).toBe('Aktif dalam diskusi');
  expect(prisma.progressNote.findMany).toHaveBeenCalledWith(expect.objectContaining({
    where: expect.objectContaining({ studentId: 8 }), take: 20, skip: 0,
  }));
});

test('guru memfilter daftar catatan berdasarkan kelas penugasan sendiri', async () => {
  await service.listByAssignment(42, 3, { page: 2, limit: 10, studentId: 8 });
  expect(prisma.progressNote.findMany).toHaveBeenCalledWith(expect.objectContaining({
    where: { teachingAssignmentId: 3, studentId: 8 }, skip: 10, take: 10,
  }));
});

test('validasi menolak tanggal tidak valid dan isi kosong', () => {
  expect(schemas.createBody.safeParse({ teachingAssignmentId: 3, studentId: 8,
    date: '2026-02-30', content: 'Catatan' }).success).toBe(false);
  expect(schemas.createBody.safeParse({ teachingAssignmentId: 3, studentId: 8,
    date: '2026-11-12', content: ' ' }).success).toBe(false);
  expect(schemas.updateBody.safeParse({}).success).toBe(false);
});
