const service = require('./elective.allocation.service');
const { sendSuccess } = require('../../utils/response');

async function preview(req, res) {
  sendSuccess(res, await service.preview(req.validated.query));
}
async function apply(req, res) {
  sendSuccess(res, await service.apply(req.validated.body), 201);
}
async function trends(req, res) {
  sendSuccess(res, await service.trends());
}
module.exports = { preview, apply, trends };
