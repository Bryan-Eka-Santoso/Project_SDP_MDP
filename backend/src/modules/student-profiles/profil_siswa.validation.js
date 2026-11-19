const { z } = require("zod");

const studentParams = z.object({
  studentId: z.coerce.number().int().positive(),
});

module.exports = {
  studentParams,
};
