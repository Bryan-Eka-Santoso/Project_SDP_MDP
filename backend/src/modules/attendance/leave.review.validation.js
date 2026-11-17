const { z } = require('zod');

const idParams = z.object({ requestId: z.coerce.number().int().positive() });
const pendingQuery = z.object({
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(100).default(20),
});
const decisionBody = z.object({ decision: z.enum(['APPROVED', 'REJECTED']) }).strict();

module.exports = { idParams, pendingQuery, decisionBody };
