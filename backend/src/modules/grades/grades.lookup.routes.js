const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const schemas = require('./grades.validation');
const controller = require('./grades.lookup.controller');

const router = express.Router();
router.get('/me/grade-teaching-assignments', authenticate, authorize('TEACHER'), controller.assignments);
router.get('/teaching-assignments/:assignmentId/students', authenticate, authorize('TEACHER'), validate({ params: schemas.assignmentParams }), controller.students);
module.exports = router;
