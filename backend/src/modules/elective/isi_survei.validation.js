const { z } = require("zod");

const idParams = z.object({
  id: z.coerce.number().int().positive(),
});

const submitBody = z
  .object({
    subjectIds: z
      .array(z.number().int().positive())
      .min(1, "Pilih minimal satu mata pelajaran.")
      .max(100)
      .refine(
        (ids) => new Set(ids).size === ids.length,
        "Mata pelajaran tidak boleh dipilih berulang.",
      ),
  })
  .strict();

module.exports = {
  idParams,
  submitBody,
};
