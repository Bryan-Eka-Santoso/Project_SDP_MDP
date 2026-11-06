const AppError = require('../utils/AppError');

/**
 * Lapis pertama otorisasi: cek role (docs/PERMISSIONS.md). Dipasang setelah `authenticate`,
 * yang mengisi `req.user = { id, role, ... }`. Cakupan data (siswa hanya datanya sendiri, dst.)
 * tetap dicek di service/policy.
 *
 * Contoh: `router.post('/', authenticate, authorize('ADMIN'), validate(...), controller.create)`.
 */
function authorize(...roles) {
  if (roles.length === 0) {
    throw new Error('authorize() membutuhkan minimal satu role.');
  }

  return (req, res, next) => {
    if (!req.user) {
      return next(new AppError(401, 'UNAUTHENTICATED', 'Silakan login terlebih dahulu.'));
    }
    if (!roles.includes(req.user.role)) {
      return next(new AppError(403, 'FORBIDDEN', 'Anda tidak memiliki akses ke fitur ini.'));
    }
    return next();
  };
}

module.exports = authorize;
