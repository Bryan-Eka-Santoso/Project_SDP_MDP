const router = require("express").Router();

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./isi_survei.controller");

const { idParams, submitBody } = require("./isi_survei.validation");

router.use(authenticate);
router.use(authorize("STUDENT"));

router.get("/", controller.list);

router.get("/:id", validate({ params: idParams }), controller.detail);

router.post(
  "/:id/responses",
  validate({
    params: idParams,
    body: submitBody,
  }),
  controller.submit,
);

module.exports = router;
