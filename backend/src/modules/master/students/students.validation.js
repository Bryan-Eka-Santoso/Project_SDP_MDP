const { z } = require('zod');
const { paginationQuery } = require('../../../utils/pagination');

const BCRYPT_MAX_BYTES = 72;
const fitsBcrypt = (value) => Buffer.byteLength(value, 'utf8') <= BCRYPT_MAX_BYTES;

const dateString = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, { message: 'Format tanggal harus YYYY-MM-DD.' });

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID siswa harus berupa bilangan bulat positif.' }),
});

const guardianIdParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID siswa harus berupa bilangan bulat positif.' }),
  guardianId: z.coerce.number().int().positive({ message: 'ID wali murid harus berupa bilangan bulat positif.' }),
});

const listStudentsQuery = paginationQuery.extend({
  q: z.string().trim().max(100).optional(),
  classGroupId: z.coerce.number().int().positive().optional(),
});

const createStudentBody = z.object({
  name: z
    .string()
    .trim()
    .min(1, { message: 'Nama siswa wajib diisi.' })
    .max(100, { message: 'Nama siswa maksimal 100 karakter.' }),
  email: z
    .string()
    .trim()
    .toLowerCase()
    .max(100, { message: 'Email maksimal 100 karakter.' })
    .pipe(z.email({ message: 'Format email tidak valid.' })),
  password: z
    .string()
    .min(8, { message: 'Password awal minimal 8 karakter.' })
    .refine(fitsBcrypt, { message: 'Password terlalu panjang.' }),
  nis: z
    .string()
    .trim()
    .min(1, { message: 'NIS wajib diisi.' })
    .max(30, { message: 'NIS maksimal 30 karakter.' }),
  birthDate: dateString,
  phone: z.string().trim().max(20).optional(),
});

const updateStudentBody = z
  .object({
    name: z.string().trim().min(1).max(100).optional(),
    email: z.string().trim().toLowerCase().max(100).pipe(z.email()).optional(),
    nis: z.string().trim().min(1).max(30).optional(),
    birthDate: dateString.optional(),
    phone: z.string().trim().max(20).nullable().optional(),
  })
  .refine(
    (data) =>
      data.name !== undefined ||
      data.email !== undefined ||
      data.nis !== undefined ||
      data.birthDate !== undefined ||
      data.phone !== undefined,
    {
      message: 'Minimal satu field harus diisi untuk pembaruan.',
    },
  );

const linkGuardianBody = z.object({
  guardianId: z.coerce.number().int().positive({ message: 'ID wali murid harus valid.' }),
  relationship: z
    .string()
    .trim()
    .min(1, { message: 'Hubungan wajib diisi (mis. Ayah, Ibu, Wali).' })
    .max(30, { message: 'Hubungan maksimal 30 karakter.' }),
});

const listGuardiansQuery = paginationQuery.extend({
  q: z.string().trim().max(100).optional(),
});

const createGuardianBody = z.object({
  name: z
    .string()
    .trim()
    .min(1, { message: 'Nama wali murid wajib diisi.' })
    .max(100, { message: 'Nama wali murid maksimal 100 karakter.' }),
  email: z
    .string()
    .trim()
    .toLowerCase()
    .max(100, { message: 'Email maksimal 100 karakter.' })
    .pipe(z.email({ message: 'Format email tidak valid.' })),
  password: z
    .string()
    .min(8, { message: 'Password awal minimal 8 karakter.' })
    .refine(fitsBcrypt, { message: 'Password terlalu panjang.' }),
  phone: z
    .string()
    .trim()
    .min(1, { message: 'Nomor telepon wajib diisi.' })
    .max(20, { message: 'Nomor telepon maksimal 20 karakter.' }),
});

const updateGuardianBody = z
  .object({
    name: z.string().trim().min(1).max(100).optional(),
    email: z.string().trim().toLowerCase().max(100).pipe(z.email()).optional(),
    phone: z.string().trim().min(1).max(20).optional(),
  })
  .refine(
    (data) =>
      data.name !== undefined || data.email !== undefined || data.phone !== undefined,
    {
      message: 'Minimal satu field harus diisi untuk pembaruan.',
    },
  );

module.exports = {
  idParams,
  guardianIdParams,
  listStudentsQuery,
  createStudentBody,
  updateStudentBody,
  linkGuardianBody,
  listGuardiansQuery,
  createGuardianBody,
  updateGuardianBody,
};
