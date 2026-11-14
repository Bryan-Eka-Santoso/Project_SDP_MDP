const prisma = require('../../lib/prisma');
const AppError = require('../../utils/AppError');

const DAYS = {
  MONDAY: 'Senin',
  TUESDAY: 'Selasa',
  WEDNESDAY: 'Rabu',
  THURSDAY: 'Kamis',
  FRIDAY: 'Jumat',
  SATURDAY: 'Sabtu',
  SUNDAY: 'Minggu',
};
const DAY_ORDER = Object.keys(DAYS);

// Prisma @db.Time mengirim Date dengan tanggal dasar 1970-01-01.
// Ambil jam UTC langsung, bukan konversi timezone +7 jam.
function clock(value) {
  if (!value) return '--.--';
  return value.toISOString().slice(11, 16).replace(':', '.');
}

async function assignments(userId) {
  const rows = await prisma.teachingAssignment.findMany({
    where: { teacher: { userId }, semester: { isActive: true } },
    select: {
      id: true,
      classGroupId: true,
      subject: { select: { name: true } },
      classGroup: { select: { name: true } },
      semester: { select: { type: true } },
      scheduleSlots: {
        select: { id: true, dayOfWeek: true, startTime: true, endTime: true },
      },
    },
    orderBy: { id: 'asc' },
  });

  return rows.map((row) => {
    const semesterName = row.semester.type === 'ODD' ? 'Ganjil' : 'Genap';
    const schedules = (row.scheduleSlots || [])
      .map((slot) => ({
        id: slot.id,
        day: DAYS[slot.dayOfWeek] || slot.dayOfWeek,
        dayOfWeek: slot.dayOfWeek,
        startTime: clock(slot.startTime),
        endTime: clock(slot.endTime),
      }))
      .sort((a, b) => {
        const dayDiff = DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek);
        return dayDiff || a.startTime.localeCompare(b.startTime);
      });
    return {
      id: row.id,
      classGroupId: row.classGroupId,
      subjectName: row.subject.name,
      className: row.classGroup.name,
      semesterName,
      // Dipertahankan untuk kompatibilitas halaman Nilai dan Absensi lama.
      label: `${row.subject.name} - ${row.classGroup.name} (Semester ${semesterName})`,
      schedules,
    };
  });
}

async function students(userId, assignmentId) {
  const assignment = await prisma.teachingAssignment.findFirst({
    where: { id: assignmentId, teacher: { userId }, semester: { isActive: true } },
    select: { classGroupId: true },
  });
  if (!assignment) throw new AppError(404, 'NOT_FOUND', 'Penugasan tidak ditemukan.');
  const members = await prisma.classMember.findMany({
    where: { classGroupId: assignment.classGroupId },
    select: { student: { select: { id: true, nis: true, user: { select: { name: true } } } } },
    orderBy: { studentId: 'asc' },
  });
  return members.map(({ student }) => ({ id: student.id, name: student.user.name, nis: student.nis }));
}

module.exports = { assignments, students };
