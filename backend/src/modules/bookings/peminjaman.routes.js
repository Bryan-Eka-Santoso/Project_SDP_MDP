const express = require("express");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./peminjaman.controller");

const { idParams, listQuery } = require("./peminjaman.validation");

const router = express.Router();

// Semua endpoint hanya untuk ADMIN.
router.use(authenticate);
router.use(authorize("ADMIN"));

// Daftar pengajuan.
router.get("/", validate({ query: listQuery }), controller.list);

// Detail pengajuan.
router.get("/:id", validate({ params: idParams }), controller.getById);

// Setujui pengajuan.
router.post("/:id/approve", validate({ params: idParams }), controller.approve);

// Tolak pengajuan.
router.post("/:id/reject", validate({ params: idParams }), controller.reject);

module.exports = router;
