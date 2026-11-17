const express = require("express");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./pelanggaran.controller");
const { inputBody } = require("./pelanggaran.validation");

const router = express.Router();

router.use(authenticate);
router.use(authorize("TEACHER"));

router.get("/students", controller.daftarSiswa);

router.get("/types", controller.daftarJenis);

router.post("/", validate({ body: inputBody }), controller.simpan);

module.exports = router;
