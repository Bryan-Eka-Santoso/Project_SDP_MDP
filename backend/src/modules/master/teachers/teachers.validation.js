const { z } = require('zod');
const { paginationQuery } = require('../../../utils/pagination');

const BCRYPT_MAX_BYTES = 72;
const fitsBcrypt = (value) => Buffer.byteLength(value, 'utf8') <= BCRYPT_MAX_BYTES;

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID guru harus berupa bilangan bulat positif.' }),
});

const listTeachersQuery = paginationQuery.extend({
  q: z.string().trim().max(100).optional(),
  isHomeroomTeacher: z
    .enum(['true', 'false'])
    .transform((val) => val === 'true')
    .optional(),
});

const createTeacherBody = z.object({
  name: z
    .string()
    .trim()
    .min(1, { message: 'Nama guru wajib diisi.' })
    .max(100, { message: 'Nama guru maksimal 100 karakter.' }),
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
  employeeNumber: z
    .string()
    .trim()
    .min(1, { message: 'NIP / Kode guru wajib diisi.' })
    .max(30, { message: 'NIP / Kode guru maksimal 30 karakter.' }),
});

const updateTeacherBody = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, { message: 'Nama guru tidak boleh kosong.' })
      .max(100, { message: 'Nama guru maksimal 100 karakter.' })
      .optional(),
    email: z
      .string()
      .trim()
      .toLowerCase()
      .max(100, { message: 'Email maksimal 100 karakter.' })
      .pipe(z.email({ message: 'Format email tidak valid.' }))
      .optional(),
    employeeNumber: z
      .string()
      .trim()
      .min(1, { message: 'NIP / Kode guru tidak boleh kosong.' })
      .max(30, { message: 'NIP / Kode guru maksimal 30 karakter.' })
      .optional(),
  })
  .refine(
    (data) =>
      data.name !== undefined ||
      data.email !== undefined ||
      data.employeeNumber !== undefined,
    {
      message: 'Minimal satu field harus diisi untuk pembaruan.',
    },
  );

module.exports = {
  idParams,
  listTeachersQuery,
  createTeacherBody,
  updateTeacherBody,
};
