const { z } = require("zod");
const { paginationQuery } = require("../../utils/pagination");

const idParams = z.object({
  id: z.coerce.number().int().positive(),
});

const listQuery = paginationQuery.extend({
  status: z.enum(["PENDING", "APPROVED", "REJECTED", "RETURNED"]).optional(),
});

module.exports = {
  idParams,
  listQuery,
};
