const express = require("express");

const authenticate = require("../../../middlewares/authenticate");
const authorize = require("../../../middlewares/authorize");
const validate = require("../../../middlewares/validate");

const controller = require("./jenis_pelanggaran.controller");

const {
  idParams,
  listQuery,
  createBody,
  updateBody,
} = require("./jenis_pelanggaran.validation");

const router = express.Router();

// Hanya ADMIN
router.use(authenticate);
router.use(authorize("ADMIN"));

// GET semua jenis pelanggaran
router.get("/", validate({ query: listQuery }), controller.list);

// GET detail
router.get("/:id", validate({ params: idParams }), controller.getById);

// POST tambah
router.post("/", validate({ body: createBody }), controller.create);

// PATCH edit
router.patch(
  "/:id",
  validate({
    params: idParams,
    body: updateBody,
  }),
  controller.update,
);

// DELETE hapus
router.delete("/:id", validate({ params: idParams }), controller.remove);

module.exports = router;
