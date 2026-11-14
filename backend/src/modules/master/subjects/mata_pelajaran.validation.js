const { z } = require("zod");

const idParams = z.object({
  id: z.coerce.number().int().positive(),
});

const listQuery = z.object({
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().min(1).max(100).default(20),
  search: z.string().trim().max(100).optional(),
});

const subjectBody = z.object({
  code: z.string().trim().min(1).max(20),
  name: z.string().trim().min(1).max(100),
  type: z.string().trim().min(1).max(30),
});

module.exports = {
  idParams,
  listQuery,
  subjectBody,
};
