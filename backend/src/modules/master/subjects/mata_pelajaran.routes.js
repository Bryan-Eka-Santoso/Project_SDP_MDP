const express = require("express");

const authenticate = require("../../../middlewares/authenticate");
const authorize = require("../../../middlewares/authorize");
const validate = require("../../../middlewares/validate");

const controller = require("./mata_pelajaran.controller");

const {
  idParams,
  listQuery,
  subjectBody,
} = require("./mata_pelajaran.validation");

const router = express.Router();

// Semua endpoint hanya untuk ADMIN
router.use(authenticate, authorize("ADMIN"));

// GET semua mata pelajaran
router.get("/", validate({ query: listQuery }), controller.list);

// GET detail
router.get("/:id", validate({ params: idParams }), controller.getById);

// POST tambah
router.post("/", validate({ body: subjectBody }), controller.create);

// PATCH edit
router.patch(
  "/:id",
  validate({
    params: idParams,
    body: subjectBody,
  }),
  controller.update,
);

// DELETE hapus
router.delete("/:id", validate({ params: idParams }), controller.remove);

module.exports = router;
