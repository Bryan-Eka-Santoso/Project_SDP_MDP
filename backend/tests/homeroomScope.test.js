// docs/PERMISSIONS.md: wali kelas membaca nilai, presensi, dan catatan perkembangan semua mapel
// kelas binaannya; menulis tetap hanya pada penugasan miliknya.
jest.mock('../src/lib/prisma', () => ({
  teacher: { findUnique: jest.fn() },
  teachingAssignment: { findFirst: jest.fn(), findMany: jest.fn() },
  classMember: { findMany: jest.fn(), findUnique: jest.fn() },
  attendance: { findMany: jest.fn() },
  progressNote: { create: jest.fn(), findMany: jest.fn(), count: jest.fn() },
}));

const prisma = require('../src/lib/prisma');
const lookup = require('../src/modules/grades/grades.lookup.service');
const attendance = require('../src/modules/attendance/attendance.read.service');
const progress = require('../src/modules/grades/grades.progress.service');

const row = (id, teacherUserId, teacherName) => ({
  id,
  classGroupId: 7,
  subject: { name: 'Fisika' },
  classGroup: { name: 'X-1' },
  semester: { type: 'ODD' },
  teacher: { userId: teacherUserId, user: { name: teacherName } },
  scheduleSlots: [],
});
const ownOrHomeroom = [
  { teacher: { userId: 42 } },
  { classGroup: { homeroomTeacher: { userId: 42 } } },
];

beforeEach(() => {
  jest.resetAllMocks();
  prisma.teacher.findUnique.mockResolvedValue({ id: 2 });
  prisma.teachingAssignment.findFirst.mockResolvedValue({ id: 3, classGroupId: 7 });
  prisma.teachingAssignment.findMany.mockResolvedValue([]);
  prisma.classMember.findMany.mockResolvedValue([]);
  prisma.attendance.findMany.mockResolvedValue([]);
  prisma.progressNote.findMany.mockResolvedValue([]);
  prisma.progressNote.count.mockResolvedValue(0);
});

describe('daftar penugasan', () => {
  it('bawaan hanya penugasan milik sendiri (dipakai untuk input nilai dan presensi)', async () => {
    await lookup.assignments(42);

    const { where } = prisma.teachingAssignment.findMany.mock.calls[0][0];
    expect(where).toEqual({ semester: { isActive: true }, teacher: { userId: 42 } });
  });

  it('menandai penugasan di kelas perwalian guru tersebut', async () => {
    prisma.teachingAssignment.findMany.mockResolvedValue([
      { ...row(4, 77, 'Pak Budi'), classGroup: { name: 'X-1', homeroomTeacher: { userId: 42 } } },
      { ...row(5, 42, 'Bu Ana'), classGroup: { name: 'XI-2', homeroomTeacher: { userId: 90 } } },
    ]);

    const result = await lookup.assignments(42, { includeHomeroom: true });

    expect(result.map((item) => [item.id, item.isHomeroomClass])).toEqual([[4, true], [5, false]]);
  });

  it('includeHomeroom menambah semua mapel kelas binaan dan menandai pemiliknya', async () => {
    prisma.teachingAssignment.findMany.mockResolvedValue([row(3, 42, 'Bu Ana'), row(4, 77, 'Pak Budi')]);

    const result = await lookup.assignments(42, { includeHomeroom: true });

    const { where } = prisma.teachingAssignment.findMany.mock.calls[0][0];
    expect(where).toEqual({ semester: { isActive: true }, OR: ownOrHomeroom });
    expect(result.map((item) => [item.id, item.isOwn, item.teacherName])).toEqual([
      [3, true, 'Bu Ana'],
      [4, false, 'Pak Budi'],
    ]);
  });
});

describe('rekap presensi dan catatan perkembangan', () => {
  it('rekap presensi terbuka untuk pengajar dan wali kelas', async () => {
    await attendance.teacherClassSummary({ id: 42 }, 3, {});

    expect(prisma.teachingAssignment.findFirst.mock.calls[0][0].where).toEqual({
      id: 3,
      semester: { isActive: true },
      OR: [{ teacherId: 2 }, { classGroup: { homeroomTeacherId: 2 } }],
    });
  });

  it('wali kelas dapat membaca catatan perkembangan mapel lain di kelas binaan', async () => {
    await progress.listByAssignment(42, 3, { page: 1, limit: 20 });

    expect(prisma.teachingAssignment.findFirst.mock.calls[0][0].where).toEqual({
      id: 3,
      semester: { isActive: true },
      OR: ownOrHomeroom,
    });
  });

  it('daftar siswa dapat dibaca wali kelas', async () => {
    await lookup.students(42, 3);

    expect(prisma.teachingAssignment.findFirst.mock.calls[0][0].where).toEqual({
      id: 3,
      semester: { isActive: true },
      OR: ownOrHomeroom,
    });
  });

  it('menulis catatan tetap hanya untuk penugasan milik sendiri', async () => {
    prisma.teachingAssignment.findFirst.mockResolvedValue(null);

    await expect(progress.create(42, {
      teachingAssignmentId: 3, studentId: 8, date: '2026-11-12', content: 'Isi',
    })).rejects.toMatchObject({ statusCode: 404 });

    expect(prisma.teachingAssignment.findFirst.mock.calls[0][0].where).toEqual({
      id: 3, teacher: { userId: 42 }, semester: { isActive: true },
    });
  });
});
