const express = require("express");
const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");
const controller = require("./grades.controller");
const schemas = require("./grades.validation");

const router = express.Router();

// NIL-01: Komponen penilaian per penugasan guru
router.get(
  "/teaching-assignments/:assignmentId/grade-components",
  authenticate,
  authorize("TEACHER"),
  validate({ params: schemas.assignmentParams }),
  controller.listComponents,
);

router.post(
  "/teaching-assignments/:assignmentId/grade-components",
  authenticate,
  authorize("TEACHER"),
  validate({
    params: schemas.assignmentParams,
    body: schemas.componentBody,
  }),
  controller.createComponent,
);

router.patch(
  "/grade-components/:id",
  authenticate,
  authorize("TEACHER"),
  validate({
    params: schemas.componentParams,
    body: schemas.componentUpdateBody,
  }),
  controller.updateComponent,
);

// NIL-02: Input dan edit nilai siswa
router.post(
  "/scores",
  authenticate,
  authorize("TEACHER"),
  validate({ body: schemas.scoreBody }),
  controller.saveScore,
);

router.get(
  "/scores",
  authenticate,
  authorize("TEACHER"),
  validate({ query: schemas.scoresQuery }),
  controller.listScores,
);

// Rincian Tugas 1..n dan Ulangan 1..n
router.get(
  '/teaching-assignments/:assignmentId/grade-assessments',
  authenticate, authorize('TEACHER'),
  validate({ params: schemas.assignmentParams }), controller.listAssessments,
);
router.post(
  '/teaching-assignments/:assignmentId/grade-assessments',
  authenticate, authorize('TEACHER'),
  validate({ params: schemas.assignmentParams, body: schemas.assessmentCreateBody }), controller.createAssessment,
);
router.put(
  '/grade-assessments/:id/scores',
  authenticate, authorize('TEACHER'),
  validate({ params: schemas.assessmentParams, body: schemas.assessmentScoreBody }), controller.saveAssessmentScore,
);
module.exports = router;
