const express = require("express");
const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");
const controller = require("./leave.controller");
const schema = require("./leave.validation");
const { z } = require("zod");
const { parseAttachment } = require("./leave.attachment");
const router = express.Router();
router.get(
  "/me/children",
  authenticate,
  authorize("GUARDIAN"),
  controller.children,
);
router.get(
  "/leave-requests",
  authenticate,
  authorize("GUARDIAN"),
  validate({ query: schema.leaveListQuery }),
  controller.list,
);
router.post(
  "/leave-requests",
  authenticate,
  authorize("GUARDIAN"),
  parseAttachment,
  validate({ body: schema.createLeaveBody }),
  controller.create,
);
module.exports = router;
