const express = require('express');
const authenticate = require('../../../middlewares/authenticate');
const authorize = require('../../../middlewares/authorize');
const validate = require('../../../middlewares/validate');
const controller = require('./academic-years.controller');
const {
  idParams,
  listAcademicYearsQuery,
  createAcademicYearBody,
  updateAcademicYearBody,
} = require('./academic-years.validation');

const router = express.Router();

router.use(authenticate);

// Baca tahun ajaran
router.get('/active', controller.getActiveAcademicYear);
router.get('/', validate({ query: listAcademicYearsQuery }), controller.listAcademicYears);
router.get('/:id', validate({ params: idParams }), controller.getAcademicYearById);

// F-MST-06: Kelola tahun ajaran (Admin)
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createAcademicYearBody }),
  controller.createAcademicYear,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateAcademicYearBody }),
  controller.updateAcademicYear,
);
router.patch(
  '/:id/active',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.setActiveAcademicYear,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.deleteAcademicYear,
);

module.exports = router;
