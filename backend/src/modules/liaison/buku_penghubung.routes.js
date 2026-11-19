const express = require("express");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./buku_penghubung.controller");

const { classParams, createBody } = require("./buku_penghubung.validation");

const router = express.Router();

router.use(authenticate);
router.use(authorize("TEACHER"));

router.get("/classes", controller.daftarKelas);

router.get(
  "/classes/:classId/students",
  validate({ params: classParams }),
  controller.daftarSiswa,
);

router.post("/", validate({ body: createBody }), controller.simpan);

module.exports = router;
