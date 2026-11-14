const { z } = require('zod');
const { DAYS_OF_WEEK } = require('../../config/constants');
const { TIME_OF_DAY, parseDateOnly } = require('../../utils/date');
const { paginationQuery } = require('../../utils/pagination');

const id = z.coerce.number().int().positive();
// Format 'HH:mm' (00:00–23:59) sehingga perbandingan string sama dengan perbandingan jam.
const timeOfDay = z.string().regex(TIME_OF_DAY, 'Format jam harus HH:mm, mis. 07:30.');

const dateOnly = z.string().refine(
  (value) => {
    try {
      parseDateOnly(value);
      return true;
    } catch {
      return false;
    }
  },
  { message: 'Format tanggal harus YYYY-MM-DD dan tanggalnya harus ada.' },
);

const idParams = z.object({ id });

const slotFields = {
  teachingAssignmentId: z.number().int().positive(),
  roomId: z.number().int().positive(),
  dayOfWeek: z.enum(DAYS_OF_WEEK),
  startTime: timeOfDay,
  endTime: timeOfDay,
};

const END_AFTER_START = { path: ['endTime'], message: 'Jam selesai harus setelah jam mulai.' };

const createSlotBody = z
  .object(slotFields)
  .refine((d) => d.startTime < d.endTime, END_AFTER_START);

// Jika hanya salah satu jam yang diubah, perbandingan dengan jam lama dicek di service.
const updateSlotBody = z
  .object(slotFields)
  .partial()
  .refine((d) => Object.keys(d).length > 0, { message: 'Tidak ada data yang diubah.' })
  .refine((d) => !d.startTime || !d.endTime || d.startTime < d.endTime, END_AFTER_START);

// Tanpa semesterId memakai semester aktif (docs/API_CONVENTIONS.md bagian 4).
const listSlotsQuery = paginationQuery.extend({
  semesterId: id.optional(),
  classGroupId: id.optional(),
  teacherId: id.optional(),
  roomId: id.optional(),
  dayOfWeek: z.enum(DAYS_OF_WEEK).optional(),
});

// F-JDW-04. Satu perubahan per slot per tanggal; mengirim ulang untuk tanggal yang sama = ubah.
const changeBase = {
  scheduleSlotId: z.number().int().positive(),
  date: dateOnly,
  description: z.string().trim().min(1).max(255),
};

const saveChangeBody = z
  .discriminatedUnion('type', [
    z.object({ ...changeBase, type: z.literal('SUBSTITUTE'), substituteTeacherId: z.number().int().positive() }),
    z.object({
      ...changeBase,
      type: z.literal('RESCHEDULE'),
      newRoomId: z.number().int().positive().optional(),
      newStartTime: timeOfDay.optional(),
      newEndTime: timeOfDay.optional(),
    }),
    z.object({ ...changeBase, type: z.literal('CANCEL') }),
  ])
  .refine((d) => d.type !== 'RESCHEDULE' || !d.newStartTime === !d.newEndTime, {
    path: ['newEndTime'],
    message: 'Jam mulai dan jam selesai baru harus diisi bersamaan.',
  })
  .refine((d) => d.type !== 'RESCHEDULE' || d.newRoomId || d.newStartTime, {
    path: ['newRoomId'],
    message: 'Isi ruangan baru dan/atau jam baru.',
  })
  .refine((d) => d.type !== 'RESCHEDULE' || !d.newStartTime || d.newStartTime < d.newEndTime, {
    path: ['newEndTime'],
    message: 'Jam selesai harus setelah jam mulai.',
  });

const listChangesQuery = paginationQuery
  .extend({
    semesterId: id.optional(),
    classGroupId: id.optional(),
    // Guru asli atau guru pengganti.
    teacherId: id.optional(),
    type: z.enum(['SUBSTITUTE', 'RESCHEDULE', 'CANCEL']).optional(),
    dateFrom: dateOnly.optional(),
    dateTo: dateOnly.optional(),
  })
  .refine((d) => !d.dateFrom || !d.dateTo || d.dateFrom <= d.dateTo, {
    path: ['dateTo'],
    message: 'Tanggal akhir tidak boleh sebelum tanggal awal.',
  });

// F-JDW-08/09. Catatan kosong disimpan sebagai null.
const taskFields = {
  title: z.string().trim().min(1).max(150),
  description: z
    .string()
    .trim()
    .max(2000)
    .transform((value) => (value === '' ? null : value))
    .nullable()
    .optional(),
  dueDate: dateOnly,
};

const createTaskBody = z.object({ teachingAssignmentId: z.number().int().positive(), ...taskFields });

const updateTaskBody = z
  .object(taskFields)
  .partial()
  .refine((d) => Object.keys(d).length > 0, { message: 'Tidak ada data yang diubah.' });

// Tanpa semesterId memakai semester aktif.
const listTasksQuery = paginationQuery
  .extend({
    semesterId: id.optional(),
    teachingAssignmentId: id.optional(),
    subjectId: id.optional(),
    classGroupId: id.optional(),
    dueFrom: dateOnly.optional(),
    dueTo: dateOnly.optional(),
  })
  .refine((d) => !d.dueFrom || !d.dueTo || d.dueFrom <= d.dueTo, {
    path: ['dueTo'],
    message: 'Tanggal akhir tidak boleh sebelum tanggal awal.',
  });

// F-JDW-05. Rentang tanggal dibatasi agar respons tetap kecil (satu bulan layar jadwal).
const MAX_VIEW_DAYS = 31;
const DAY_MS = 24 * 60 * 60 * 1000;

const viewQuery = z
  .object({
    dateFrom: dateOnly.optional(),
    dateTo: dateOnly.optional(),
    // Wali murid: anak yang jadwalnya dilihat (wajib). Siswa: hanya boleh dirinya sendiri.
    studentId: id.optional(),
    // Wali kelas: jadwal kelas binaannya.
    classGroupId: id.optional(),
  })
  .refine((d) => !d.dateTo || d.dateFrom, {
    path: ['dateFrom'],
    message: 'Tanggal awal wajib diisi jika tanggal akhir diisi.',
  })
  .refine((d) => !d.dateFrom || !d.dateTo || d.dateFrom <= d.dateTo, {
    path: ['dateTo'],
    message: 'Tanggal akhir tidak boleh sebelum tanggal awal.',
  })
  .refine(
    (d) =>
      !d.dateFrom ||
      !d.dateTo ||
      (parseDateOnly(d.dateTo) - parseDateOnly(d.dateFrom)) / DAY_MS < MAX_VIEW_DAYS,
    { path: ['dateTo'], message: `Rentang tanggal maksimal ${MAX_VIEW_DAYS} hari.` },
  );

// F-JDW-02. Field teks pada form multipart; klien boleh mengirim semesterId kosong (= semester aktif).
const importSlotsBody = z.object({
  semesterId: z.preprocess((value) => (value === '' ? undefined : value), id.optional()),
});

module.exports = {
  idParams,
  createSlotBody,
  updateSlotBody,
  listSlotsQuery,
  saveChangeBody,
  listChangesQuery,
  importSlotsBody,
  viewQuery,
  createTaskBody,
  updateTaskBody,
  listTasksQuery,
};
