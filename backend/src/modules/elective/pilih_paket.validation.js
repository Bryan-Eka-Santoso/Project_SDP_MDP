const { z } = require("zod");

const pilihPaketBody = z
  .object({
    packageId: z.number().int().positive(),
  })
  .strict();

module.exports = {
  pilihPaketBody,
};
