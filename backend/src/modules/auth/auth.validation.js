const { z } = require('zod');

// bcrypt hanya membaca 72 byte pertama; password lebih panjang ditolak agar tidak terpotong diam-diam.
const BCRYPT_MAX_BYTES = 72;
const fitsBcrypt = (value) => Buffer.byteLength(value, 'utf8') <= BCRYPT_MAX_BYTES;

const email = z.string().trim().toLowerCase().max(100).pipe(z.email());
const password = z
  .string()
  .min(1)
  .refine(fitsBcrypt, { message: 'Password terlalu panjang.' });
const refreshToken = z.string().min(1).max(200);

const loginBody = z.object({ email, password });

const refreshBody = z.object({ refreshToken });

const logoutBody = z.object({
  refreshToken,
  // Token FCM perangkat ini, agar notifikasi tidak lagi dikirim ke HP yang sudah logout.
  deviceToken: z.string().min(1).max(255).optional(),
});

const changePasswordBody = z
  .object({
    currentPassword: password,
    newPassword: z
      .string()
      .min(8, { message: 'Password baru minimal 8 karakter.' })
      .refine(fitsBcrypt, { message: 'Password terlalu panjang.' }),
  })
  .refine((data) => data.currentPassword !== data.newPassword, {
    message: 'Password baru harus berbeda dari password lama.',
    path: ['newPassword'],
  });

module.exports = { loginBody, refreshBody, logoutBody, changePasswordBody };
