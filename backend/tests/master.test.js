process.env.JWT_ACCESS_SECRET = 'test-secret-yang-panjangnya-lebih-dari-32-karakter';
process.env.JWT_ACCESS_TTL = '15m';
process.env.JWT_REFRESH_TTL = '7d';

jest.mock('../src/lib/prisma', () => {
  const db = {
    user: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    teacher: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
      delete: jest.fn(),
    },
    student: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    guardian: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    studentGuardian: {
      findUnique: jest.fn(),
      create: jest.fn(),
      delete: jest.fn(),
      deleteMany: jest.fn(),
    },
    academicYear: {
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
      delete: jest.fn(),
    },
    semester: {
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
      delete: jest.fn(),
      deleteMany: jest.fn(),
    },
    classGroup: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    classMember: {
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      delete: jest.fn(),
    },
    subject: {
      findUnique: jest.fn(),
    },
    teachingAssignment: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      count: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
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

const ADMIN_ID = 1;
const adminToken = jwt.sign({ role: 'ADMIN' }, process.env.JWT_ACCESS_SECRET, {
  algorithm: 'HS256',
  subject: String(ADMIN_ID),
  expiresIn: 900,
});

beforeEach(() => {
  for (const model of Object.values(prisma)) {
    if (typeof model === 'object' && model !== null) {
      for (const fn of Object.values(model)) {
        if (typeof fn === 'function' && fn.mockReset) fn.mockReset();
      }
    }
  }
  prisma.$transaction.mockClear();

  // Auth default: admin aktif
  prisma.user.findUnique.mockImplementation(({ where }) => {
    if (where.id === ADMIN_ID) {
      return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
    }
    return Promise.resolve(null);
  });
});

describe('F-MST-06: Master Tahun Ajaran & Semester', () => {
  it('berhasil membuat tahun ajaran baru (201)', async () => {
    prisma.academicYear.findUnique.mockResolvedValue(null);
    prisma.academicYear.create.mockResolvedValue({
      id: 1,
      name: '2026/2027',
      startDate: new Date('2026-07-01'),
      endDate: new Date('2027-06-30'),
      isActive: false,
    });

    const res = await request(app)
      .post('/api/v1/academic-years')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        name: '2026/2027',
        startDate: '2026-07-01',
        endDate: '2027-06-30',
        isActive: false,
      });

    expect(res.status).toBe(201);
    expect(res.body.data.name).toBe('2026/2027');
  });

  it('mengaktifkan tahun ajaran menonaktifkan tahun ajaran lama dan menyinkronkan status wali kelas', async () => {
    prisma.academicYear.findUnique.mockResolvedValue({ id: 2 });
    prisma.academicYear.updateMany.mockResolvedValue({ count: 1 });
    prisma.academicYear.update.mockResolvedValue({
      id: 2,
      name: '2027/2028',
      startDate: new Date('2027-07-01'),
      endDate: new Date('2028-06-30'),
      isActive: true,
    });
    prisma.classGroup.findMany.mockResolvedValue([{ homeroomTeacherId: 5 }]);
    prisma.teacher.updateMany.mockResolvedValue({ count: 1 });

    const res = await request(app)
      .patch('/api/v1/academic-years/2/active')
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(200);
    expect(prisma.academicYear.updateMany).toHaveBeenCalledWith({
      where: { isActive: true },
      data: { isActive: false },
    });
    expect(prisma.teacher.updateMany).toHaveBeenCalledWith({
      where: { id: { in: [5] } },
      data: { isHomeroomTeacher: true },
    });
  });

  it('berhasil membuat semester baru (201)', async () => {
    prisma.academicYear.findUnique.mockResolvedValue({ id: 1 });
    prisma.semester.findUnique.mockResolvedValue(null);
    prisma.semester.create.mockResolvedValue({
      id: 1,
      academicYearId: 1,
      type: 'ODD',
      startDate: new Date('2026-07-01'),
      endDate: new Date('2026-12-31'),
      isActive: true,
      academicYear: { id: 1, name: '2026/2027' },
    });

    const res = await request(app)
      .post('/api/v1/semesters')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        academicYearId: 1,
        type: 'ODD',
        startDate: '2026-07-01',
        endDate: '2026-12-31',
        isActive: true,
      });

    expect(res.status).toBe(201);
    expect(res.body.data.type).toBe('ODD');
  });
});

describe('F-MST-05: Master Guru', () => {
  it('berhasil membuat data guru baru beserta user dalam transaksi (201)', async () => {
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      return Promise.resolve(null);
    });
    prisma.teacher.findUnique.mockResolvedValue(null);
    prisma.user.create.mockResolvedValue({ id: 10 });
    prisma.teacher.create.mockResolvedValue({
      id: 2,
      employeeNumber: 'G001',
      isHomeroomTeacher: false,
      createdAt: new Date(),
      updatedAt: new Date(),
      user: { id: 10, name: 'Guru Maria', email: 'maria@test.com', status: 'ACTIVE' },
    });

    const res = await request(app)
      .post('/api/v1/teachers')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        name: 'Guru Maria',
        email: 'maria@test.com',
        password: 'password123',
        employeeNumber: 'G001',
      });

    expect(res.status).toBe(201);
    expect(res.body.data.employeeNumber).toBe('G001');
  });

  it('menolak pembuatan guru jika NIP sudah ada (409 DUPLICATE)', async () => {
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      return Promise.resolve(null);
    });
    prisma.teacher.findUnique.mockResolvedValue({ id: 99 });

    const res = await request(app)
      .post('/api/v1/teachers')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        name: 'Guru Lain',
        email: 'lain@test.com',
        password: 'password123',
        employeeNumber: 'G001',
      });

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('DUPLICATE');
  });
});

describe('F-MST-04: Master Siswa & Relasi Wali Murid', () => {
  it('berhasil membuat data siswa baru (201)', async () => {
    prisma.user.findUnique.mockImplementation(({ where }) => {
      if (where.id === ADMIN_ID) {
        return Promise.resolve({ id: ADMIN_ID, role: 'ADMIN', status: 'ACTIVE' });
      }
      return Promise.resolve(null);
    });
    prisma.student.findUnique.mockResolvedValue(null);
    prisma.user.create.mockResolvedValue({ id: 20 });
    prisma.student.create.mockResolvedValue({
      id: 5,
      nis: '26001',
      birthDate: new Date('2010-05-15'),
      phone: '08123456789',
      user: { id: 20, name: 'Siswa Budi', email: 'budi@test.com', status: 'ACTIVE' },
    });

    const res = await request(app)
      .post('/api/v1/students')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        name: 'Siswa Budi',
        email: 'budi@test.com',
        password: 'password123',
        nis: '26001',
        birthDate: '2010-05-15',
        phone: '08123456789',
      });

    expect(res.status).toBe(201);
    expect(res.body.data.nis).toBe('26001');
    expect(res.body.data.birthDate).toBe('2010-05-15');
  });

  it('berhasil menghubungkan wali murid ke siswa (201)', async () => {
    prisma.student.findUnique.mockResolvedValue({ id: 5 });
    prisma.guardian.findUnique.mockResolvedValue({ id: 3 });
    prisma.studentGuardian.findUnique.mockResolvedValue(null);
    prisma.studentGuardian.create.mockResolvedValue({
      studentId: 5,
      guardianId: 3,
      relationship: 'Ayah',
      guardian: {
        id: 3,
        user: { name: 'Wali Ayah', email: 'ayah@test.com' },
      },
    });

    const res = await request(app)
      .post('/api/v1/students/5/guardians')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        guardianId: 3,
        relationship: 'Ayah',
      });

    expect(res.status).toBe(201);
    expect(res.body.data.relationship).toBe('Ayah');
  });
});

describe('F-MST-07 & F-MST-08: Master Kelas & Atur Wali Kelas', () => {
  it('berhasil membuat kelas baru (201)', async () => {
    prisma.academicYear.findUnique.mockResolvedValue({ id: 1, isActive: true });
    prisma.classGroup.findUnique.mockResolvedValue(null);
    prisma.classGroup.create.mockResolvedValue({
      id: 1,
      name: 'X-1',
      gradeLevel: '10',
      capacity: 36,
      academicYear: { id: 1, name: '2026/2027', isActive: true },
      homeroomTeacher: null,
      _count: { members: 0, teachingAssignments: 0 },
    });

    const res = await request(app)
      .post('/api/v1/classes')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        academicYearId: 1,
        name: 'X-1',
        gradeLevel: '10',
        capacity: 36,
      });

    expect(res.status).toBe(201);
    expect(res.body.data.name).toBe('X-1');
  });

  it('berhasil mengatur wali kelas dan menyinkronkan status isHomeroomTeacher', async () => {
    prisma.classGroup.findUnique.mockResolvedValue({
      id: 1,
      academicYear: { id: 1, isActive: true },
    });
    prisma.teacher.findUnique.mockResolvedValue({ id: 8 });
    prisma.classGroup.update.mockResolvedValue({
      id: 1,
      name: 'X-1',
      homeroomTeacher: { id: 8, employeeNumber: 'G008', user: { name: 'Guru Wali' } },
    });
    prisma.classGroup.findMany.mockResolvedValue([{ homeroomTeacherId: 8 }]);
    prisma.teacher.updateMany.mockResolvedValue({ count: 1 });

    const res = await request(app)
      .patch('/api/v1/classes/1/homeroom-teacher')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ homeroomTeacherId: 8 });

    expect(res.status).toBe(200);
    expect(prisma.teacher.updateMany).toHaveBeenCalledWith({
      where: { id: { in: [8] } },
      data: { isHomeroomTeacher: true },
    });
  });
});

describe('F-MST-09: Atur Siswa dalam Kelas', () => {
  it('menolak penempatan siswa jika kapasitas kelas sudah penuh (409 INVALID_STATE)', async () => {
    prisma.classGroup.findUnique.mockResolvedValue({
      id: 1,
      academicYearId: 1,
      capacity: 36,
      _count: { members: 36 },
    });

    const res = await request(app)
      .post('/api/v1/classes/1/members')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ studentId: 10 });

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('INVALID_STATE');
  });

  it('menolak penempatan jika siswa sudah terdaftar di kelas lain pada tahun ajaran yang sama (409 DUPLICATE)', async () => {
    prisma.classGroup.findUnique.mockResolvedValue({
      id: 1,
      academicYearId: 1,
      capacity: 36,
      _count: { members: 20 },
    });
    prisma.student.findUnique.mockResolvedValue({ id: 10, nis: '26010' });
    prisma.classMember.findFirst.mockResolvedValue({
      classGroup: { name: 'X-2' },
    });

    const res = await request(app)
      .post('/api/v1/classes/1/members')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ studentId: 10 });

    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('DUPLICATE');
  });
});

describe('F-MST-10: Penugasan Guru Mengajar', () => {
  it('berhasil membuat penugasan guru mengajar (201)', async () => {
    prisma.teacher.findUnique.mockResolvedValue({ id: 2 });
    prisma.subject.findUnique.mockResolvedValue({ id: 3 });
    prisma.classGroup.findUnique.mockResolvedValue({ id: 4, academicYearId: 1 });
    prisma.semester.findUnique.mockResolvedValue({ id: 5, academicYearId: 1 });
    prisma.teachingAssignment.findUnique.mockResolvedValue(null);
    prisma.teachingAssignment.create.mockResolvedValue({
      id: 100,
      teacher: { id: 2, employeeNumber: 'G002', user: { name: 'Guru Dua' } },
      subject: { id: 3, code: 'MAT-10', name: 'Matematika Wajib' },
      classGroup: { id: 4, name: 'X-1', gradeLevel: '10' },
      semester: { id: 5, type: 'ODD', isActive: true },
    });

    const res = await request(app)
      .post('/api/v1/teaching-assignments')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        teacherId: 2,
        subjectId: 3,
        classGroupId: 4,
        semesterId: 5,
      });

    expect(res.status).toBe(201);
    expect(res.body.data.id).toBe(100);
  });

  it('menolak jika semester dan kelas tidak berada pada tahun ajaran yang sama (400 VALIDATION_ERROR)', async () => {
    prisma.teacher.findUnique.mockResolvedValue({ id: 2 });
    prisma.subject.findUnique.mockResolvedValue({ id: 3 });
    prisma.classGroup.findUnique.mockResolvedValue({ id: 4, academicYearId: 1 });
    prisma.semester.findUnique.mockResolvedValue({ id: 5, academicYearId: 2 }); // beda tahun ajaran!

    const res = await request(app)
      .post('/api/v1/teaching-assignments')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        teacherId: 2,
        subjectId: 3,
        classGroupId: 4,
        semesterId: 5,
      });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });
});
