const { z } = require('zod');

const idParams = z.object({ requestId: z.coerce.number().int().positive() });
const pendingQuery = z.object({
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(100).default(20),
});
const decisionBody = z
  .object({
    decision: z.enum(['APPROVED', 'REJECTED']),
    rejectionReason: z.string().trim().max(255).optional(),
  })
  .strict()
  .superRefine((data, ctx) => {
    if (data.decision === 'REJECTED' && (!data.rejectionReason || data.rejectionReason.length === 0)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['rejectionReason'],
        message: 'Alasan penolakan wajib diisi saat menolak pengajuan izin.',
      });
    }
  });

module.exports = { idParams, pendingQuery, decisionBody };
