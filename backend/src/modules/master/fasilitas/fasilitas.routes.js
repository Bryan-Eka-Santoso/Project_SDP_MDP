const express = require("express");

const authenticate = require("../../../middlewares/authenticate");
const authorize = require("../../../middlewares/authorize");
const validate = require("../../../middlewares/validate");

const controller = require("./fasilitas.controller");

const {
  idParams,
  listQuery,
  createBody,
  updateBody,
} = require("./fasilitas.validation");

const router = express.Router();

router.use(authenticate);
router.use(authorize("ADMIN"));

router.get("/", validate({ query: listQuery }), controller.list);

router.get("/:id", validate({ params: idParams }), controller.getById);

router.post("/", validate({ body: createBody }), controller.create);

router.patch(
  "/:id",
  validate({
    params: idParams,
    body: updateBody,
  }),
  controller.update,
);

router.delete("/:id", validate({ params: idParams }), controller.remove);

module.exports = router;
