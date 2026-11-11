const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const validate = require('../../middlewares/validate');
const { loginLimits, refreshLimit, passwordLimit } = require('../../middlewares/rateLimit');
const controller = require('./auth.controller');
const { loginBody, refreshBody, logoutBody, changePasswordBody } = require('./auth.validation');

const router = express.Router();

// F-AUTH-01, F-AUTH-02. Tanpa authenticate: access token bisa sudah kedaluwarsa saat refresh/logout.
// Pembatas laju dipasang sebelum validasi dan bcrypt agar request berlebih ditolak murah.
router.post('/auth/login', ...loginLimits, validate({ body: loginBody }), controller.login);
router.post('/auth/refresh', refreshLimit, validate({ body: refreshBody }), controller.refresh);
router.post('/auth/logout', validate({ body: logoutBody }), controller.logout);

// F-AUTH-03. Semua role boleh (docs/PERMISSIONS.md), jadi cukup authenticate tanpa authorize.
router.get('/me', authenticate, controller.me);
router.patch(
  '/me/password',
  authenticate,
  passwordLimit,
  validate({ body: changePasswordBody }),
  controller.changePassword,
);

module.exports = router;
