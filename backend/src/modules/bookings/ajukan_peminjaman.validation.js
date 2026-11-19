const { z } = require("zod");

const pengajuanBody = z
  .object({
    facilityId: z.number().int().positive(),

    bookingDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),

    startTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/),

    endTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/),

    purpose: z.string().trim().min(5, "Keperluan minimal 5 karakter").max(255),
  })
  .strict()
  .refine((data) => data.startTime < data.endTime, {
    message: "Jam selesai harus setelah jam mulai",
    path: ["endTime"],
  });

module.exports = { pengajuanBody };
