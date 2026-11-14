const { z } = require('zod');
const date = z.iso.date();
const createLeaveBody = z.object({
  studentId: z.number().int().positive(),
  startDate: date,
  endDate: date,
  reason: z.string().trim().min(5).max(255),
}).refine((value) => value.startDate <= value.endDate, {
  message: 'Tanggal akhir tidak boleh sebelum tanggal mulai.', path: ['endDate'],
});
const leaveListQuery = z.object({
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(100).default(20),
  studentId: z.coerce.number().int().positive().optional(),
});
module.exports = { createLeaveBody, leaveListQuery };
