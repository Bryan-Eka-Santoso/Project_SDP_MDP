const { z } = require("zod");
const { paginationQuery } = require("../../utils/pagination");

const idParams = z.object({
  id: z.coerce.number().int().positive(),
});

const listQuery = paginationQuery.extend({
  status: z.enum(["PENDING", "APPROVED", "REJECTED", "RETURNED"]).optional(),
});

const rejectBody = z.object({
  rejectionReason: z
    .string()
    .trim()
    .min(1, "Alasan penolakan wajib diisi.")
    .max(255, "Alasan penolakan maksimal 255 karakter."),
});

module.exports = {
  idParams,
  listQuery,
  rejectBody,
};
