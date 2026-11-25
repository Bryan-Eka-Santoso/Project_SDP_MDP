const express = require("express");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./pilih_paket.controller");
const { pilihPaketBody } = require("./pilih_paket.validation");

const router = express.Router();

router.use(authenticate);
router.use(authorize("STUDENT"));

router.get("/", controller.daftarPaket);

router.post("/", validate({ body: pilihPaketBody }), controller.pilihPaket);

module.exports = router;
