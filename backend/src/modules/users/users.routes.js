const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const controller = require('./users.controller');
const {
  idParams,
  listUsersQuery,
  createUserBody,
  updateUserBody,
  updateRoleBody,
  updateStatusBody,
  resetPasswordBody,
} = require('./users.validation');

const router = express.Router();

// Semua rute master user hanya boleh diakses role ADMIN (docs/PERMISSIONS.md).
router.use(authenticate, authorize('ADMIN'));

// F-MST-01: Daftar & Tambah User
router.get('/', validate({ query: listUsersQuery }), controller.list);
router.post('/', validate({ body: createUserBody }), controller.create);

// F-MST-01: Detail, Ubah Data Dasar, dan Hapus User
router.get('/:id', validate({ params: idParams }), controller.getById);
router.patch(
  '/:id',
  validate({ params: idParams, body: updateUserBody }),
  controller.update,
);
router.delete('/:id', validate({ params: idParams }), controller.remove);

// F-MST-02: Atur Role User
router.patch(
  '/:id/role',
  validate({ params: idParams, body: updateRoleBody }),
  controller.updateRole,
);

// F-MST-01: Blokir / Buka Blokir Akun
router.patch(
  '/:id/status',
  validate({ params: idParams, body: updateStatusBody }),
  controller.updateStatus,
);

// F-MST-03: Reset Password User oleh Admin
router.post(
  '/:id/reset-password',
  validate({ params: idParams, body: resetPasswordBody }),
  controller.resetPassword,
);

module.exports = router;
