const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const schema = require('./leave.review.validation');
const controller = require('./leave.review.controller');

const router = express.Router();
// Wali kelas merupakan Guru yang ditugaskan pada kelas aktif, bukan role JWT terpisah.
router.get('/homeroom/leave-requests/pending', authenticate, authorize('TEACHER'),
  validate({ query: schema.pendingQuery }), controller.pending);
router.patch('/homeroom/leave-requests/:requestId/decision', authenticate, authorize('TEACHER'),
  validate({ params: schema.idParams, body: schema.decisionBody }), controller.decide);

router.get('/homeroom/leave-requests/:requestId/attachment', authenticate, authorize('TEACHER'),
  validate({ params: schema.idParams }), controller.downloadAttachment);

module.exports = router;
