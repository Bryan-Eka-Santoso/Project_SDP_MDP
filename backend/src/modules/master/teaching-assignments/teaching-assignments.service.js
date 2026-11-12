const prisma = require('../../../lib/prisma');
const AppError = require('../../../utils/AppError');
const { toPrismaPagination } = require('../../../utils/pagination');

const ASSIGNMENT_SELECT = {
  id: true,
  createdAt: true,
  updatedAt: true,
  teacher: {
    select: {
      id: true,
      employeeNumber: true,
      user: { select: { id: true, name: true, email: true } },
    },
  },
  subject: {
    select: {
      id: true,
      code: true,
      name: true,
      type: true,
    },
  },
  classGroup: {
    select: {
      id: true,
      name: true,
      gradeLevel: true,
      academicYear: { select: { id: true, name: true, isActive: true } },
    },
  },
  semester: {
    select: {
      id: true,
      type: true,
      isActive: true,
    },
  },
  _count: {
    select: {
      scheduleSlots: true,
      scores: true,
      gradeComponents: true,
      tasks: true,
    },
  },
};

/**
 * F-MST-10: Mengambil daftar penugasan guru mengajar dengan filter.
 */
async function listTeachingAssignments({
  page,
  limit,
  semesterId,
  teacherId,
  classGroupId,
  subjectId,
}) {
  const where = {};

  if (semesterId) {
    where.semesterId = semesterId;
  }
  if (teacherId) {
    where.teacherId = teacherId;
  }
  if (classGroupId) {
    where.classGroupId = classGroupId;
  }
  if (subjectId) {
    where.subjectId = subjectId;
  }

  const [total, items] = await Promise.all([
    prisma.teachingAssignment.count({ where }),
    prisma.teachingAssignment.findMany({
      where,
      select: ASSIGNMENT_SELECT,
      ...toPrismaPagination({ page, limit }),
      orderBy: { id: 'desc' },
    }),
  ]);

  return { teachingAssignments: items, total };
}

/**
 * F-MST-10: Mengambil detail satu penugasan mengajar.
 */
async function getTeachingAssignmentById(id) {
  const item = await prisma.teachingAssignment.findUnique({
    where: { id },
    select: ASSIGNMENT_SELECT,
  });

  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Penugasan mengajar tidak ditemukan.');
  }

  return item;
}

/**
 * F-MST-10: Menambah penugasan guru per mata pelajaran dan kelas.
 */
async function createTeachingAssignment({
  teacherId,
  subjectId,
  classGroupId,
  semesterId,
}) {
  // 1. Verifikasi guru
  const teacher = await prisma.teacher.findUnique({
    where: { id: teacherId },
    select: { id: true },
  });
  if (!teacher) {
    throw new AppError(404, 'NOT_FOUND', 'Guru tidak ditemukan.');
  }

  // 2. Verifikasi mata pelajaran
  const subject = await prisma.subject.findUnique({
    where: { id: subjectId },
    select: { id: true },
  });
  if (!subject) {
    throw new AppError(404, 'NOT_FOUND', 'Mata pelajaran tidak ditemukan.');
  }

  // 3. Verifikasi kelas
  const classGroup = await prisma.classGroup.findUnique({
    where: { id: classGroupId },
    select: { id: true, academicYearId: true },
  });
  if (!classGroup) {
    throw new AppError(404, 'NOT_FOUND', 'Kelas tidak ditemukan.');
  }

  // 4. Verifikasi semester
  const semester = await prisma.semester.findUnique({
    where: { id: semesterId },
    select: { id: true, academicYearId: true },
  });
  if (!semester) {
    throw new AppError(404, 'NOT_FOUND', 'Semester tidak ditemukan.');
  }

  // 5. Validasi kesesuaian periode akademik: semester harus berada pada tahun ajaran yang sama dengan kelas
  if (semester.academicYearId !== classGroup.academicYearId) {
    throw new AppError(
      400,
      'VALIDATION_ERROR',
      'Semester dan kelas tidak berada pada tahun ajaran yang sama.',
    );
  }

  // 6. Cek duplikasi
  const existing = await prisma.teachingAssignment.findUnique({
    where: {
      teacherId_subjectId_classGroupId_semesterId: {
        teacherId,
        subjectId,
        classGroupId,
        semesterId,
      },
    },
  });
  if (existing) {
    throw new AppError(
      409,
      'DUPLICATE',
      'Guru sudah ditugaskan mengajar mata pelajaran ini pada kelas dan semester tersebut.',
    );
  }

  return prisma.teachingAssignment.create({
    data: {
      teacherId,
      subjectId,
      classGroupId,
      semesterId,
    },
    select: ASSIGNMENT_SELECT,
  });
}

/**
 * F-MST-10: Mengubah guru pengampu pada penugasan mengajar.
 */
async function updateTeachingAssignment(id, { teacherId }) {
  const current = await prisma.teachingAssignment.findUnique({
    where: { id },
    select: {
      id: true,
      teacherId: true,
      subjectId: true,
      classGroupId: true,
      semesterId: true,
    },
  });
  if (!current) {
    throw new AppError(404, 'NOT_FOUND', 'Penugasan mengajar tidak ditemukan.');
  }

  const teacher = await prisma.teacher.findUnique({
    where: { id: teacherId },
    select: { id: true },
  });
  if (!teacher) {
    throw new AppError(404, 'NOT_FOUND', 'Guru baru tidak ditemukan.');
  }

  if (teacherId !== current.teacherId) {
    const duplicate = await prisma.teachingAssignment.findUnique({
      where: {
        teacherId_subjectId_classGroupId_semesterId: {
          teacherId,
          subjectId: current.subjectId,
          classGroupId: current.classGroupId,
          semesterId: current.semesterId,
        },
      },
    });
    if (duplicate) {
      throw new AppError(
        409,
        'DUPLICATE',
        'Guru tersebut sudah ditugaskan mengajar mata pelajaran ini pada kelas dan semester yang sama.',
      );
    }
  }

  return prisma.teachingAssignment.update({
    where: { id },
    data: { teacherId },
    select: ASSIGNMENT_SELECT,
  });
}

/**
 * F-MST-10: Menghapus penugasan mengajar.
 */
async function deleteTeachingAssignment(id) {
  const item = await prisma.teachingAssignment.findUnique({
    where: { id },
    include: {
      _count: {
        select: {
          scheduleSlots: true,
          scores: true,
          gradeComponents: true,
          tasks: true,
          progressNotes: true,
        },
      },
    },
  });

  if (!item) {
    throw new AppError(404, 'NOT_FOUND', 'Penugasan mengajar tidak ditemukan.');
  }

  if (
    item._count.scheduleSlots > 0 ||
    item._count.scores > 0 ||
    item._count.gradeComponents > 0 ||
    item._count.tasks > 0 ||
    item._count.progressNotes > 0
  ) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Penugasan mengajar tidak dapat dihapus karena sudah memiliki jadwal, komponen nilai, nilai siswa, atau tugas.',
    );
  }

  await prisma.teachingAssignment.delete({ where: { id } });
}

module.exports = {
  listTeachingAssignments,
  getTeachingAssignmentById,
  createTeachingAssignment,
  updateTeachingAssignment,
  deleteTeachingAssignment,
};
