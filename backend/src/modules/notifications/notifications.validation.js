const { z } = require('zod');

const deviceTokenBody = z.object({
  token: z.string().trim().min(1).max(255),
  // Hanya Android untuk saat ini (docs/API_CONVENTIONS.md bagian 6).
  platform: z.literal('ANDROID').default('ANDROID'),
});

module.exports = { deviceTokenBody };
