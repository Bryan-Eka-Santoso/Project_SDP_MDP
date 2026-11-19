const { z } = require('zod');
const { paginationQuery } = require('../../utils/pagination');

const idParams = z.object({
  id: z.coerce.number().int().positive({ message: 'ID agenda tidak valid.' }),
});

const eventTypeEnum = z.enum(['EXAM', 'HOLIDAY', 'ACTIVITY'], {
  message: 'Jenis agenda harus EXAM, HOLIDAY, atau ACTIVITY.',
});

const dateString = z
  .string({ message: 'Tanggal wajib diisi.' })
  .regex(/^\d{4}-\d{2}-\d{2}$/, { message: 'Format tanggal harus YYYY-MM-DD.' });

const listEventsQuery = paginationQuery
  .extend({
    type: eventTypeEnum.optional(),
    from: dateString.optional(),
    to: dateString.optional(),
    q: z.string().trim().max(100).optional(),
  })
  .refine((data) => !(data.from && data.to && data.from > data.to), {
    message: 'Tanggal awal filter tidak boleh setelah tanggal akhir.',
    path: ['to'],
  });

const createEventBody = z
  .object({
    title: z
      .string({ message: 'Judul agenda wajib diisi.' })
      .trim()
      .min(1, { message: 'Judul agenda wajib diisi.' })
      .max(100, { message: 'Judul agenda maksimal 100 karakter.' }),
    type: eventTypeEnum,
    startDate: dateString,
    endDate: dateString,
    description: z
      .string()
      .trim()
      .max(255, { message: 'Keterangan maksimal 255 karakter.' })
      .nullable()
      .optional()
      .transform((val) => (val ? val : null)),
  })
  .refine((data) => data.startDate <= data.endDate, {
    message: 'Tanggal selesai tidak boleh sebelum tanggal mulai.',
    path: ['endDate'],
  });

const updateEventBody = z
  .object({
    title: z
      .string()
      .trim()
      .min(1, { message: 'Judul agenda tidak boleh kosong.' })
      .max(100, { message: 'Judul agenda maksimal 100 karakter.' })
      .optional(),
    type: eventTypeEnum.optional(),
    startDate: dateString.optional(),
    endDate: dateString.optional(),
    description: z
      .string()
      .trim()
      .max(255, { message: 'Keterangan maksimal 255 karakter.' })
      .nullable()
      .optional()
      .transform((val) => (val === undefined ? undefined : val || null)),
  })
  .refine(
    (data) =>
      data.title !== undefined ||
      data.type !== undefined ||
      data.startDate !== undefined ||
      data.endDate !== undefined ||
      data.description !== undefined,
    { message: 'Minimal satu field harus diisi untuk pembaruan.' },
  );

module.exports = {
  idParams,
  listEventsQuery,
  createEventBody,
  updateEventBody,
};
