const express = require("express");
const { z } = require("zod");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./baca_buku_penghubung.controller");

const router = express.Router();

const studentParams = z.object({
  studentId: z.coerce.number().int().positive(),
});

router.use(authenticate);
router.use(authorize("TEACHER", "GUARDIAN"));

router.get("/students", controller.daftarSiswa);

router.get(
  "/students/:studentId/notes",
  validate({ params: studentParams }),
  controller.riwayat,
);

module.exports = router;
