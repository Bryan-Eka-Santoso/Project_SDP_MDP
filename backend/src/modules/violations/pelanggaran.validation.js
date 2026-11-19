const { z } = require("zod");

const inputBody = z.object({
  studentId: z.number().int().positive(),
  violationTypeId: z.number().int().positive(),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  description: z.string().trim().max(255).nullable().optional(),
});

module.exports = {
  inputBody,
};
