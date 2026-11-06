const jwt = require('jsonwebtoken');
const env = require('../config/env');
const { parseDuration } = require('../utils/date');

// Algoritma dikunci agar token dengan `alg` lain (mis. `none`) ditolak.
const ALGORITHM = 'HS256';
const MIN_SECRET_LENGTH = 32;

function getSecret() {
  const secret = env.jwt.accessSecret;
  if (!secret || secret.length < MIN_SECRET_LENGTH) {
    throw new Error(`JWT_ACCESS_SECRET wajib diisi minimal ${MIN_SECRET_LENGTH} karakter.`);
  }
  return secret;
}

function signAccessToken(user) {
  return jwt.sign({ role: user.role }, getSecret(), {
    algorithm: ALGORITHM,
    subject: String(user.id),
    expiresIn: Math.floor(parseDuration(env.jwt.accessTtl) / 1000),
  });
}

// Melempar `TokenExpiredError` atau `JsonWebTokenError` jika token tidak valid.
function verifyAccessToken(token) {
  return jwt.verify(token, getSecret(), { algorithms: [ALGORITHM] });
}

module.exports = {
  signAccessToken,
  verifyAccessToken,
  TokenExpiredError: jwt.TokenExpiredError,
  JsonWebTokenError: jwt.JsonWebTokenError,
};
