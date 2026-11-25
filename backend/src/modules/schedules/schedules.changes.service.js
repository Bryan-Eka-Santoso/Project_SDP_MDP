// Perubahan jadwal per tanggal (F-JDW-04, docs/BUSINESS_RULES.md 2.4, ADR-006).
const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');
const { DAYS_OF_WEEK, DAY_LABELS } = require('../../config/constants');
const {
  parseDateOnly,
  formatDateOnly,
  parseTimeOfDay,
  formatTimeOfDay,
  todayLocal,
} = require('../../utils/date');
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
  newDate: true,
  description: true,
  createdAt: true,
  updatedAt: true,
  scheduleSlot: { select: slotSelect },
};

const forbidden = (message) => new AppError(403, 'FORBIDDEN', message);
const invalidState = (message) => new AppError(409, 'INVALID_STATE', message);

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
    // Tanggal tujuan bila pertemuan dipindah ke hari lain; null = tetap di `date`.
    newDate: change.newDate ? formatDateOnly(change.newDate) : null,
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

// Tanggal tujuan pindah lintas hari (BUSINESS_RULES 2.4). Hari jadwal itu sendiri ditolak agar
// satu slot tidak punya dua pertemuan pada tanggal yang sama.
function ensureNewDateAllowed(newDate, slot) {
  const { semester } = slot.teachingAssignment;
  if (newDate < semester.startDate || newDate > semester.endDate) {
    throw invalidField('newDate', 'Tanggal baru berada di luar semester jadwal ini.');
  }
  const day = dayOfWeekOf(newDate);
  if (day === null) {
    throw invalidField('newDate', 'Tanggal baru jatuh pada hari Minggu, bukan hari sekolah.');
  }
  if (day === slot.dayOfWeek) {
    throw invalidField(
      'newDate',
      `Tanggal baru juga hari ${DAY_LABELS[day]}, hari jadwal ini sendiri. Pilih hari lain atau ubah jamnya saja.`,
    );
  }
}

/**
 * Guru hanya boleh memindah (RESCHEDULE) pertemuan dari slot mengajarnya sendiri yang belum
 * lewat; guru pengganti dan pembatalan tetap wewenang admin (docs/PERMISSIONS.md).
 */
function ensureTeacherMayChange(user, slot, type) {
  if (user.role !== 'TEACHER') return;
  if (slot.teachingAssignment.teacher.userId !== user.id) {
    throw forbidden('Anda hanya dapat mengubah jadwal mengajar Anda sendiri.');
  }
  if (type === 'SUBSTITUTE') {
    throw forbidden('Guru hanya dapat memindah jadwal atau membatalkan kelas. Guru pengganti diatur admin.');
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
    // Sama dengan tanggal asal = tidak pindah tanggal.
    newDate:
      input.type === 'RESCHEDULE' && input.newDate && input.newDate !== input.date
        ? parseDateOnly(input.newDate)
        : null,
  };
}

// Bentrok dicek pada tanggal pertemuan itu benar-benar berlangsung (tanggal baru bila dipindah).
async function ensureNoConflictOnDate(slot, columns, originalDate) {
  if (columns.type === 'CANCEL') return;
  const date = columns.newDate ?? originalDate;
  const ta = slot.teachingAssignment;
  const candidate = {
    dayOfWeek: dayOfWeekOf(date),
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
      const day = change.newDate ? `${longDate.format(change.newDate)} ` : '';
      return `pindah ke ${day}${start}–${end} di ${room}`;
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
 * `user` = pelaku (admin, atau guru pemilik slot yang hanya boleh RESCHEDULE dan CANCEL).
 * Mengembalikan `{ change, created }`.
 */
async function saveChange(user, input) {
  const slot = await loadSlot(input.scheduleSlotId);
  ensureTeacherMayChange(user, slot, input.type);
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
    if (sameTime && sameRoom && !columns.newDate) {
      throw invalidField('newRoomId', 'Tanggal, jam, dan ruangan baru sama dengan jadwal asli.');
    }
  }
  if (columns.newDate) ensureNewDateAllowed(columns.newDate, slot);

  const key = { scheduleSlotId_date: { scheduleSlotId: slot.id, date } };
  const previous = await prisma.scheduleChange.findUnique({
    where: key,
    select: { type: true, newDate: true, substituteTeacher: { select: { userId: true } } },
  });
  if (user.role === 'TEACHER') {
    // Guru tidak boleh menimpa guru pengganti yang ditetapkan admin. Pemindahan dan pembatalan
    // boleh ditimpa karena tidak tercatat siapa pembuatnya dan guru berhak atas kelasnya sendiri.
    if (previous && previous.type === 'SUBSTITUTE') {
      throw invalidState('Pertemuan ini sudah diganti guru oleh admin. Hubungi admin.');
    }
    // Yang dinilai adalah kapan pertemuan itu berlangsung sekarang: tanggal pindahannya bila ada.
    const today = parseDateOnly(todayLocal());
    if ((previous?.newDate ?? date) < today) {
      throw invalidField('date', 'Pertemuan yang sudah lewat tidak dapat dipindah.');
    }
    if (columns.newDate && columns.newDate < today) {
      throw invalidField('newDate', 'Tanggal baru tidak boleh sebelum hari ini.');
    }
  }
  if (columns.newDate) {
    // Satu slot hanya boleh punya satu pertemuan pada satu tanggal.
    const sameTarget = await prisma.scheduleChange.findFirst({
      where: { scheduleSlotId: slot.id, newDate: columns.newDate, date: { not: date } },
      select: { id: true },
    });
    if (sameTarget) throw invalidState('Pertemuan lain dari jadwal ini sudah dipindah ke tanggal tersebut.');
  }
  await ensureNoConflictOnDate(slot, columns, date);

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

// Menghapus perubahan = jadwal tanggal itu kembali seperti slot asli. Guru hanya boleh
// menghapus pemindahan atau pembatalan pada slot miliknya yang belum berlangsung.
async function deleteChange(user, id) {
  const change = await prisma.scheduleChange.findUnique({ where: { id }, select: changeSelect });
  if (!change) throw new AppError(404, 'NOT_FOUND', 'Perubahan jadwal tidak ditemukan.');
  if (user.role === 'TEACHER') {
    if (change.scheduleSlot.teachingAssignment.teacher.userId !== user.id) {
      throw forbidden('Anda hanya dapat mengubah jadwal mengajar Anda sendiri.');
    }
    if (change.type === 'SUBSTITUTE') {
      throw forbidden('Guru pengganti ditetapkan admin. Hubungi admin untuk membatalkannya.');
    }
    if ((change.newDate ?? change.date) < parseDateOnly(todayLocal())) {
      throw invalidState('Perubahan jadwal yang sudah berlangsung tidak dapat dibatalkan.');
    }
  }

  // Saat memulihkan jadwal yang dibatalkan/dipindah, pastikan slot asli tidak bentrok dengan jadwal lain di tanggal asal.
  if (change.type === 'CANCEL' || change.type === 'RESCHEDULE') {
    const slot = change.scheduleSlot;
    const ta = slot.teachingAssignment;
    const details = await findConflictsOnDate(
      prisma,
      {
        dayOfWeek: dayOfWeekOf(change.date),
        startTime: slot.startTime,
        endTime: slot.endTime,
        roomId: slot.room.id,
        teacherId: ta.teacher.id,
        classGroupId: ta.classGroup.id,
      },
      { semesterId: ta.semesterId, date: change.date, excludeSlotId: slot.id },
    );
    if (details.length > 0) {
      throw new AppError(
        409,
        'SCHEDULE_CONFLICT',
        'Jadwal asli tidak dapat dipulihkan karena bentrok dengan jadwal lain pada tanggal tersebut.',
        details,
      );
    }
  }

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
