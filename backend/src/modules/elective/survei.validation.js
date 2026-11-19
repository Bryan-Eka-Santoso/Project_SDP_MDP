const { z } = require("zod");

const idParams = z.object({
  id: z.coerce.number().int().positive(),
});

const createBody = z
  .object({
    academicYearId: z.number().int().positive(),
    gradeLevel: z.string().trim().min(1).max(10),
    title: z.string().trim().min(5).max(100),
    openDate: z.iso.date(),
    closeDate: z.iso.date(),
  })
  .strict()
  .refine((data) => data.closeDate >= data.openDate, {
    path: ["closeDate"],
    message: "Tanggal tutup tidak boleh sebelum tanggal buka.",
  });

module.exports = {
  idParams,
  createBody,
};
