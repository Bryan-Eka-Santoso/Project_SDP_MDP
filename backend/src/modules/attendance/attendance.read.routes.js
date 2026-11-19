const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const schema = require('./attendance.read.validation');
const controller = require('./attendance.read.controller');

const router = express.Router();

// ABS-03: Data kehadiran siswa sendiri atau anak yang terhubung ke wali.
router.get('/me/attendance-summary', authenticate, authorize('STUDENT'),
  validate({ query: schema.summaryQuery }), controller.mySummary);
router.get('/students/:studentId/attendance-summary', authenticate,
  authorize('STUDENT', 'GUARDIAN'),
  validate({ params: schema.studentParams, query: schema.summaryQuery }),
  controller.studentSummary);
// ABS-03: Rekap siswa untuk kelas/mapel yang benar-benar diajar guru.
router.get('/teaching-assignments/:assignmentId/attendance-summary', authenticate,
  authorize('TEACHER'),
  validate({ params: schema.assignmentParams, query: schema.summaryQuery }),
  controller.classSummary);
// ABS-04: Admin dapat memantau jurnal pelajaran seluruh sekolah.
router.get('/admin/teaching-journals', authenticate, authorize('ADMIN'),
  validate({ query: schema.journalQuery }), controller.adminJournals);

module.exports = router;
