const express = require('express');
const { z } = require('zod');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const { paginationQuery } = require('../../utils/pagination');
const controller = require('./grades.read.controller');

const router = express.Router();
const studentParams = z.object({ studentId: z.coerce.number().int().positive() });
const assignmentParams = z.object({ assignmentId: z.coerce.number().int().positive() });

// NIL-03: Data siswa sendiri dan data anak wali murid.
router.get('/me/scores', authenticate, authorize('STUDENT'), controller.myScores);
router.get('/students/:studentId/scores', authenticate, authorize('STUDENT', 'GUARDIAN'),
  validate({ params: studentParams }), controller.studentScores);

// NIL-04: Guru hanya dapat melihat rekap kelas/mapel penugasannya sendiri.
router.get('/teaching-assignments/:assignmentId/grade-summary', authenticate,
  authorize('TEACHER'), validate({ params: assignmentParams, query: paginationQuery }),
  controller.classSummary);

module.exports = router;
