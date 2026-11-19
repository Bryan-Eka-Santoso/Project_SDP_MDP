const { z } = require('zod');

const positiveId = z.coerce.number().int().positive();
const gradeLevel = z.string().trim().min(1).max(10);

const allocationQuery = z.object({
  packageId: positiveId,
  gradeLevel,
});

const allocationBody = z.object({
  packageId: positiveId,
  gradeLevel,
  signature: z.string().regex(/^[a-f0-9]{64}$/),
});

module.exports = { allocationQuery, allocationBody };
