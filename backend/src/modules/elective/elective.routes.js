const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const controller = require('./elective.controller');
const {
  idParams,
  listSubjectPackagesQuery,
  createSubjectPackageBody,
  updateSubjectPackageBody,
} = require('./elective.validation');

const router = express.Router();

router.use(authenticate);

// F-MPP-03: Master paket mata pelajaran pilihan per tahun ajaran
router.get(
  '/',
  validate({ query: listSubjectPackagesQuery }),
  controller.listSubjectPackages,
);
router.get(
  '/:id',
  validate({ params: idParams }),
  controller.getSubjectPackageById,
);
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createSubjectPackageBody }),
  controller.createSubjectPackage,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateSubjectPackageBody }),
  controller.updateSubjectPackage,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.deleteSubjectPackage,
);

module.exports = router;
