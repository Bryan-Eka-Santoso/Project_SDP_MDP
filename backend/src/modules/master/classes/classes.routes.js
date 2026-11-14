const express = require('express');
const authenticate = require('../../../middlewares/authenticate');
const authorize = require('../../../middlewares/authorize');
const validate = require('../../../middlewares/validate');
const controller = require('./classes.controller');
const {
  idParams,
  memberParams,
  listClassesQuery,
  createClassBody,
  updateClassBody,
  setHomeroomTeacherBody,
  addClassMemberBody,
  moveClassMemberBody,
} = require('./classes.validation');

const router = express.Router();

router.use(authenticate);

// Baca daftar & detail kelas
router.get('/', validate({ query: listClassesQuery }), controller.list);
router.get('/:id', validate({ params: idParams }), controller.getById);

// F-MST-07: Kelola kelas (Admin)
router.post(
  '/',
  authorize('ADMIN'),
  validate({ body: createClassBody }),
  controller.create,
);
router.patch(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams, body: updateClassBody }),
  controller.update,
);
router.delete(
  '/:id',
  authorize('ADMIN'),
  validate({ params: idParams }),
  controller.remove,
);

// F-MST-08: Atur wali kelas (Admin)
router.patch(
  '/:id/homeroom-teacher',
  authorize('ADMIN'),
  validate({ params: idParams, body: setHomeroomTeacherBody }),
  controller.setHomeroomTeacher,
);

// F-MST-09: Atur siswa dalam kelas (tempatkan, pindahkan, hapus)
router.get('/:id/members', validate({ params: idParams }), controller.listMembers);
router.post(
  '/:id/members',
  authorize('ADMIN'),
  validate({ params: idParams, body: addClassMemberBody }),
  controller.addMembers,
);
router.post(
  '/:id/members/move',
  authorize('ADMIN'),
  validate({ params: idParams, body: moveClassMemberBody }),
  controller.moveMember,
);
router.delete(
  '/:id/members/:studentId',
  authorize('ADMIN'),
  validate({ params: memberParams }),
  controller.removeMember,
);

module.exports = router;
