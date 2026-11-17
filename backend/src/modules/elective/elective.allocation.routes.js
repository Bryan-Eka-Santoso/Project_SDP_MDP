const express = require("express");
const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");
const controller = require("./elective.allocation.controller");
const schema = require("./elective.allocation.validation");

const router = express.Router();

// MPP-05: Preview pembagian kelas
router.get(
  "/admin/elective/allocations/preview",
  authenticate,
  authorize("ADMIN"),
  validate({ query: schema.allocationQuery }),
  controller.preview,
);

// MPP-05: Konfirmasi pembagian kelas
router.post(
  "/admin/elective/allocations",
  authenticate,
  authorize("ADMIN"),
  validate({ body: schema.allocationBody }),
  controller.apply,
);

// MPP-06: Tren peminat paket mata pelajaran
router.get(
  "/admin/elective/package-trends",
  authenticate,
  authorize("ADMIN"),
  controller.trends,
);

module.exports = router;
