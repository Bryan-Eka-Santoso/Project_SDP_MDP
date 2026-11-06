const { z } = require('zod');

const DEFAULT_LIMIT = 20;
const MAX_LIMIT = 100;

// Query `?page=&limit=` (docs/API_CONVENTIONS.md bagian 4). Gabungkan dengan filter modul:
// `paginationQuery.extend({ semesterId: z.coerce.number().int().positive().optional() })`.
const paginationQuery = z.object({
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(MAX_LIMIT).default(DEFAULT_LIMIT),
});

// Hasil untuk argumen `findMany` Prisma.
function toPrismaPagination({ page, limit }) {
  return { skip: (page - 1) * limit, take: limit };
}

module.exports = { paginationQuery, toPrismaPagination, DEFAULT_LIMIT, MAX_LIMIT };
