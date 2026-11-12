const { z } = require('zod');
const { paginationQuery } = require('../../utils/pagination');

// bcrypt hanya membaca 72 byte pertama (ADR-003, docs/BUSINESS_RULES.md bagian 11).
const BCRYPT_MAX_BYTES = 72;
const fitsBcrypt = (value) => Buffer.byteLength(value, 'utf8') <= BCRYPT_MAX_BYTES;

const roleEnum = z.enum(['STUDENT', 'TEACHER', 'GUARDIAN', 'ADMIN'], {
  message: 'Role harus salah satu dari STUDENT, TEACHER, GUARDIAN, atau ADMIN.',
});

const statusEnum = z.enum(['ACTIVE', 'BLOCKED'], {
  message: 'Status harus salah satu dari ACTIVE atau BLOCKED.',
});

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID pengguna harus berupa bilangan bulat positif.' }),
});

const listUsersQuery = paginationQuery.extend({
  q: z.string().trim().max(100).optional(),
  role: roleEnum.optional(),
  status: statusEnum.optional(),
});

const createUserBody = z.object({
  email: z
    .string()
    .trim()
    .toLowerCase()
    .max(100, { message: 'Email maksimal 100 karakter.' })
    .pipe(z.email({ message: 'Format email tidak valid.' })),
  password: z
    .string()
    .min(8, { message: 'Password minimal 8 karakter.' })
    .refine(fitsBcrypt, { message: 'Password terlalu panjang.' }),
  name: z
    .string()
    .trim()
    .min(1, { message: 'Nama lengkap wajib diisi.' })
    .max(100, { message: 'Nama maksimal 100 karakter.' }),
  role: roleEnum,
});

const updateUserBody = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, { message: 'Nama lengkap tidak boleh kosong.' })
      .max(100, { message: 'Nama maksimal 100 karakter.' })
      .optional(),
    email: z
      .string()
      .trim()
      .toLowerCase()
      .max(100, { message: 'Email maksimal 100 karakter.' })
      .pipe(z.email({ message: 'Format email tidak valid.' }))
      .optional(),
  })
  .refine((data) => data.name !== undefined || data.email !== undefined, {
    message: 'Minimal satu field (nama atau email) harus diisi untuk pembaruan.',
  });

const updateRoleBody = z.object({
  role: roleEnum,
});

const updateStatusBody = z.object({
  status: statusEnum,
});

const resetPasswordBody = z.object({
  newPassword: z
    .string()
    .min(8, { message: 'Password baru minimal 8 karakter.' })
    .refine(fitsBcrypt, { message: 'Password terlalu panjang.' }),
});

module.exports = {
  idParams,
  listUsersQuery,
  createUserBody,
  updateUserBody,
  updateRoleBody,
  updateStatusBody,
  resetPasswordBody,
};
