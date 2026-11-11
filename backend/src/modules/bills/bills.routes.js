const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const { uploadXlsx } = require('../../middlewares/upload');
const controller = require('./bills.controller');
const { studentParams, studentBillsQuery, listBillsQuery } = require('./bills.validation');

const router = express.Router();

// F-BYR-01. Hanya admin; upload dibaca setelah authenticate + authorize.
router.post('/bills/import', authenticate, authorize('ADMIN'), uploadXlsx('file'), controller.importBills);

// F-BYR-02. Daftar lintas siswa: admin (semua) dan wali kelas (kelas binaan; guru lain 403 di service).
router.get('/bills', authenticate, authorize('ADMIN', 'TEACHER'), validate({ query: listBillsQuery }), controller.listBills);

// F-BYR-02. Semua role boleh memanggil; cakupan datanya (SELF/CHILD/HOMEROOM/ALL) dicek di policy.
router.get(
  '/students/:studentId/bills',
  authenticate,
  validate({ params: studentParams, query: studentBillsQuery }),
  controller.listStudentBills,
);

module.exports = router;
