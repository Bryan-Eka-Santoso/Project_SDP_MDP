const express = require("express");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./profil_siswa.controller");

const { studentParams } = require("./profil_siswa.validation");

const router = express.Router();

router.use(authenticate);
router.use(authorize("TEACHER"));

router.get("/students", controller.daftarSiswa);

router.get(
  "/students/:studentId",
  validate({ params: studentParams }),
  controller.detailSiswa,
);

module.exports = router;
