const express = require("express");

const authenticate = require("../../middlewares/authenticate");
const authorize = require("../../middlewares/authorize");
const validate = require("../../middlewares/validate");

const controller = require("./riwayat_peminjaman.controller");

const { paginationQuery } = require("../../utils/pagination");

const { z } = require("zod");

const listQuery = paginationQuery.extend({
  status: z.enum(["PENDING", "APPROVED", "REJECTED", "RETURNED"]).optional(),
});

const router = express.Router();

router.use(authenticate);
router.use(authorize("STUDENT", "TEACHER", "ADMIN"));

router.get("/", validate({ query: listQuery }), controller.daftar);

module.exports = router;
