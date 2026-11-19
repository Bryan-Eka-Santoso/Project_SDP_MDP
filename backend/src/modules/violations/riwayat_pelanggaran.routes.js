const express = require("express");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./riwayat_pelanggaran.controller");

const { siswaParams } = require("./riwayat_pelanggaran.validation");

const router = express.Router();

router.use(authenticate);

router.use(authorize("ADMIN", "TEACHER", "STUDENT", "GUARDIAN"));

// Daftar siswa sesuai hak akses.
router.get("/students", controller.daftarSiswa);

// Akumulasi dan rincian pelanggaran satu siswa.
router.get(
  "/students/:studentId",
  validate({
    params: siswaParams,
  }),
  controller.getRiwayat,
);

module.exports = router;
