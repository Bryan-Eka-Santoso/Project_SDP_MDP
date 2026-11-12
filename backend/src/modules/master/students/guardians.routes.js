const express = require('express');
const authenticate = require('../../../middlewares/authenticate');
const authorize = require('../../../middlewares/authorize');
const validate = require('../../../middlewares/validate');
const controller = require('./students.controller');
const {
  idParams,
  listGuardiansQuery,
  createGuardianBody,
  updateGuardianBody,
} = require('./students.validation');

const router = express.Router();

router.use(authenticate);

// Baca daftar & detail wali murid (Admin & Guru / Wali kelas)
router.get(
  '/',
  authorize('ADMIN', 'TEACHER'),
  validate({ query: listGuardiansQuery }),
  controller.listGuardians,
);
router.get(
  '/:id',
  authorize('ADMIN', 'TEACHER'),
  validate({ params: idParams }),
  controller.getGuardianById,
);

// F-MST-04: Kelola wali murid (Admin)
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createGuardianBody }),
  controller.createGuardian,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateGuardianBody }),
  controller.updateGuardian,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.deleteGuardian,
);

module.exports = router;
