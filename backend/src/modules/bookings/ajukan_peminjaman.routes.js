const express = require("express");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./ajukan_peminjaman.controller");

const { pengajuanBody } = require("./ajukan_peminjaman.validation");

const router = express.Router();

router.use(authenticate);
router.use(authorize("STUDENT", "TEACHER"));

router.get("/facilities", controller.daftarFasilitas);

router.post("/", validate({ body: pengajuanBody }), controller.ajukan);

module.exports = router;
