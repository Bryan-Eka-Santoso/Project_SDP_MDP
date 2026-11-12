const { z } = require('zod');
const { paginationQuery } = require('../../../utils/pagination');

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID harus berupa bilangan bulat positif.' }),
});

const listAcademicYearsQuery = paginationQuery.extend({
  q: z.string().trim().max(100).optional(),
  isActive: z
    .enum(['true', 'false'])
    .transform((val) => val === 'true')
    .optional(),
});

const dateString = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, { message: 'Format tanggal harus YYYY-MM-DD.' });

const createAcademicYearBody = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, { message: 'Nama tahun ajaran wajib diisi.' })
      .max(30, { message: 'Nama tahun ajaran maksimal 30 karakter.' }),
    startDate: dateString,
    endDate: dateString,
    isActive: z.boolean().default(false),
  })
  .refine((data) => data.startDate <= data.endDate, {
    message: 'Tanggal mulai harus sebelum atau sama dengan tanggal selesai.',
    path: ['endDate'],
  });

const updateAcademicYearBody = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, { message: 'Nama tahun ajaran tidak boleh kosong.' })
      .max(30, { message: 'Nama tahun ajaran maksimal 30 karakter.' })
      .optional(),
    startDate: dateString.optional(),
    endDate: dateString.optional(),
  })
  .refine(
    (data) => {
      if (data.startDate && data.endDate) {
        return data.startDate <= data.endDate;
      }
      return true;
    },
    {
      message: 'Tanggal mulai harus sebelum atau sama dengan tanggal selesai.',
      path: ['endDate'],
    },
  );

const semesterTypeEnum = z.enum(['ODD', 'EVEN'], {
  message: 'Jenis semester harus ODD (ganjil) atau EVEN (genap).',
});

const listSemestersQuery = paginationQuery.extend({
  academicYearId: z.coerce.number().int().positive().optional(),
  type: semesterTypeEnum.optional(),
  isActive: z
    .enum(['true', 'false'])
    .transform((val) => val === 'true')
    .optional(),
});

const createSemesterBody = z
  .object({
    academicYearId: z.coerce.number().int().positive({ message: 'ID tahun ajaran tidak valid.' }),
    type: semesterTypeEnum,
    startDate: dateString,
    endDate: dateString,
    isActive: z.boolean().default(false),
  })
  .refine((data) => data.startDate <= data.endDate, {
    message: 'Tanggal mulai harus sebelum atau sama dengan tanggal selesai.',
    path: ['endDate'],
  });

const updateSemesterBody = z
  .object({
    startDate: dateString.optional(),
    endDate: dateString.optional(),
  })
  .refine(
    (data) => {
      if (data.startDate && data.endDate) {
        return data.startDate <= data.endDate;
      }
      return true;
    },
    {
      message: 'Tanggal mulai harus sebelum atau sama dengan tanggal selesai.',
      path: ['endDate'],
    },
  );

module.exports = {
  idParams,
  listAcademicYearsQuery,
  createAcademicYearBody,
  updateAcademicYearBody,
  listSemestersQuery,
  createSemesterBody,
  updateSemesterBody,
};
