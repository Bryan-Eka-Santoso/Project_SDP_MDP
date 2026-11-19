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

const gradePredicateItem = z.object({
  label: z
    .string({ message: 'Label predikat wajib diisi.' })
    .trim()
    .min(1, { message: 'Label predikat wajib diisi.' })
    .max(5, { message: 'Label predikat maksimal 5 karakter.' }),
  minScore: z
    .number({ message: 'Nilai minimum wajib berupa angka.' })
    .finite()
    .min(0, { message: 'Nilai minimum harus di antara 0 dan 100.' })
    .max(100, { message: 'Nilai minimum harus di antara 0 dan 100.' })
    .refine((v) => Math.abs(Math.round(v * 100) - v * 100) < 1e-6, {
      message: 'Nilai minimum maksimal dua angka desimal.',
    }),
  description: z
    .string()
    .trim()
    .max(50, { message: 'Keterangan maksimal 50 karakter.' })
    .nullable()
    .optional()
    .transform((val) => (val ? val : null)),
});

// Skala predikat satu tahun ajaran wajib punya nilai minimum 0 serta label & nilai unik (BUSINESS_RULES 3).
const replaceGradePredicatesBody = z
  .object({
    predicates: z
      .array(gradePredicateItem)
      .min(1, { message: 'Minimal harus ada satu baris skala predikat.' })
      .max(20, { message: 'Maksimal 20 baris skala predikat per tahun ajaran.' }),
  })
  .superRefine((data, ctx) => {
    const seenLabels = new Map();
    const seenScores = new Map();
    let hasZero = false;

    data.predicates.forEach((item, index) => {
      if (item.minScore === 0) hasZero = true;

      const labelKey = item.label.toUpperCase();
      if (seenLabels.has(labelKey)) {
        ctx.addIssue({
          code: 'custom',
          path: ['predicates', index, 'label'],
          message: `Label predikat "${item.label}" duplikat dalam skala.`,
        });
      } else {
        seenLabels.set(labelKey, index);
      }

      const normalizedScore = Math.round(item.minScore * 100);
      if (seenScores.has(normalizedScore)) {
        ctx.addIssue({
          code: 'custom',
          path: ['predicates', index, 'minScore'],
          message: `Nilai minimum ${item.minScore} duplikat dalam skala.`,
        });
      } else {
        seenScores.set(normalizedScore, index);
      }
    });

    if (!hasZero) {
      ctx.addIssue({
        code: 'custom',
        path: ['predicates'],
        message: 'Skala predikat wajib memiliki satu predikat dengan nilai minimum 0.',
      });
    }
  });

module.exports = {
  idParams,
  listAcademicYearsQuery,
  createAcademicYearBody,
  updateAcademicYearBody,
  listSemestersQuery,
  createSemesterBody,
  updateSemesterBody,
  replaceGradePredicatesBody,
};
