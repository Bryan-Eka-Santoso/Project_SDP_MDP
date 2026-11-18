const { z } = require("zod");

const classParams = z.object({
  classId: z.coerce.number().int().positive(),
});

const createBody = z
  .object({
    classId: z.number().int().positive(),
    studentId: z.number().int().positive(),
    date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
    content: z
      .string()
      .trim()
      .min(1, "Isi catatan wajib diisi.")
      .max(500, "Catatan maksimal 500 karakter."),
  })
  .strict();

module.exports = {
  classParams,
  createBody,
};
