const { z } = require('zod');
const { paginationQuery } = require('../../utils/pagination');

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID pengumuman tidak valid.' }),
});

const scopeEnum = z.enum(['SCHOOL', 'CLASS'], {
  message: 'Target pengumuman harus SCHOOL atau CLASS.',
});

const dateString = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, { message: 'Format tanggal harus YYYY-MM-DD.' });

const listAnnouncementsQuery = paginationQuery.extend({
  scope: scopeEnum.optional(),
  classGroupId: z.coerce.number().int().positive().optional(),
  studentId: z.coerce.number().int().positive().optional(),
  q: z.string().trim().max(100).optional(),
});

const optionalUrl = z.preprocess(
  (val) => (typeof val === 'string' && val.trim() === '' ? null : val),
  z
    .string()
    .trim()
    .url({ message: 'URL lampiran tidak valid.' })
    .max(255, { message: 'URL lampiran maksimal 255 karakter.' })
    .nullable()
    .optional(),
);

const optionalPublicId = z.preprocess(
  (val) => (typeof val === 'string' && val.trim() === '' ? null : val),
  z
    .string()
    .trim()
    .max(255, { message: 'ID lampiran maksimal 255 karakter.' })
    .nullable()
    .optional(),
);

const createAnnouncementBody = z
  .object({
    title: z
      .string({ message: 'Judul pengumuman wajib diisi.' })
      .trim()
      .min(1, { message: 'Judul pengumuman wajib diisi.' })
      .max(100, { message: 'Judul pengumuman maksimal 100 karakter.' }),
    content: z
      .string({ message: 'Isi pengumuman wajib diisi.' })
      .trim()
      .min(1, { message: 'Isi pengumuman wajib diisi.' })
      .max(500, { message: 'Isi pengumuman maksimal 500 karakter.' }),
    scope: scopeEnum,
    classGroupId: z.coerce.number().int().positive().nullable().optional(),
    date: dateString.optional(),
    attachmentUrl: optionalUrl,
    attachmentPublicId: optionalPublicId,
  })
  .superRefine((data, ctx) => {
    const hasClass = data.classGroupId !== null && data.classGroupId !== undefined;
    if (data.scope === 'SCHOOL' && hasClass) {
      ctx.addIssue({
        code: 'custom',
        path: ['classGroupId'],
        message: 'Pengumuman tingkat sekolah tidak boleh terikat ke kelas tertentu.',
      });
    }
    if (data.scope === 'CLASS' && !hasClass) {
      ctx.addIssue({
        code: 'custom',
        path: ['classGroupId'],
        message: 'Pilih kelas tujuan pengumuman.',
      });
    }
  });

const updateAnnouncementBody = z
  .object({
    title: z
      .string()
      .trim()
      .min(1, { message: 'Judul pengumuman tidak boleh kosong.' })
      .max(100, { message: 'Judul pengumuman maksimal 100 karakter.' })
      .optional(),
    content: z
      .string()
      .trim()
      .min(1, { message: 'Isi pengumuman tidak boleh kosong.' })
      .max(500, { message: 'Isi pengumuman maksimal 500 karakter.' })
      .optional(),
    date: dateString.optional(),
    attachmentUrl: optionalUrl,
    attachmentPublicId: optionalPublicId,
  })
  .refine(
    (data) =>
      data.title !== undefined ||
      data.content !== undefined ||
      data.date !== undefined ||
      data.attachmentUrl !== undefined ||
      data.attachmentPublicId !== undefined,
    { message: 'Minimal satu field harus diisi untuk pembaruan.' },
  );

module.exports = {
  idParams,
  listAnnouncementsQuery,
  createAnnouncementBody,
  updateAnnouncementBody,
};
