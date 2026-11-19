// Helper validasi query bersama untuk modul laporan admin.
const { z } = require('zod');
const { parseDateOnly } = require('../../utils/date');

const positiveId = z.coerce.number().int().positive();

const dateOnlySchema = z.string().refine(
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

module.exports = { positiveId, dateOnlySchema };
