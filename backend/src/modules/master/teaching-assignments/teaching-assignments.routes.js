const express = require('express');
const authenticate = require('../../../middlewares/authenticate');
const authorize = require('../../../middlewares/authorize');
const validate = require('../../../middlewares/validate');
const controller = require('./teaching-assignments.controller');
const {
  idParams,
  listTeachingAssignmentsQuery,
  createTeachingAssignmentBody,
  updateTeachingAssignmentBody,
} = require('./teaching-assignments.validation');

const router = express.Router();

router.use(authenticate);

// Baca daftar & detail penugasan guru
router.get(
  '/',
  validate({ query: listTeachingAssignmentsQuery }),
  controller.list,
);
router.get('/:id', validate({ params: idParams }), controller.getById);

// F-MST-10: Kelola penugasan guru (Admin)
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createTeachingAssignmentBody }),
  controller.create,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateTeachingAssignmentBody }),
  controller.update,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.remove,
);

module.exports = router;
