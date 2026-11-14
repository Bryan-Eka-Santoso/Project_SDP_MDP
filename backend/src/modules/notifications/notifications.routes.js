const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const validate = require('../../middlewares/validate');
const controller = require('./notifications.controller');
const { deviceTokenBody } = require('./notifications.validation');

const router = express.Router();

// F-NTF-02. Dipanggil Android setelah login dan saat token FCM berganti. Semua role.
router.post(
  '/me/device-tokens',
  authenticate,
  validate({ body: deviceTokenBody }),
  controller.registerDeviceToken,
);

module.exports = router;
