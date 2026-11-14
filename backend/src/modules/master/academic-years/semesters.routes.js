const express = require('express');
const authenticate = require('../../../middlewares/authenticate');
const authorize = require('../../../middlewares/authorize');
const validate = require('../../../middlewares/validate');
const controller = require('./academic-years.controller');
const {
  idParams,
  listSemestersQuery,
  createSemesterBody,
  updateSemesterBody,
} = require('./academic-years.validation');

const router = express.Router();

router.use(authenticate);

// Baca semester
router.get('/active', controller.getActiveSemester);
router.get('/', validate({ query: listSemestersQuery }), controller.listSemesters);
router.get('/:id', validate({ params: idParams }), controller.getSemesterById);

// F-MST-06: Kelola semester (Admin)
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createSemesterBody }),
  controller.createSemester,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateSemesterBody }),
  controller.updateSemester,
);
router.patch(
  '/:id/active',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.setActiveSemester,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.deleteSemester,
);

module.exports = router;
