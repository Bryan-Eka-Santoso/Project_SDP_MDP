const express = require('express');
const authenticate = require('../../../middlewares/authenticate');
const authorize = require('../../../middlewares/authorize');
const validate = require('../../../middlewares/validate');
const controller = require('./teachers.controller');
const {
  idParams,
  listTeachersQuery,
  createTeacherBody,
  updateTeacherBody,
} = require('./teachers.validation');

const router = express.Router();

router.use(authenticate);

// Baca daftar & detail guru
router.get('/', validate({ query: listTeachersQuery }), controller.list);
router.get('/:id', validate({ params: idParams }), controller.getById);

// F-MST-05: Kelola guru (Admin)
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createTeacherBody }),
  controller.create,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateTeacherBody }),
  controller.update,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.remove,
);

module.exports = router;
