const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const schema = require('./grades.progress.validation');
const controller = require('./grades.progress.controller');

const router = express.Router();

// NIL-05: Guru membuat dan memperbarui catatan berdasarkan penugasan miliknya.
router.post('/progress-notes', authenticate, authorize('TEACHER'),
  validate({ body: schema.createBody }), controller.create);
router.patch('/progress-notes/:noteId', authenticate, authorize('TEACHER'),
  validate({ params: schema.noteParams, body: schema.updateBody }), controller.update);
router.get('/teaching-assignments/:assignmentId/progress-notes', authenticate, authorize('TEACHER'),
  validate({ params: schema.assignmentParams, query: schema.assignmentListQuery }), controller.byAssignment);

// Siswa dan wali hanya melihat catatan yang berhak diakses.
router.get('/me/progress-notes', authenticate, authorize('STUDENT'),
  validate({ query: schema.studentListQuery }), controller.myNotes);
router.get('/students/:studentId/progress-notes', authenticate, authorize('STUDENT', 'GUARDIAN'),
  validate({ params: schema.studentParams, query: schema.studentListQuery }), controller.byStudent);

module.exports = router;
