const bcrypt = require('bcrypt');
const prisma = require('../../../lib/prisma');
const AppError = require('../../../utils/AppError');
const { toPrismaPagination } = require('../../../utils/pagination');
const { formatDateOnly } = require('../../../utils/date');

const BCRYPT_ROUNDS = 10;

function mapStudent(s) {
  if (!s) return null;
  return {
    ...s,
    birthDate: formatDateOnly(s.birthDate),
    guardians: s.guardians
      ? s.guardians.map((g) => ({
          guardianId: g.guardian.id,
          relationship: g.relationship,
          name: g.guardian.user.name,
          phone: g.guardian.phone,
          email: g.guardian.user.email,
        }))
      : undefined,
  };
}

// ==========================================
// A. SISWA (STUDENT)
// ==========================================

async function listStudents({ page, limit, q, classGroupId }) {
  const where = {};

  if (q) {
    where.OR = [
      { nis: { contains: q } },
      { user: { name: { contains: q } } },
      { user: { email: { contains: q } } },
    ];
  }

  if (classGroupId) {
    where.classMemberships = {
      some: { classGroupId },
    };
  }

  const [total, items] = await Promise.all([
    prisma.student.count({ where }),
    prisma.student.findMany({
      where,
      select: {
        id: true,
        nis: true,
        birthDate: true,
        phone: true,
        createdAt: true,
        updatedAt: true,
        user: {
          select: { id: true, name: true, email: true, status: true },
        },
        classMemberships: {
          take: 1,
          select: {
            classGroup: { select: { id: true, name: true, gradeLevel: true } },
          },
        },
      },
      ...toPrismaPagination({ page, limit }),
      orderBy: { nis: 'asc' },
    }),
  ]);

  return {
    students: items.map((s) => ({
      ...mapStudent(s),
      currentClass: s.classMemberships[0]?.classGroup || null,
    })),
    total,
  };
}

async function getStudentById(id) {
  const student = await prisma.student.findUnique({
    where: { id },
    include: {
      user: {
        select: { id: true, name: true, email: true, status: true },
      },
      guardians: {
        include: {
          guardian: {
            include: { user: { select: { name: true, email: true } } },
          },
        },
      },
      classMemberships: {
        include: {
          classGroup: {
            include: { academicYear: { select: { id: true, name: true, isActive: true } } },
          },
        },
      },
    },
  });

  if (!student) {
    throw new AppError(404, 'NOT_FOUND', 'Data siswa tidak ditemukan.');
  }

  return mapStudent(student);
}

async function createStudent({ name, email, password, nis, birthDate, phone }) {
  const existingUser = await prisma.user.findUnique({
    where: { email },
    select: { id: true },
  });
  if (existingUser) {
    throw new AppError(409, 'DUPLICATE', 'Email sudah terdaftar.', [
      { field: 'body.email', message: 'Email sudah terdaftar.' },
    ]);
  }

  const existingNis = await prisma.student.findUnique({
    where: { nis },
    select: { id: true },
  });
  if (existingNis) {
    throw new AppError(409, 'DUPLICATE', 'NIS sudah terdaftar.', [
      { field: 'body.nis', message: 'NIS sudah terdaftar.' },
    ]);
  }

  const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);

  return prisma.$transaction(async (tx) => {
    const user = await tx.user.create({
      data: {
        name,
        email,
        passwordHash,
        role: 'STUDENT',
        status: 'ACTIVE',
      },
    });

    const student = await tx.student.create({
      data: {
        userId: user.id,
        nis,
        birthDate: new Date(birthDate),
        phone,
      },
      include: {
        user: { select: { id: true, name: true, email: true, status: true } },
      },
    });

    return mapStudent(student);
  });
}

async function updateStudent(id, { name, email, nis, birthDate, phone }) {
  const student = await prisma.student.findUnique({
    where: { id },
    include: { user: { select: { id: true, email: true } } },
  });
  if (!student) {
    throw new AppError(404, 'NOT_FOUND', 'Data siswa tidak ditemukan.');
  }

  if (email && email !== student.user.email) {
    const duplicateEmail = await prisma.user.findUnique({
      where: { email },
      select: { id: true },
    });
    if (duplicateEmail) {
      throw new AppError(409, 'DUPLICATE', 'Email sudah terdaftar.', [
        { field: 'body.email', message: 'Email sudah terdaftar.' },
      ]);
    }
  }

  if (nis && nis !== student.nis) {
    const duplicateNis = await prisma.student.findUnique({
      where: { nis },
      select: { id: true },
    });
    if (duplicateNis) {
      throw new AppError(409, 'DUPLICATE', 'NIS sudah terdaftar.', [
        { field: 'body.nis', message: 'NIS sudah terdaftar.' },
      ]);
    }
  }

  return prisma.$transaction(async (tx) => {
    if (name !== undefined || email !== undefined) {
      await tx.user.update({
        where: { id: student.user.id },
        data: {
          ...(name !== undefined && { name }),
          ...(email !== undefined && { email }),
        },
      });
    }

    const updated = await tx.student.update({
      where: { id },
      data: {
        ...(nis !== undefined && { nis }),
        ...(birthDate !== undefined && { birthDate: new Date(birthDate) }),
        ...(phone !== undefined && { phone }),
      },
      include: {
        user: { select: { id: true, name: true, email: true, status: true } },
      },
    });

    return mapStudent(updated);
  });
}

async function linkGuardian(studentId, { guardianId, relationship }) {
  const student = await prisma.student.findUnique({
    where: { id: studentId },
    select: { id: true },
  });
  if (!student) {
    throw new AppError(404, 'NOT_FOUND', 'Data siswa tidak ditemukan.');
  }

  const guardian = await prisma.guardian.findUnique({
    where: { id: guardianId },
    select: { id: true },
  });
  if (!guardian) {
    throw new AppError(404, 'NOT_FOUND', 'Data wali murid tidak ditemukan.');
  }

  const existing = await prisma.studentGuardian.findUnique({
    where: {
      studentId_guardianId: { studentId, guardianId },
    },
  });
  if (existing) {
    throw new AppError(409, 'DUPLICATE', 'Wali murid sudah terhubung dengan siswa ini.');
  }

  return prisma.studentGuardian.create({
    data: {
      studentId,
      guardianId,
      relationship,
    },
    include: {
      guardian: {
        include: { user: { select: { name: true, email: true } } },
      },
    },
  });
}

async function unlinkGuardian(studentId, guardianId) {
  const existing = await prisma.studentGuardian.findUnique({
    where: {
      studentId_guardianId: { studentId, guardianId },
    },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Hubungan siswa dan wali murid tidak ditemukan.');
  }

  await prisma.studentGuardian.delete({
    where: {
      studentId_guardianId: { studentId, guardianId },
    },
  });
}

async function deleteStudent(id) {
  const student = await prisma.student.findUnique({
    where: { id },
    include: {
      _count: {
        select: {
          classMemberships: true,
          scores: true,
          attendances: true,
          violations: true,
          bills: true,
          leaveRequests: true,
        },
      },
    },
  });

  if (!student) {
    throw new AppError(404, 'NOT_FOUND', 'Data siswa tidak ditemukan.');
  }

  if (
    student._count.classMemberships > 0 ||
    student._count.scores > 0 ||
    student._count.attendances > 0 ||
    student._count.violations > 0 ||
    student._count.bills > 0 ||
    student._count.leaveRequests > 0
  ) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Data siswa tidak dapat dihapus karena sudah memiliki riwayat akademik/kehadiran/nilai. Silakan gunakan fitur blokir akun.',
    );
  }

  return prisma.$transaction(async (tx) => {
    await tx.studentGuardian.deleteMany({ where: { studentId: id } });
    await tx.student.delete({ where: { id } });
    await tx.user.delete({ where: { id: student.userId } });
  });
}

// ==========================================
// B. WALI MURID (GUARDIAN)
// ==========================================

async function listGuardians({ page, limit, q }) {
  const where = {};
  if (q) {
    where.OR = [
      { phone: { contains: q } },
      { user: { name: { contains: q } } },
      { user: { email: { contains: q } } },
    ];
  }

  const [total, items] = await Promise.all([
    prisma.guardian.count({ where }),
    prisma.guardian.findMany({
      where,
      include: {
        user: { select: { id: true, name: true, email: true, status: true } },
        _count: { select: { students: true } },
      },
      ...toPrismaPagination({ page, limit }),
      orderBy: { id: 'desc' },
    }),
  ]);

  return { guardians: items, total };
}

async function getGuardianById(id) {
  const guardian = await prisma.guardian.findUnique({
    where: { id },
    include: {
      user: { select: { id: true, name: true, email: true, status: true } },
      students: {
        include: {
          student: {
            include: { user: { select: { name: true, email: true } } },
          },
        },
      },
    },
  });

  if (!guardian) {
    throw new AppError(404, 'NOT_FOUND', 'Data wali murid tidak ditemukan.');
  }

  return {
    id: guardian.id,
    phone: guardian.phone,
    user: guardian.user,
    children: guardian.students.map((sg) => ({
      id: sg.student.id,
      nis: sg.student.nis,
      name: sg.student.user.name,
      relationship: sg.relationship,
    })),
  };
}

async function createGuardian({ name, email, password, phone }) {
  const existingUser = await prisma.user.findUnique({
    where: { email },
    select: { id: true },
  });
  if (existingUser) {
    throw new AppError(409, 'DUPLICATE', 'Email sudah terdaftar.', [
      { field: 'body.email', message: 'Email sudah terdaftar.' },
    ]);
  }

  const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);

  return prisma.$transaction(async (tx) => {
    const user = await tx.user.create({
      data: {
        name,
        email,
        passwordHash,
        role: 'GUARDIAN',
        status: 'ACTIVE',
      },
    });

    return tx.guardian.create({
      data: {
        userId: user.id,
        phone,
      },
      include: {
        user: { select: { id: true, name: true, email: true, status: true } },
      },
    });
  });
}

async function updateGuardian(id, { name, email, phone }) {
  const guardian = await prisma.guardian.findUnique({
    where: { id },
    include: { user: { select: { id: true, email: true } } },
  });
  if (!guardian) {
    throw new AppError(404, 'NOT_FOUND', 'Data wali murid tidak ditemukan.');
  }

  if (email && email !== guardian.user.email) {
    const duplicateEmail = await prisma.user.findUnique({
      where: { email },
      select: { id: true },
    });
    if (duplicateEmail) {
      throw new AppError(409, 'DUPLICATE', 'Email sudah terdaftar.', [
        { field: 'body.email', message: 'Email sudah terdaftar.' },
      ]);
    }
  }

  return prisma.$transaction(async (tx) => {
    if (name !== undefined || email !== undefined) {
      await tx.user.update({
        where: { id: guardian.user.id },
        data: {
          ...(name !== undefined && { name }),
          ...(email !== undefined && { email }),
        },
      });
    }

    return tx.guardian.update({
      where: { id },
      data: {
        ...(phone !== undefined && { phone }),
      },
      include: {
        user: { select: { id: true, name: true, email: true, status: true } },
      },
    });
  });
}

async function deleteGuardian(id) {
  const guardian = await prisma.guardian.findUnique({
    where: { id },
    include: {
      _count: { select: { students: true, leaveRequests: true } },
    },
  });
  if (!guardian) {
    throw new AppError(404, 'NOT_FOUND', 'Data wali murid tidak ditemukan.');
  }

  if (guardian._count.students > 0 || guardian._count.leaveRequests > 0) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Data wali murid tidak dapat dihapus karena masih terhubung ke data siswa atau pengajuan izin.',
    );
  }

  return prisma.$transaction(async (tx) => {
    await tx.guardian.delete({ where: { id } });
    await tx.user.delete({ where: { id: guardian.userId } });
  });
}

module.exports = {
  listStudents,
  getStudentById,
  createStudent,
  updateStudent,
  linkGuardian,
  unlinkGuardian,
  deleteStudent,
  listGuardians,
  getGuardianById,
  createGuardian,
  updateGuardian,
  deleteGuardian,
};
