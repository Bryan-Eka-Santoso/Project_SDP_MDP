const { z } = require("zod");
const { paginationQuery } = require("../../../utils/pagination");

const idParams = z.object({
  id: z.coerce.number().int().positive(),
});

const listQuery = paginationQuery.extend({
  type: z.enum(["ROOM", "ITEM"]).optional(),
  q: z.string().trim().max(100).optional(),
});

const createBody = z.object({
  name: z.string().trim().min(1).max(100),
  type: z.enum(["ROOM", "ITEM"]),
  quantity: z.coerce.number().int().positive(),
});

const updateBody = createBody
  .partial()
  .refine((data) => Object.keys(data).length > 0, {
    message: "Minimal satu field harus diisi.",
  });

module.exports = {
  idParams,
  listQuery,
  createBody,
  updateBody,
};
