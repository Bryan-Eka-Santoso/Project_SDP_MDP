// Tugas dari guru (F-JDW-08, F-JDW-09; docs/BUSINESS_RULES.md 2.5). Hanya pengingat: tidak ada
// pengumpulan atau penilaian, dan tanda "selesai" siswa disimpan di perangkat.
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const {
  parseDateOnly,
  formatDateOnly,
  parseTimeOfDay,
  formatTimeOfDay,
  todayLocal,
  timeLocal,
} = require('../../utils/date');
const { toPrismaPagination } = require('../../utils/pagination');
const { invalidField, activeSemesterId } = require('./schedules.service');
const { assertChildOfGuardian, invalidQuery } = require('./schedules.policy');
const { notify } = require('../notifications/notifications.service');
const recipients = require('../notifications/notifications.recipients');

const taskSelect = {
  id: true,
  title: true,
  description: true,
  dueDate: true,
  dueTime: true,
  createdAt: true,
  updatedAt: true,
  teachingAssignment: {
    select: {
      id: true,
      semesterId: true,
      teacher: { select: { id: true, user: { select: { name: true } } } },
      subject: { select: { id: true, code: true, name: true } },
      classGroup: { select: { id: true, name: true } },
    },
  },
};

const longDate = new Intl.DateTimeFormat('id-ID', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

const taskNotFound = () => new AppError(404, 'NOT_FOUND', 'Tugas tidak ditemukan.');

function toTaskDto(task) {
  const ta = task.teachingAssignment;
  return {
    id: task.id,
    title: task.title,
    description: task.description,
    dueDate: formatDateOnly(task.dueDate),
    // null = tugas lama tanpa jam; klien menganggapnya berakhir pukul 23:59.
    dueTime: task.dueTime ? formatTimeOfDay(task.dueTime) : null,
    createdAt: task.createdAt,
    updatedAt: task.updatedAt,
    teachingAssignment: {
      id: ta.id,
      teacher: { id: ta.teacher.id, name: ta.teacher.user.name },
      subject: ta.subject,
      classGroup: ta.classGroup,
    },
  };
}

// Cakupan data (docs/PERMISSIONS.md): siswa = tugas kelasnya (SELF), wali murid = tugas kelas
// anaknya (CHILD), guru = penugasan miliknya (TAUGHT).
function scopeWhere(user, studentId) {
  if (user.role === 'STUDENT') {
    return { teachingAssignment: { classGroup: { members: { some: { student: { userId: user.id } } } } } };
  }
  if (user.role === 'GUARDIAN') {
    return { teachingAssignment: { classGroup: { members: { some: { studentId } } } } };
  }
  return { teachingAssignment: { teacher: { userId: user.id } } };
}

// 404, bukan 403, agar keberadaan tugas guru lain tidak bocor.
async function loadOwnedTaskId(user, id) {
  const task = await prisma.task.findFirst({
    where: { id, teachingAssignment: { teacher: { userId: user.id } } },
    select: { id: true },
  });
  if (!task) throw taskNotFound();
}

/** Penerima: siswa kelas penugasan itu saja (BUSINESS_RULES 2.5). Tidak ditunggu. */
function sendTaskNotification(task) {
  (async () => {
    const ta = task.teachingAssignment;
    const students = await recipients.studentUserIdsOfClasses([ta.classGroup.id]);
    const due = task.dueTime
      ? `${longDate.format(task.dueDate)} pukul ${formatTimeOfDay(task.dueTime).replace(':', '.')}`
      : longDate.format(task.dueDate);
    notify(students, {
      type: 'TASK',
      title: 'Tugas baru',
      body: `${ta.subject.name} ${ta.classGroup.name}: ${task.title}. Tenggat ${due}.`,
      referenceId: task.id,
    });
  })().catch((err) => console.error('[notifikasi] gagal menyiapkan tugas baru:', err));
}

async function createTask(user, { teachingAssignmentId, title, description, dueDate, dueTime }) {
  const assignment = await prisma.teachingAssignment.findFirst({
    where: { id: teachingAssignmentId, teacher: { userId: user.id } },
    select: { id: true },
  });
  if (!assignment) throw invalidField('teachingAssignmentId', 'Penugasan mengajar tidak ditemukan.');
  if (dueDate < todayLocal()) throw invalidField('dueDate', 'Tenggat tidak boleh sebelum hari ini.');
  // Tenggat hari ini harus masih di depan jam sekarang (WIB).
  if (dueDate === todayLocal() && dueTime <= timeLocal()) {
    throw invalidField('dueTime', 'Jam tenggat harus setelah jam sekarang.');
  }

  const task = await prisma.task.create({
    data: {
      teachingAssignmentId,
      title,
      description: description ?? null,
      dueDate: parseDateOnly(dueDate),
      dueTime: parseTimeOfDay(dueTime),
    },
    select: taskSelect,
  });
  sendTaskNotification(task);
  return toTaskDto(task);
}

// Tanpa semesterId memakai semester aktif (docs/API_CONVENTIONS.md bagian 4).
async function listTasks(
  user,
  { page, limit, semesterId, teachingAssignmentId, subjectId, classGroupId, studentId, dueFrom, dueTo },
) {
  // studentId hanya bermakna untuk wali murid: anak yang tugasnya dilihat, harus anaknya sendiri.
  if (user.role === 'GUARDIAN') {
    if (!studentId) throw invalidQuery('studentId', 'Pilih anak yang tugasnya ingin dilihat.');
    await assertChildOfGuardian(user, studentId);
  } else if (studentId) {
    throw invalidQuery('studentId', 'studentId hanya untuk wali murid.');
  }

  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };

  const where = {
    AND: [
      scopeWhere(user, studentId),
      { teachingAssignment: { semesterId: periodId } },
      ...(teachingAssignmentId ? [{ teachingAssignmentId }] : []),
      ...(subjectId ? [{ teachingAssignment: { subjectId } }] : []),
      ...(classGroupId ? [{ teachingAssignment: { classGroupId } }] : []),
      ...(dueFrom ? [{ dueDate: { gte: parseDateOnly(dueFrom) } }] : []),
      ...(dueTo ? [{ dueDate: { lte: parseDateOnly(dueTo) } }] : []),
    ],
  };

  const [rows, total] = await Promise.all([
    prisma.task.findMany({
      where,
      select: taskSelect,
      orderBy: [{ dueDate: 'asc' }, { id: 'asc' }],
      ...toPrismaPagination({ page, limit }),
    }),
    prisma.task.count({ where }),
  ]);
  return { items: rows.map(toTaskDto), total };
}

async function getTask(user, id) {
  const task = await prisma.task.findFirst({ where: { AND: [{ id }, scopeWhere(user)] }, select: taskSelect });
  if (!task) throw taskNotFound();
  return toTaskDto(task);
}

// Penugasan tidak bisa dipindah. Tenggat hanya dicek saat tugas dibuat (BUSINESS_RULES 2.5).
async function updateTask(user, id, { title, description, dueDate, dueTime }) {
  await loadOwnedTaskId(user, id);
  const task = await prisma.task.update({
    where: { id },
    data: {
      ...(title !== undefined && { title }),
      ...(description !== undefined && { description }),
      ...(dueDate !== undefined && { dueDate: parseDateOnly(dueDate) }),
      ...(dueTime !== undefined && { dueTime: parseTimeOfDay(dueTime) }),
    },
    select: taskSelect,
  });
  return toTaskDto(task);
}

async function deleteTask(user, id) {
  await loadOwnedTaskId(user, id);
  await prisma.task.delete({ where: { id } });
}

/**
 * Penugasan mengajar milik guru pada semester aktif: pilihan kelas + mapel saat membuat tugas
 * (`teachingAssignmentId`). Daftar kecil dan terbatas per guru, jadi tidak berpaginasi.
 */
async function listMyTeachingAssignments(user) {
  const semesterId = await activeSemesterId();
  if (semesterId === null) return [];

  return prisma.teachingAssignment.findMany({
    where: { semesterId, teacher: { userId: user.id } },
    select: {
      id: true,
      subject: { select: { id: true, code: true, name: true } },
      classGroup: { select: { id: true, name: true } },
    },
    orderBy: [{ classGroup: { name: 'asc' } }, { subject: { name: 'asc' } }],
  });
}

module.exports = { createTask, listTasks, getTask, updateTask, deleteTask, listMyTeachingAssignments };
