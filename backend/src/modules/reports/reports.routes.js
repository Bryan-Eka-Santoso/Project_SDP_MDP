const express = require('express');
const authenticate = require('../../middlewares/authenticate');
const authorize = require('../../middlewares/authorize');
const validate = require('../../middlewares/validate');
const controller = require('./reports.controller');
const { querySchema } = require('./reports.service');

const router = express.Router();

// F-LAP-*. Laporan dan ekspor hanya admin (docs/PERMISSIONS.md).
const admin = [authenticate, authorize('ADMIN')];

// Filter berbeda per jenis laporan, jadi schema dipilih dari :type; jenis tak dikenal → 404.
const reportQuery = (mode) => (req, res, next) => {
  try {
    return validate({ query: querySchema(req.params.type, mode) })(req, res, next);
  } catch (err) {
    return next(err);
  }
};

router.get('/reports/:type', ...admin, reportQuery('list'), controller.getReport);
router.get('/reports/:type/export', ...admin, reportQuery('export'), controller.exportReport);

module.exports = router;
