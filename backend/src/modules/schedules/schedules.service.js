const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { parseTimeOfDay, formatTimeOfDay } = require('../../utils/date');
const { toPrismaPagination } = require('../../utils/pagination');
const { findSlotConflicts } = require('./schedules.conflicts');

const slotSelect = {
  id: true,
  dayOfWeek: true,
  startTime: true,
  endTime: true,
  room: { select: { id: true, name: true } },
  teachingAssignment: {
    select: {
      id: true,
      semesterId: true,
      teacher: { select: { id: true, userId: true, user: { select: { name: true } } } },
      subject: { select: { id: true, code: true, name: true } },
      classGroup: { select: { id: true, name: true } },
    },
  },
};

function toSlotDto(slot) {
  const ta = slot.teachingAssignment;
  return {
    id: slot.id,
    semesterId: ta.semesterId,
    dayOfWeek: slot.dayOfWeek,
    startTime: formatTimeOfDay(slot.startTime),
    endTime: formatTimeOfDay(slot.endTime),
    room: slot.room,
    teachingAssignment: {
      id: ta.id,
      teacher: { id: ta.teacher.id, name: ta.teacher.user.name },
      subject: ta.subject,
      classGroup: ta.classGroup,
    },
  };
}

// Referensi di body yang tidak valid → 400 dengan field, sama seperti format validate().
function invalidField(field, message) {
  return new AppError(400, 'VALIDATION_ERROR', 'Data yang dikirim tidak valid.', [
    { field: `body.${field}`, message },
  ]);
}

async function loadAssignment(id) {
  const assignment = await prisma.teachingAssignment.findUnique({
    where: { id },
    select: { id: true, teacherId: true, classGroupId: true, semesterId: true },
  });
  if (!assignment) throw invalidField('teachingAssignmentId', 'Penugasan mengajar tidak ditemukan.');
  return assignment;
}

async function ensureRoom(id, field = 'roomId') {
  const room = await prisma.facility.findUnique({ where: { id }, select: { type: true } });
  if (!room) throw invalidField(field, 'Ruangan tidak ditemukan.');
  if (room.type !== 'ROOM') throw invalidField(field, 'Fasilitas yang dipilih bukan ruangan.');
}

async function ensureNoConflict(slot, assignment, excludeSlotId) {
  const details = await findSlotConflicts(
    prisma,
    {
      dayOfWeek: slot.dayOfWeek,
      startTime: slot.startTime,
      endTime: slot.endTime,
      roomId: slot.roomId,
      teacherId: assignment.teacherId,
      classGroupId: assignment.classGroupId,
    },
    { semesterId: assignment.semesterId, excludeSlotId },
  );
  if (details.length > 0) {
    throw new AppError(409, 'SCHEDULE_CONFLICT', 'Jadwal bentrok dengan slot lain.', details);
  }
}

async function activeSemesterId() {
  const semester = await prisma.semester.findFirst({ where: { isActive: true }, select: { id: true } });
  return semester?.id ?? null;
}

// F-JDW-01: master jadwal untuk admin, default semester aktif.
async function listSlots({ page, limit, semesterId, classGroupId, teacherId, roomId, dayOfWeek }) {
  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };

  const where = {
    teachingAssignment: {
      semesterId: periodId,
      ...(classGroupId ? { classGroupId } : {}),
      ...(teacherId ? { teacherId } : {}),
    },
    ...(roomId ? { roomId } : {}),
    ...(dayOfWeek ? { dayOfWeek } : {}),
  };

  const [rows, total] = await Promise.all([
    prisma.scheduleSlot.findMany({
      where,
      select: slotSelect,
      // Enum MySQL diurutkan sesuai urutan deklarasi: Senin → Sabtu.
      orderBy: [{ dayOfWeek: 'asc' }, { startTime: 'asc' }, { id: 'asc' }],
      ...toPrismaPagination({ page, limit }),
    }),
    prisma.scheduleSlot.count({ where }),
  ]);
  return { items: rows.map(toSlotDto), total };
}

async function getSlot(id) {
  const slot = await prisma.scheduleSlot.findUnique({ where: { id }, select: slotSelect });
  if (!slot) throw new AppError(404, 'NOT_FOUND', 'Jadwal tidak ditemukan.');
  return toSlotDto(slot);
}

// F-JDW-01 + F-JDW-03: simpan slot baru setelah lolos cek bentrok.
async function createSlot(input) {
  const assignment = await loadAssignment(input.teachingAssignmentId);
  await ensureRoom(input.roomId);

  const data = {
    teachingAssignmentId: assignment.id,
    roomId: input.roomId,
    dayOfWeek: input.dayOfWeek,
    startTime: parseTimeOfDay(input.startTime),
    endTime: parseTimeOfDay(input.endTime),
  };
  await ensureNoConflict(data, assignment);

  const slot = await prisma.scheduleSlot.create({ data, select: slotSelect });
  return toSlotDto(slot);
}

async function updateSlot(id, input) {
  const current = await prisma.scheduleSlot.findUnique({
    where: { id },
    select: { teachingAssignmentId: true, roomId: true, dayOfWeek: true, startTime: true, endTime: true },
  });
  if (!current) throw new AppError(404, 'NOT_FOUND', 'Jadwal tidak ditemukan.');

  const data = {
    teachingAssignmentId: input.teachingAssignmentId ?? current.teachingAssignmentId,
    roomId: input.roomId ?? current.roomId,
    dayOfWeek: input.dayOfWeek ?? current.dayOfWeek,
    startTime: input.startTime ? parseTimeOfDay(input.startTime) : current.startTime,
    endTime: input.endTime ? parseTimeOfDay(input.endTime) : current.endTime,
  };
  if (data.startTime.getTime() >= data.endTime.getTime()) {
    throw invalidField('endTime', 'Jam selesai harus setelah jam mulai.');
  }

  const assignment = await loadAssignment(data.teachingAssignmentId);
  if (input.roomId !== undefined) await ensureRoom(input.roomId);
  await ensureNoConflict(data, assignment, id);

  const slot = await prisma.scheduleSlot.update({ where: { id }, data, select: slotSelect });
  return toSlotDto(slot);
}

async function deleteSlot(id) {
  const slot = await prisma.scheduleSlot.findUnique({
    where: { id },
    select: { _count: { select: { sessions: true, changes: true } } },
  });
  if (!slot) throw new AppError(404, 'NOT_FOUND', 'Jadwal tidak ditemukan.');

  // Sesi mengajar (presensi, jurnal) dan perubahan jadwal merujuk slot ini; riwayatnya tidak boleh hilang.
  if (slot._count.sessions > 0 || slot._count.changes > 0) {
    throw new AppError(
      409,
      'INVALID_STATE',
      'Jadwal tidak bisa dihapus karena sudah memiliki sesi mengajar atau perubahan jadwal.',
    );
  }
  await prisma.scheduleSlot.delete({ where: { id } });
}

module.exports = {
  listSlots,
  getSlot,
  createSlot,
  updateSlot,
  deleteSlot,
  // Dipakai ulang oleh schedules.changes.service.js
  slotSelect,
  toSlotDto,
  invalidField,
  ensureRoom,
  activeSemesterId,
};
