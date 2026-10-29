const AppError = require('../utils/AppError');

function notFoundHandler(req, res, next) {
  next(new AppError(404, 'NOT_FOUND', 'Halaman atau data tidak ditemukan.'));
}

// Ubah error dari library menjadi AppError agar format response selalu sama.
function toAppError(err) {
  if (err instanceof AppError) return err;

  // JSON body rusak dari express.json()
  if (err.type === 'entity.parse.failed') {
    return new AppError(400, 'VALIDATION_ERROR', 'Format JSON tidak valid.');
  }

  // Dicek lewat nama agar middleware ini tidak bergantung pada Prisma Client.
  if (err.name === 'PrismaClientKnownRequestError') {
    if (err.code === 'P2002') {
      return new AppError(409, 'DUPLICATE', 'Data sudah ada.');
    }
    if (err.code === 'P2025') {
      return new AppError(404, 'NOT_FOUND', 'Data tidak ditemukan.');
    }
  }

  return null;
}

// eslint-disable-next-line no-unused-vars -- Express mengenali error handler dari 4 parameter
function errorHandler(err, req, res, next) {
  const appError = toAppError(err);

  if (!appError) {
    // Detail error tak terduga hanya di log server, tidak dikirim ke client.
    console.error(err);
    return res.status(500).json({
      success: false,
      error: { code: 'INTERNAL_ERROR', message: 'Terjadi kesalahan pada server.' },
    });
  }

  const error = { code: appError.code, message: appError.message };
  if (appError.details !== undefined) error.details = appError.details;

  return res.status(appError.statusCode).json({ success: false, error });
}

module.exports = { errorHandler, notFoundHandler };
