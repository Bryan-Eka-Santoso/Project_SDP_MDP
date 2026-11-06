const prisma = require('../lib/prisma');
const { verifyAccessToken, TokenExpiredError, JsonWebTokenError } = require('../lib/jwt');
const AppError = require('../utils/AppError');

/**
 * Memeriksa `Authorization: Bearer <accessToken>` lalu mengisi `req.user = { id, role }`.
 *
 * Role dan status dibaca ulang dari database, bukan dari isi token, supaya akun yang diblokir
 * atau diganti role-nya langsung berlaku tanpa menunggu access token kedaluwarsa.
 */
async function authenticate(req, res, next) {
  const [scheme, token] = (req.get('authorization') || '').split(' ');
  if (scheme !== 'Bearer' || !token) {
    throw new AppError(401, 'UNAUTHENTICATED', 'Silakan login terlebih dahulu.');
  }

  let payload;
  try {
    payload = verifyAccessToken(token);
  } catch (err) {
    if (err instanceof TokenExpiredError) {
      throw new AppError(401, 'TOKEN_EXPIRED', 'Sesi telah berakhir.');
    }
    if (err instanceof JsonWebTokenError) {
      throw new AppError(401, 'UNAUTHENTICATED', 'Sesi tidak valid. Silakan login kembali.');
    }
    // Mis. JWT_ACCESS_SECRET belum diisi: biarkan jadi 500 agar terlihat di log.
    throw err;
  }

  const userId = Number(payload.sub);
  const user = Number.isInteger(userId)
    ? await prisma.user.findUnique({
        where: { id: userId },
        select: { id: true, role: true, status: true },
      })
    : null;

  if (!user) {
    throw new AppError(401, 'UNAUTHENTICATED', 'Sesi tidak valid. Silakan login kembali.');
  }
  if (user.status === 'BLOCKED') {
    throw new AppError(403, 'ACCOUNT_BLOCKED', 'Akun Anda diblokir. Hubungi admin sekolah.');
  }

  req.user = { id: user.id, role: user.role };
  return next();
}

module.exports = authenticate;
