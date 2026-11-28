jest.mock('../src/lib/prisma', () => ({
  student: { findUnique: jest.fn() },
  classMember: { findMany: jest.fn() },
  electiveSurvey: { findMany: jest.fn() },
}));

const prisma = require('../src/lib/prisma');
const service = require('../src/modules/elective/isi_survei.service');

beforeEach(() => {
  jest.resetAllMocks();
  prisma.student.findUnique.mockResolvedValue({ id: 3 });
  prisma.classMember.findMany.mockResolvedValue([{ classGroup: { academicYearId: 1, gradeLevel: 'X' } }]);
});

it('survei yang sudah ditutup tetap tampil (hanya baca), draf tidak', async () => {
  prisma.electiveSurvey.findMany.mockResolvedValue([{
    id: 5,
    title: 'Survei Minat',
    academicYear: { name: '2026/2027' },
    gradeLevel: 'X',
    openDate: new Date('2020-01-01T00:00:00.000Z'),
    closeDate: new Date('2099-01-01T00:00:00.000Z'),
    status: 'CLOSED',
    responses: [],
  }]);

  const result = await service.list(42);

  expect(prisma.electiveSurvey.findMany.mock.calls[0][0].where.status).toEqual({ in: ['PUBLISHED', 'CLOSED'] });
  expect(result[0]).toMatchObject({ status: 'CLOSED', canFill: false });
});
