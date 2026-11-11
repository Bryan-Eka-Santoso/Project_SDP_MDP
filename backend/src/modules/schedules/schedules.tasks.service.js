// Tugas dari guru (F-JDW-08, F-JDW-09; docs/BUSINESS_RULES.md 2.5). Hanya pengingat: tidak ada
// pengumpulan atau penilaian, dan tanda "selesai" siswa disimpan di perangkat.
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { parseDateOnly, formatDateOnly, todayLocal } = require('../../utils/date');
const { toPrismaPagination } = require('../../utils/pagination');
const { invalidField, activeSemesterId } = require('./schedules.service');
const { notify } = require('../notifications/notifications.service');
const recipients = require('../notifications/notifications.recipients');

const taskSelect = {
  id: true,
  title: true,
  description: true,
  dueDate: true,
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

// Cakupan data (docs/PERMISSIONS.md): siswa = tugas kelasnya (SELF), guru = penugasan miliknya (TAUGHT).
function scopeWhere(user) {
  if (user.role === 'STUDENT') {
    return { teachingAssignment: { classGroup: { members: { some: { student: { userId: user.id } } } } } };
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
    notify(students, {
      type: 'TASK',
      title: 'Tugas baru',
      body: `${ta.subject.name} ${ta.classGroup.name}: ${task.title}. Tenggat ${longDate.format(task.dueDate)}.`,
      referenceId: task.id,
    });
  })().catch((err) => console.error('[notifikasi] gagal menyiapkan tugas baru:', err));
}

async function createTask(user, { teachingAssignmentId, title, description, dueDate }) {
  const assignment = await prisma.teachingAssignment.findFirst({
    where: { id: teachingAssignmentId, teacher: { userId: user.id } },
    select: { id: true },
  });
  if (!assignment) throw invalidField('teachingAssignmentId', 'Penugasan mengajar tidak ditemukan.');
  if (dueDate < todayLocal()) throw invalidField('dueDate', 'Tenggat tidak boleh sebelum hari ini.');

  const task = await prisma.task.create({
    data: { teachingAssignmentId, title, description: description ?? null, dueDate: parseDateOnly(dueDate) },
    select: taskSelect,
  });
  sendTaskNotification(task);
  return toTaskDto(task);
}

// Tanpa semesterId memakai semester aktif (docs/API_CONVENTIONS.md bagian 4).
async function listTasks(user, { page, limit, semesterId, teachingAssignmentId, subjectId, classGroupId, dueFrom, dueTo }) {
  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };

  const where = {
    AND: [
      scopeWhere(user),
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
async function updateTask(user, id, { title, description, dueDate }) {
  await loadOwnedTaskId(user, id);
  const task = await prisma.task.update({
    where: { id },
    data: {
      ...(title !== undefined && { title }),
      ...(description !== undefined && { description }),
      ...(dueDate !== undefined && { dueDate: parseDateOnly(dueDate) }),
    },
    select: taskSelect,
  });
  return toTaskDto(task);
}

async function deleteTask(user, id) {
  await loadOwnedTaskId(user, id);
  await prisma.task.delete({ where: { id } });
}

module.exports = { createTask, listTasks, getTask, updateTask, deleteTask };
