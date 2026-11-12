const express = require('express');
const authenticate = require('../../../middlewares/authenticate');
const authorize = require('../../../middlewares/authorize');
const validate = require('../../../middlewares/validate');
const controller = require('./students.controller');
const {
  idParams,
  guardianIdParams,
  listStudentsQuery,
  createStudentBody,
  updateStudentBody,
  linkGuardianBody,
} = require('./students.validation');

const router = express.Router();

router.use(authenticate);

// Baca daftar & detail siswa
router.get('/', validate({ query: listStudentsQuery }), controller.listStudents);
router.get('/:id', validate({ params: idParams }), controller.getStudentById);

// F-MST-04: Kelola siswa & relasi wali murid (Admin)
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createStudentBody }),
  controller.createStudent,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateStudentBody }),
  controller.updateStudent,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.deleteStudent,
);

// Relasi Siswa <-> Wali Murid
router.post(
  '/:id/guardians',
  authorize('ADMIN'),
  validate({ params: idParams, body: linkGuardianBody }),
  controller.linkGuardian,
);
router.delete(
  '/:id/guardians/:guardianId',
  authorize('ADMIN'),
  validate({ params: guardianIdParams }),
  controller.unlinkGuardian,
);

module.exports = router;
