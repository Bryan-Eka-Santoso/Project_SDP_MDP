const { z } = require('zod');
const AppError = require('../utils/AppError');

// Pesan default Zod dalam bahasa Indonesia karena `details` ditampilkan ke pengguna.
z.config(z.locales.id());

const SOURCES = ['params', 'query', 'body'];

function toDetails(issues) {
  return issues.map((issue) => ({
    field: issue.path.join('.'),
    message: issue.message,
  }));
}

/**
 * Validasi request dengan schema Zod per bagian, mis.
 * `validate({ params: idParams, body: createSchema })`.
 *
 * Hasil parse (sudah di-coerce dan diberi default) disimpan di `req.validated.<bagian>`.
 * `req.body` juga diganti hasil parse. `req.query` tidak bisa ditimpa di Express 5,
 * jadi controller wajib membaca `req.validated.query`.
 */
function validate(schemas) {
  return (req, res, next) => {
    const validated = {};
    const issues = [];

    for (const source of SOURCES) {
      const schema = schemas[source];
      if (!schema) continue;

      const result = schema.safeParse(req[source] ?? {});
      if (result.success) {
        validated[source] = result.data;
      } else {
        issues.push(...result.error.issues.map((i) => ({ ...i, path: [source, ...i.path] })));
      }
    }

    if (issues.length > 0) {
      return next(
        new AppError(400, 'VALIDATION_ERROR', 'Data yang dikirim tidak valid.', toDetails(issues)),
      );
    }

    req.validated = validated;
    if (validated.body !== undefined) req.body = validated.body;
    return next();
  };
}

module.exports = validate;
