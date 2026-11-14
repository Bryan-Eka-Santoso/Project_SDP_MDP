const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const controller = require('./attendance.controller');
const schema = require('./attendance.validation');

const router = express.Router();

// Middleware dipasang per-route agar path lain tetap menghasilkan 404.
router.get('/me/attendance-schedule-slots',
  authenticate, authorize('TEACHER'), controller.listSlots);

router.get('/schedule-slots/:slotId/students',
  authenticate, authorize('TEACHER'),
  validate({ params: schema.slotParams }), controller.listStudents);

router.get('/schedule-slots/:slotId/attendance',
  authenticate, authorize('TEACHER'),
  validate({ params: schema.slotParams, query: schema.dateQuery }), controller.getSession);

router.post('/schedule-slots/:slotId/attendance',
  authenticate, authorize('TEACHER'),
  validate({ params: schema.slotParams, body: schema.saveAttendanceBody }),
  controller.saveSession);

module.exports = router;
