// Tampilan jadwal sesuai role (F-JDW-05, docs/BUSINESS_RULES.md 2.4: "slot asli yang ditimpa
// oleh perubahan pada tanggal itu").
const prisma = require('../../lib/prisma');
const { parseDateOnly, formatDateOnly, formatTimeOfDay, todayLocal } = require('../../utils/date');
const { slotSelect } = require('./schedules.service');
const { dayOfWeekOf } = require('./schedules.changes.service');
const { resolveViewScope } = require('./schedules.policy');

const DAY_MS = 24 * 60 * 60 * 1000;
const DEFAULT_DAYS = 7;

// slotSelect ditambah rentang semester dan perubahan pada rentang tanggal yang diminta.
function viewSelect(from, to) {
  const assignment = slotSelect.teachingAssignment.select;
  return {
    ...slotSelect,
    teachingAssignment: {
      select: { ...assignment, semester: { select: { startDate: true, endDate: true } } },
    },
    changes: {
      where: { date: { gte: from, lte: to } },
      select: {
        id: true,
        type: true,
        date: true,
        description: true,
        substituteTeacher: { select: { id: true, userId: true, user: { select: { name: true } } } },
        newRoom: { select: { id: true, name: true } },
        newStartTime: true,
        newEndTime: true,
      },
    },
  };
}

function resolveRange({ dateFrom, dateTo }) {
  const from = parseDateOnly(dateFrom ?? todayLocal());
  const to = dateTo ? parseDateOnly(dateTo) : new Date(from.getTime() + (DEFAULT_DAYS - 1) * DAY_MS);
  return { from, to };
}

// Siapa yang mengajar pada hari itu setelah perubahan; `original` hanya dibawa jika ada perubahan.
function toEntry(slot, date, change) {
  const ta = slot.teachingAssignment;
  const original = {
    startTime: formatTimeOfDay(slot.startTime),
    endTime: formatTimeOfDay(slot.endTime),
    room: slot.room,
    teacher: { id: ta.teacher.id, name: ta.teacher.user.name },
  };

  const entry = {
    date: formatDateOnly(date),
    slotId: slot.id,
    dayOfWeek: slot.dayOfWeek,
    ...original,
    subject: ta.subject,
    classGroup: ta.classGroup,
    change: null,
  };
  if (!change) return entry;

  entry.change = { id: change.id, type: change.type, description: change.description };
  entry.original = original;
  if (change.type === 'SUBSTITUTE') {
    entry.teacher = { id: change.substituteTeacher.id, name: change.substituteTeacher.user.name };
  }
  if (change.type === 'RESCHEDULE') {
    if (change.newRoom) entry.room = change.newRoom;
    if (change.newStartTime) {
      entry.startTime = formatTimeOfDay(change.newStartTime);
      entry.endTime = formatTimeOfDay(change.newEndTime);
    }
  }
  return entry;
}

// Guru melihat jadwal mengajarnya: slot miliknya (termasuk yang digantikan orang lain pada
// tanggal itu) dan slot guru lain yang ia gantikan pada tanggal itu.
function teacherSees(slot, change, userId) {
  if (slot.teachingAssignment.teacher.userId === userId) return true;
  return change?.type === 'SUBSTITUTE' && change.substituteTeacher.userId === userId;
}

/**
 * Jadwal pada rentang tanggal untuk `user`, urut tanggal lalu jam. Satu entri per slot per
 * tanggal; entri yang dibatalkan tetap ditampilkan dengan `change.type = CANCEL`.
 */
async function viewSchedule(user, query) {
  const scope = await resolveViewScope(user, query);
  const { from, to } = resolveRange(query);

  const assignmentWhere = {
    semester: { startDate: { lte: to }, endDate: { gte: from } },
    ...scope.classWhere && { classGroup: scope.classWhere },
  };
  const where = scope.teacherUserId
    ? {
        teachingAssignment: assignmentWhere,
        OR: [
          { teachingAssignment: { teacher: { userId: scope.teacherUserId } } },
          { changes: { some: { date: { gte: from, lte: to }, substituteTeacher: { userId: scope.teacherUserId } } } },
        ],
      }
    : { teachingAssignment: assignmentWhere };

  const slots = await prisma.scheduleSlot.findMany({ where, select: viewSelect(from, to) });

  const entries = [];
  for (let time = from.getTime(); time <= to.getTime(); time += DAY_MS) {
    const date = new Date(time);
    const day = dayOfWeekOf(date);
    for (const slot of slots) {
      if (slot.dayOfWeek !== day) continue;
      const { semester } = slot.teachingAssignment;
      if (date < semester.startDate || date > semester.endDate) continue;

      const change = slot.changes.find((item) => item.date.getTime() === time) ?? null;
      if (scope.teacherUserId && !teacherSees(slot, change, scope.teacherUserId)) continue;
      entries.push(toEntry(slot, date, change));
    }
  }

  return entries.sort(
    (a, b) =>
      a.date.localeCompare(b.date) || a.startTime.localeCompare(b.startTime) || a.slotId - b.slotId,
  );
}

module.exports = { viewSchedule };
