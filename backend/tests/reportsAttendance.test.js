jest.mock('../src/lib/prisma', () => ({
  classMember: { findMany: jest.fn() },
  attendance: { findMany: jest.fn() },
}));

const prisma = require('../src/lib/prisma');
const attendance = require('../src/modules/reports/reports.attendance');

const member = {
  classGroupId: 1,
  classGroup: { name: 'X-1' },
  student: { id: 7, nis: '001', user: { name: 'Siswa Satu' } },
};
const page = { skip: 0, take: 20 };

beforeEach(() => prisma.classMember.findMany.mockResolvedValue([member]));

test('LAP-02 filter tanpa satu pun catatan presensi menghasilkan daftar kosong', async () => {
  prisma.attendance.findMany.mockResolvedValue([]);

  await expect(
    attendance.load({ semesterId: 1, dateFrom: '2026-08-02', dateTo: '2026-08-02' }, page),
  ).resolves.toEqual({ items: [], total: 0 });
});

test('LAP-02 siswa tanpa catatan tetap tampil bila filter punya presensi', async () => {
  const other = { ...member, student: { id: 8, nis: '002', user: { name: 'Siswa Dua' } } };
  prisma.classMember.findMany.mockResolvedValue([member, other]);
  prisma.attendance.findMany.mockResolvedValue([
    { studentId: 7, status: 'PRESENT', session: { scheduleSlot: { teachingAssignment: { classGroupId: 1 } } } },
  ]);

  const result = await attendance.load({ semesterId: 1 }, page);

  expect(result.total).toBe(2);
  expect(result.items.map((row) => row.total)).toEqual([1, 0]);
});
