const prisma = require('../../../lib/prisma');
const AppError = require('../../../utils/AppError');
const { toPrismaPagination } = require('../../../utils/pagination');
const { formatDateOnly } = require('../../../utils/date');
const { syncHomeroomTeachers } = require('../master.helper');

const CLASS_SELECT = {
  id: true,
  name: true,
  gradeLevel: true,
  capacity: true,
  createdAt: true,
  updatedAt: true,
  academicYear: {
    select: { id: true, name: true, isActive: true },
  },
  homeroomTeacher: {
    select: {
      id: true,
      employeeNumber: true,
      user: { select: { id: true, name: true, email: true } },
    },
  },
  _count: {
    select: { members: true, teachingAssignments: true },
  },
};

/**
 * F-MST-07: Mengambil daftar kelas dengan filter.
 */
async function listClasses({ page, limit, academicYearId, gradeLevel, q }) {
  const where = {};

  if (academicYearId) {
    where.academicYearId = academicYearId;
  }
  if (gradeLevel) {
    where.gradeLevel = gradeLevel;
  }
  if (q) {
    where.name = { contains: q };
  }

  const [total, items] = await Promise.all([
    prisma.classGroup.count({ where }),
    prisma.classGroup.findMany({
      where,
      select: CLASS_SELECT,
      ...toPrismaPagination({ page, limit }),
      orderBy: [{ gradeLevel: 'asc' }, { name: 'asc' }],
    }),
  ]);

  return { classes: items, total };
}

/**
 * F-MST-07: Mengambil detail satu kelas.
 */
async function getClassById(id) {
  const item = await prisma.classGroup.findUnique({
    where: { id },
    select: CLASS_SELECT,
  });

  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
  }

  return item;
}

/**
 * F-MST-07: Menambah rombongan belajar / kelas baru per tahun ajaran.
 */
async function createClass({
  academicYearId,
  name,
  gradeLevel,
  capacity,
  homeroomTeacherId,
}) {
  const year = await prisma.academicYear.findUnique({
    where: { id: academicYearId },
    select: { id: true, isActive: true },
  });
  if (!year) {
    throw new AppError(404, 'NOT_FOUND', 'Tahun ajaran tidak ditemukan.');
  }

  const duplicate = await prisma.classGroup.findUnique({
    where: {
      academicYearId_name: { academicYearId, name },
    },
  });
  if (duplicate) {
    throw new AppError(409, 'DUPLICATE', 'Nama kelas sudah digunakan pada tahun ajaran ini.', [
      { field: 'body.name', message: 'Nama kelas sudah digunakan.' },
    ]);
  }

  if (homeroomTeacherId) {
    const teacher = await prisma.teacher.findUnique({
      where: { id: homeroomTeacherId },
      select: { id: true },
    });
    if (!teacher) {
      throw new AppError(404, 'NOT_FOUND', 'Guru wali kelas tidak ditemukan.');
    }
  }

  return prisma.$transaction(async (tx) => {
    const created = await tx.classGroup.create({
      data: {
        academicYearId,
        name,
        gradeLevel,
        capacity,
        homeroomTeacherId: homeroomTeacherId || null,
      },
      select: CLASS_SELECT,
    });

    if (year.isActive && homeroomTeacherId) {
      await syncHomeroomTeachers(tx, year.id);
    }

    return created;
  });
}

/**
 * F-MST-07: Mengubah data kelas.
 */
async function updateClass(id, { name, gradeLevel, capacity }) {
  const item = await prisma.classGroup.findUnique({
    where: { id },
    include: { _count: { select: { members: true } } },
  });
  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
  }

  if (name && name !== item.name) {
    const duplicate = await prisma.classGroup.findUnique({
      where: {
        academicYearId_name: { academicYearId: item.academicYearId, name },
      },
    });
    if (duplicate) {
      throw new AppError(409, 'DUPLICATE', 'Nama kelas sudah digunakan pada tahun ajaran ini.', [
        { field: 'body.name', message: 'Nama kelas sudah digunakan.' },
      ]);
    }
  }

  if (capacity !== undefined && capacity < item._count.members) {
    throw new AppError(
      409,
      'INVALID_STATE',
      `Kapasitas baru (${capacity}) lebih kecil dari jumlah siswa yang sudah ada di kelas (${item._count.members}).`,
    );
  }

  return prisma.classGroup.update({
    where: { id },
    data: {
      ...(name !== undefined && { name }),
      ...(gradeLevel !== undefined && { gradeLevel }),
      ...(capacity !== undefined && { capacity }),
    },
    select: CLASS_SELECT,
  });
}

/**
 * F-MST-08: Mengatur wali kelas untuk suatu kelas.
 */
async function setHomeroomTeacher(classGroupId, homeroomTeacherId) {
  const classGroup = await prisma.classGroup.findUnique({
    where: { id: classGroupId },
    include: { academicYear: { select: { id: true, isActive: true } } },
  });
  if (!classGroup) {
    throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
  }

  if (homeroomTeacherId !== null) {
    const teacher = await prisma.teacher.findUnique({
      where: { id: homeroomTeacherId },
      select: { id: true },
    });
    if (!teacher) {
      throw new AppError(404, 'NOT_FOUND', 'Guru tidak ditemukan.');
    }
  }

  return prisma.$transaction(async (tx) => {
    const updated = await tx.classGroup.update({
      where: { id: classGroupId },
      data: { homeroomTeacherId },
      select: CLASS_SELECT,
    });

    // Hitung ulang penanda isHomeroomTeacher untuk guru di tahun ajaran aktif
    if (classGroup.academicYear.isActive) {
      await syncHomeroomTeachers(tx, classGroup.academicYear.id);
    }

    return updated;
  });
}

/**
 * F-MST-07: Menghapus rombel kelas.
 */
async function deleteClass(id) {
  const item = await prisma.classGroup.findUnique({
    where: { id },
    include: {
      academicYear: { select: { id: true, isActive: true } },
      _count: { select: { members: true, teachingAssignments: true } },
    },
  });
  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
  }

  if (item._count.members > 0 || item._count.teachingAssignments > 0) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Kelas tidak dapat dihapus karena sudah memiliki siswa atau penugasan mengajar.',
    );
  }

  return prisma.$transaction(async (tx) => {
    await tx.classGroup.delete({ where: { id } });
    if (item.academicYear.isActive && item.homeroomTeacherId) {
      await syncHomeroomTeachers(tx, item.academicYear.id);
    }
  });
}

// ==========================================
// F-MST-09: ANGGOTA KELAS (CLASS MEMBERS)
// ==========================================

/**
 * Mengambil daftar siswa dalam kelas.
 */
async function listClassMembers(classGroupId) {
  const classGroup = await prisma.classGroup.findUnique({
    where: { id: classGroupId },
    select: { id: true, name: true, capacity: true },
  });
  if (!classGroup) {
    throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
  }

  const members = await prisma.classMember.findMany({
    where: { classGroupId },
    include: {
      student: {
        select: {
          id: true,
          nis: true,
          birthDate: true,
          phone: true,
          user: { select: { id: true, name: true, email: true, status: true } },
        },
      },
    },
    orderBy: { student: { nis: 'asc' } },
  });

  return {
    classGroup,
    total: members.length,
    members: members.map((m) => ({
      studentId: m.student.id,
      nis: m.student.nis,
      name: m.student.user.name,
      email: m.student.user.email,
      phone: m.student.phone,
      birthDate: formatDateOnly(m.student.birthDate),
      joinedAt: m.createdAt,
    })),
  };
}

/**
 * Menempatkan siswa ke dalam kelas (bisa tunggal atau batch).
 */
async function addClassMembers(classGroupId, { studentId, studentIds }) {
  const idsToAdd = studentIds || [studentId];

  return prisma.$transaction(async (tx) => {
    const classGroup = await tx.classGroup.findUnique({
      where: { id: classGroupId },
      include: { _count: { select: { members: true } } },
    });
    if (!classGroup) {
      throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
    }

    if (classGroup._count.members + idsToAdd.length > classGroup.capacity) {
      throw new AppError(
        409,
        'INVALID_STATE',
        `Kapasitas kelas tidak mencukupi (Kapasitas: ${classGroup.capacity}, Terisi: ${classGroup._count.members}, Tambahan: ${idsToAdd.length}).`,
      );
    }

    for (const sid of idsToAdd) {
      const student = await tx.student.findUnique({
        where: { id: sid },
        select: { id: true, nis: true },
      });
      if (!student) {
        throw new AppError(404, 'NOT_FOUND', `Siswa dengan ID ${sid} tidak ditemukan.`);
      }

      // Cek apakah siswa sudah terdaftar di kelas lain pada tahun ajaran yang sama
      const alreadyInYear = await tx.classMember.findFirst({
        where: {
          studentId: sid,
          classGroup: { academicYearId: classGroup.academicYearId },
        },
        include: { classGroup: { select: { name: true } } },
      });

      if (alreadyInYear) {
        throw new AppError(
          409,
          'DUPLICATE',
          `Siswa dengan NIS ${student.nis} sudah terdaftar di kelas ${alreadyInYear.classGroup.name} pada tahun ajaran ini.`,
        );
      }

      await tx.classMember.create({
        data: {
          studentId: sid,
          classGroupId,
        },
      });
    }

    return listClassMembers(classGroupId);
  });
}

/**
 * Memindahkan siswa ke kelas lain dalam satu transaksi.
 */
async function moveClassMember(sourceClassGroupId, { studentId, targetClassGroupId }) {
  if (sourceClassGroupId === targetClassGroupId) {
    throw new AppError(400, 'VALIDATION_ERROR', 'Kelas tujuan tidak boleh sama dengan kelas asal.');
  }

  return prisma.$transaction(async (tx) => {
    const currentMembership = await tx.classMember.findUnique({
      where: {
        studentId_classGroupId: { studentId, classGroupId: sourceClassGroupId },
      },
    });
    if (!currentMembership) {
      throw new AppError(404, 'NOT_FOUND', 'Siswa tidak terdaftar di kelas asal ini.');
    }

    const targetClass = await tx.classGroup.findUnique({
      where: { id: targetClassGroupId },
      include: { _count: { select: { members: true } } },
    });
    if (!targetClass) {
      throw new AppError(404, 'NOT_FOUND', 'Kelas tujuan tidak ditemukan.');
    }

    if (targetClass._count.members >= targetClass.capacity) {
      throw new AppError(409, 'INVALID_STATE', 'Kapasitas kelas tujuan sudah penuh.');
    }

    // Hapus dari kelas asal dan tempatkan di kelas tujuan
    await tx.classMember.delete({
      where: {
        studentId_classGroupId: { studentId, classGroupId: sourceClassGroupId },
      },
    });

    await tx.classMember.create({
      data: {
        studentId,
        classGroupId: targetClassGroupId,
      },
    });

    return {
      message: `Siswa berhasil dipindahkan ke kelas ${targetClass.name}.`,
      targetClassGroupId,
    };
  });
}

/**
 * Menghapus siswa dari rombel kelas.
 */
async function removeClassMember(classGroupId, studentId) {
  const existing = await prisma.classMember.findUnique({
    where: {
      studentId_classGroupId: { studentId, classGroupId },
    },
  });
  if (!existing) {
    throw new AppError(404, 'NOT_FOUND', 'Siswa tidak terdaftar di kelas ini.');
  }

  await prisma.classMember.delete({
    where: {
      studentId_classGroupId: { studentId, classGroupId },
    },
  });
}

module.exports = {
  listClasses,
  getClassById,
  createClass,
  updateClass,
  setHomeroomTeacher,
  deleteClass,
  listClassMembers,
  addClassMembers,
  moveClassMember,
  removeClassMember,
};
