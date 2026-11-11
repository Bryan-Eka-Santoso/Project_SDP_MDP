const { z } = require('zod');
const { parseDateOnly } = require('../../utils/date');
const { paginationQuery } = require('../../utils/pagination');

const id = z.coerce.number().int().positive();

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

// Query string selalu berupa teks: ?overdue=true / ?overdue=false.
const flag = z.enum(['true', 'false']).transform((value) => value === 'true');

const filters = {
  status: z.enum(['PAID', 'UNPAID']).optional(),
  // true = menunggak (belum dibayar dan lewat jatuh tempo); false = lunas atau belum jatuh tempo.
  overdue: flag.optional(),
  dueFrom: dateOnly.optional(),
  dueTo: dateOnly.optional(),
};

const dueRange = {
  path: ['dueTo'],
  message: 'Tanggal akhir tidak boleh sebelum tanggal awal.',
};
const isValidRange = (d) => !d.dueFrom || !d.dueTo || d.dueFrom <= d.dueTo;

const studentParams = z.object({ studentId: id });

const studentBillsQuery = paginationQuery.extend(filters).refine(isValidRange, dueRange);

const listBillsQuery = paginationQuery
  .extend({
    ...filters,
    classGroupId: id.optional(),
    // Cari NIS atau nama siswa.
    q: z.string().trim().min(1).max(100).optional(),
  })
  .refine(isValidRange, dueRange);

module.exports = { studentParams, studentBillsQuery, listBillsQuery };
