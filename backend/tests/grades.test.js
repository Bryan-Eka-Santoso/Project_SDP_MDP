jest.mock('../src/lib/prisma', () => ({
  teachingAssignment: { findFirst: jest.fn() },
  gradeComponent: { findMany: jest.fn(), aggregate: jest.fn(), create: jest.fn(), findUnique: jest.fn(), update: jest.fn() },
  classMember: { findUnique: jest.fn() },
  score: { upsert: jest.fn(), findMany: jest.fn(), count: jest.fn() },
}));

const prisma = require('../src/lib/prisma');
const service = require('../src/modules/grades/grades.service');

beforeEach(() => {
  jest.clearAllMocks();
  prisma.teachingAssignment.findFirst.mockResolvedValue({ id: 3, classGroupId: 7, semesterId: 1 });
  prisma.gradeComponent.findMany.mockResolvedValue([{ id: 9, weight: 60 }, { id: 10, weight: 40 }]);
  prisma.classMember.findUnique.mockResolvedValue({ studentId: 8 });
});

test('guru tidak dapat membaca komponen penugasan yang bukan miliknya', async () => {
  prisma.teachingAssignment.findFirst.mockResolvedValue(null);
  await expect(service.listComponents(42, 3)).rejects.toMatchObject({ statusCode: 404 });
});

test('menolak input nilai jika bobot belum tepat 100', async () => {
  prisma.gradeComponent.findMany.mockResolvedValue([{ id: 9, weight: 60 }]);
  await expect(service.saveScore(42, {
    teachingAssignmentId: 3, gradeComponentId: 9, studentId: 8, value: 80,
  })).rejects.toMatchObject({ statusCode: 409, code: 'GRADE_WEIGHTS_INVALID' });
  expect(prisma.score.upsert).not.toHaveBeenCalled();
});

test('menolak nilai siswa di luar kelas penugasan', async () => {
  prisma.classMember.findUnique.mockResolvedValue(null);
  await expect(service.saveScore(42, {
    teachingAssignmentId: 3, gradeComponentId: 9, studentId: 99, value: 80,
  })).rejects.toMatchObject({ statusCode: 404 });
});

test('input ulang skor memakai upsert kunci siswa dan komponen', async () => {
  prisma.score.upsert.mockResolvedValue({
    id: 11, studentId: 8, teachingAssignmentId: 3, gradeComponentId: 9,
    value: { toString: () => '79.5' }, inputDate: new Date('2026-11-11T00:00:00Z'),
  });
  const result = await service.saveScore(42, {
    teachingAssignmentId: 3, gradeComponentId: 9, studentId: 8, value: 79.5,
  });
  expect(prisma.score.upsert).toHaveBeenCalledWith(expect.objectContaining({
    where: { studentId_gradeComponentId: { studentId: 8, gradeComponentId: 9 } },
  }));
  expect(result.value).toBe(79.5);
  expect(result.inputDate).toBe('2026-11-11');
});

test('menolak komponen baru jika jumlah bobot melebihi 100%', async () => {
  prisma.gradeComponent.aggregate.mockResolvedValue({ _sum: { weight: 90 } });
  await expect(service.createComponent(42, 3, { name: 'UAS', weight: 20 })).rejects.toMatchObject({ statusCode: 409 });
  expect(prisma.gradeComponent.create).not.toHaveBeenCalled();
});
