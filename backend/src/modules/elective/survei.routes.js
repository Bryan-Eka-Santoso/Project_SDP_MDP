const router = require("express").Router();

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./survei.controller");

const { idParams, createBody } = require("./survei.validation");

router.use(authenticate);
router.use(authorize("ADMIN"));

router.get("/lookups", controller.lookups);

router.get("/", controller.list);

router.post("/", validate({ body: createBody }), controller.create);

router.get("/:id/recap", validate({ params: idParams }), controller.recap);

router.post("/:id/publish", validate({ params: idParams }), controller.publish);

router.post("/:id/close", validate({ params: idParams }), controller.close);

module.exports = router;
