// Perubahan jadwal per tanggal (F-JDW-04, docs/BUSINESS_RULES.md 2.4, ADR-006).
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { DAYS_OF_WEEK, DAY_LABELS } = require('../../config/constants');
const { parseDateOnly, formatDateOnly, parseTimeOfDay, formatTimeOfDay } = require('../../utils/date');
const { toPrismaPagination } = require('../../utils/pagination');
const { findConflictsOnDate } = require('./schedules.conflicts');
const { slotSelect, toSlotDto, invalidField, ensureRoom, activeSemesterId } = require('./schedules.service');
const { notify } = require('../notifications/notifications.service');
const recipients = require('../notifications/notifications.recipients');

const changeSelect = {
  id: true,
  scheduleSlotId: true,
  type: true,
  date: true,
  substituteTeacher: { select: { id: true, userId: true, user: { select: { name: true } } } },
  newRoom: { select: { id: true, name: true } },
  newStartTime: true,
  newEndTime: true,
  description: true,
  createdAt: true,
  updatedAt: true,
  scheduleSlot: { select: slotSelect },
};

function toChangeDto(change) {
  const sub = change.substituteTeacher;
  return {
    id: change.id,
    scheduleSlotId: change.scheduleSlotId,
    date: formatDateOnly(change.date),
    type: change.type,
    substituteTeacher: sub ? { id: sub.id, name: sub.user.name } : null,
    newRoom: change.newRoom,
    newStartTime: change.newStartTime ? formatTimeOfDay(change.newStartTime) : null,
    newEndTime: change.newEndTime ? formatTimeOfDay(change.newEndTime) : null,
    description: change.description,
    createdAt: change.createdAt,
    updatedAt: change.updatedAt,
    slot: toSlotDto(change.scheduleSlot),
  };
}

// getUTCDay(): 0 = Minggu. Kolom @db.Date disimpan sebagai UTC tengah malam.
function dayOfWeekOf(date) {
  return DAYS_OF_WEEK[date.getUTCDay() - 1] ?? null;
}

const longDate = new Intl.DateTimeFormat('id-ID', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

async function loadSlot(id) {
  const slot = await prisma.scheduleSlot.findUnique({
    where: { id },
    select: {
      id: true,
      dayOfWeek: true,
      startTime: true,
      endTime: true,
      roomId: true,
      room: { select: { name: true } },
      teachingAssignment: {
        select: {
          semesterId: true,
          teacherId: true,
          classGroupId: true,
          teacher: { select: { userId: true } },
          subject: { select: { name: true } },
          classGroup: { select: { name: true } },
          semester: { select: { startDate: true, endDate: true } },
        },
      },
    },
  });
  if (!slot) throw invalidField('scheduleSlotId', 'Jadwal tidak ditemukan.');
  return slot;
}

function ensureDateMatchesSlot(date, slot) {
  const { semester } = slot.teachingAssignment;
  if (date < semester.startDate || date > semester.endDate) {
    throw invalidField('date', 'Tanggal berada di luar semester jadwal ini.');
  }
  if (dayOfWeekOf(date) !== slot.dayOfWeek) {
    throw invalidField(
      'date',
      `Tanggal tersebut bukan hari ${DAY_LABELS[slot.dayOfWeek]}, sesuai hari jadwal ini.`,
    );
  }
}

async function loadSubstitute(id, slot) {
  if (id === slot.teachingAssignment.teacherId) {
    throw invalidField('substituteTeacherId', 'Guru pengganti tidak boleh sama dengan guru asli.');
  }
  const teacher = await prisma.teacher.findUnique({
    where: { id },
    select: { id: true, user: { select: { name: true, status: true } } },
  });
  if (!teacher) throw invalidField('substituteTeacherId', 'Guru pengganti tidak ditemukan.');
  if (teacher.user.status !== 'ACTIVE') {
    throw invalidField('substituteTeacherId', 'Akun guru pengganti sedang diblokir.');
  }
  return teacher;
}

// Data kolom perubahan sesuai jenis; kolom yang tidak relevan dikosongkan agar update tidak
// menyisakan nilai dari jenis sebelumnya.
function changeColumns(input) {
  return {
    type: input.type,
    description: input.description,
    substituteTeacherId: input.type === 'SUBSTITUTE' ? input.substituteTeacherId : null,
    newRoomId: input.type === 'RESCHEDULE' ? (input.newRoomId ?? null) : null,
    newStartTime: input.type === 'RESCHEDULE' && input.newStartTime ? parseTimeOfDay(input.newStartTime) : null,
    newEndTime: input.type === 'RESCHEDULE' && input.newEndTime ? parseTimeOfDay(input.newEndTime) : null,
  };
}

async function ensureNoConflictOnDate(slot, columns, date) {
  if (columns.type === 'CANCEL') return;
  const ta = slot.teachingAssignment;
  const candidate = {
    dayOfWeek: slot.dayOfWeek,
    startTime: columns.newStartTime ?? slot.startTime,
    endTime: columns.newEndTime ?? slot.endTime,
    roomId: columns.newRoomId ?? slot.roomId,
    teacherId: columns.substituteTeacherId ?? ta.teacherId,
    classGroupId: ta.classGroupId,
  };
  const details = await findConflictsOnDate(prisma, candidate, {
    semesterId: ta.semesterId,
    date,
    excludeSlotId: slot.id,
  });
  if (details.length > 0) {
    throw new AppError(409, 'SCHEDULE_CONFLICT', 'Perubahan jadwal bentrok dengan jadwal lain di tanggal itu.', details);
  }
}

function describeChange(change) {
  switch (change.type) {
    case 'CANCEL':
      return 'dibatalkan';
    case 'SUBSTITUTE':
      return `diajar guru pengganti ${change.substituteTeacher.user.name}`;
    default: {
      const slot = change.scheduleSlot;
      const start = formatTimeOfDay(change.newStartTime ?? slot.startTime);
      const end = formatTimeOfDay(change.newEndTime ?? slot.endTime);
      const room = change.newRoom?.name ?? slot.room.name;
      return `pindah ke ${start}–${end} di ${room}`;
    }
  }
}

/**
 * Penerima: siswa kelas, wali murid mereka, guru asli, guru pengganti (BUSINESS_RULES 2.4),
 * ditambah guru pengganti sebelumnya jika ia diganti/dihapus agar tidak datang sia-sia.
 * Tidak ditunggu: kegagalan mencari penerima hanya dicatat di log, data sudah tersimpan.
 */
function sendChangeNotification({ classGroupId, teacherUserIds, change, detail, referenceId }) {
  (async () => {
    const [students, guardians] = await Promise.all([
      recipients.studentUserIdsOfClasses([classGroupId]),
      recipients.guardianUserIdsOfClasses([classGroupId]),
    ]);
    const ta = change.scheduleSlot.teachingAssignment;
    const body =
      `${ta.subject.name} ${ta.classGroup.name}, ${longDate.format(change.date)}: ${detail}.` +
      (change.description ? ` ${change.description}` : '');
    notify([...students, ...guardians, ...teacherUserIds.filter(Boolean)], {
      type: 'SCHEDULE_CHANGE',
      title: 'Perubahan jadwal',
      body,
      referenceId,
    });
  })().catch((err) => console.error('[notifikasi] gagal menyiapkan perubahan jadwal:', err));
}

/**
 * Simpan perubahan untuk satu slot pada satu tanggal. Jika sudah ada perubahan di tanggal
 * itu, perubahan tersebut diperbarui (maksimal satu per slot per tanggal).
 * Mengembalikan `{ change, created }`.
 */
async function saveChange(input) {
  const slot = await loadSlot(input.scheduleSlotId);
  const date = parseDateOnly(input.date);
  ensureDateMatchesSlot(date, slot);

  if (input.type === 'SUBSTITUTE') await loadSubstitute(input.substituteTeacherId, slot);
  if (input.type === 'RESCHEDULE' && input.newRoomId !== undefined) {
    await ensureRoom(input.newRoomId, 'newRoomId');
  }

  const columns = changeColumns(input);
  if (columns.type === 'RESCHEDULE') {
    const sameTime =
      !columns.newStartTime ||
      (columns.newStartTime.getTime() === slot.startTime.getTime() &&
        columns.newEndTime.getTime() === slot.endTime.getTime());
    const sameRoom = !columns.newRoomId || columns.newRoomId === slot.roomId;
    if (sameTime && sameRoom) {
      throw invalidField('newRoomId', 'Jam dan ruangan baru sama dengan jadwal asli.');
    }
  }
  await ensureNoConflictOnDate(slot, columns, date);

  const key = { scheduleSlotId_date: { scheduleSlotId: slot.id, date } };
  const previous = await prisma.scheduleChange.findUnique({
    where: key,
    select: { substituteTeacher: { select: { userId: true } } },
  });

  const change = await prisma.scheduleChange.upsert({
    where: key,
    create: { scheduleSlotId: slot.id, date, ...columns },
    update: columns,
    select: changeSelect,
  });

  sendChangeNotification({
    classGroupId: slot.teachingAssignment.classGroupId,
    teacherUserIds: [
      slot.teachingAssignment.teacher.userId,
      change.substituteTeacher?.userId,
      previous?.substituteTeacher?.userId,
    ],
    change,
    detail: describeChange(change),
    referenceId: change.id,
  });

  return { change: toChangeDto(change), created: previous === null };
}

async function listChanges({ page, limit, semesterId, classGroupId, teacherId, type, dateFrom, dateTo }) {
  const periodId = semesterId ?? (await activeSemesterId());
  if (periodId === null) return { items: [], total: 0 };

  const where = {
    AND: [
      { scheduleSlot: { teachingAssignment: { semesterId: periodId } } },
      ...(classGroupId ? [{ scheduleSlot: { teachingAssignment: { classGroupId } } }] : []),
      ...(teacherId
        ? [{ OR: [{ scheduleSlot: { teachingAssignment: { teacherId } } }, { substituteTeacherId: teacherId }] }]
        : []),
      ...(type ? [{ type }] : []),
      ...(dateFrom ? [{ date: { gte: parseDateOnly(dateFrom) } }] : []),
      ...(dateTo ? [{ date: { lte: parseDateOnly(dateTo) } }] : []),
    ],
  };

  const [rows, total] = await Promise.all([
    prisma.scheduleChange.findMany({
      where,
      select: changeSelect,
      orderBy: [{ date: 'desc' }, { id: 'desc' }],
      ...toPrismaPagination({ page, limit }),
    }),
    prisma.scheduleChange.count({ where }),
  ]);
  return { items: rows.map(toChangeDto), total };
}

async function getChange(id) {
  const change = await prisma.scheduleChange.findUnique({ where: { id }, select: changeSelect });
  if (!change) throw new AppError(404, 'NOT_FOUND', 'Perubahan jadwal tidak ditemukan.');
  return toChangeDto(change);
}

// Menghapus perubahan = jadwal tanggal itu kembali seperti slot asli.
async function deleteChange(id) {
  const change = await prisma.scheduleChange.findUnique({ where: { id }, select: changeSelect });
  if (!change) throw new AppError(404, 'NOT_FOUND', 'Perubahan jadwal tidak ditemukan.');

  await prisma.scheduleChange.delete({ where: { id } });

  const ta = change.scheduleSlot.teachingAssignment;
  sendChangeNotification({
    classGroupId: ta.classGroup.id,
    teacherUserIds: [ta.teacher.userId, change.substituteTeacher?.userId],
    change: { ...change, description: '' },
    detail: 'kembali sesuai jadwal biasa',
    // Data perubahan sudah dihapus; Android cukup memuat ulang jadwal (ARCHITECTURE.md bagian 5).
    referenceId: change.id,
  });
}

module.exports = { saveChange, listChanges, getChange, deleteChange, dayOfWeekOf };
