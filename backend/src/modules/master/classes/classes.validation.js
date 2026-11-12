const { z } = require('zod');
const { paginationQuery } = require('../../../utils/pagination');

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID kelas harus berupa bilangan bulat positif.' }),
});

const memberParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID kelas harus berupa bilangan bulat positif.' }),
  studentId: z.coerce.number().int().positive({ message: 'ID siswa harus berupa bilangan bulat positif.' }),
});

const listClassesQuery = paginationQuery.extend({
  academicYearId: z.coerce.number().int().positive().optional(),
  gradeLevel: z.string().trim().max(10).optional(),
  q: z.string().trim().max(100).optional(),
});

const createClassBody = z.object({
  academicYearId: z.coerce.number().int().positive({ message: 'ID tahun ajaran tidak valid.' }),
  name: z
    .string()
    .trim()
    .min(1, { message: 'Nama kelas wajib diisi.' })
    .max(30, { message: 'Nama kelas maksimal 30 karakter.' }),
  gradeLevel: z
    .string()
    .trim()
    .min(1, { message: 'Tingkat kelas wajib diisi (mis. 10, 11, 12).' })
    .max(10, { message: 'Tingkat kelas maksimal 10 karakter.' }),
  capacity: z.coerce
    .number()
    .int()
    .positive({ message: 'Kapasitas harus berupa bilangan bulat positif.' })
    .max(100, { message: 'Kapasitas maksimal 100 siswa per kelas.' }),
  homeroomTeacherId: z.coerce.number().int().positive().nullable().optional(),
});

const updateClassBody = z
  .object({
    name: z.string().trim().min(1).max(30).optional(),
    gradeLevel: z.string().trim().min(1).max(10).optional(),
    capacity: z.coerce.number().int().positive().max(100).optional(),
  })
  .refine(
    (data) =>
      data.name !== undefined ||
      data.gradeLevel !== undefined ||
      data.capacity !== undefined,
    {
      message: 'Minimal satu field harus diisi untuk pembaruan.',
    },
  );

const setHomeroomTeacherBody = z.object({
  homeroomTeacherId: z.coerce
    .number()
    .int()
    .positive({ message: 'ID guru tidak valid.' })
    .nullable(),
});

const addClassMemberBody = z.object({
  studentId: z.coerce
    .number()
    .int()
    .positive({ message: 'ID siswa tidak valid.' })
    .optional(),
  studentIds: z
    .array(z.coerce.number().int().positive())
    .min(1, { message: 'Daftar ID siswa tidak boleh kosong.' })
    .optional(),
}).refine((data) => data.studentId !== undefined || data.studentIds !== undefined, {
  message: 'studentId atau studentIds wajib diisi.',
});

const moveClassMemberBody = z.object({
  studentId: z.coerce.number().int().positive({ message: 'ID siswa tidak valid.' }),
  targetClassGroupId: z.coerce
    .number()
    .int()
    .positive({ message: 'ID kelas tujuan tidak valid.' }),
});

module.exports = {
  idParams,
  memberParams,
  listClassesQuery,
  createClassBody,
  updateClassBody,
  setHomeroomTeacherBody,
  addClassMemberBody,
  moveClassMemberBody,
};
