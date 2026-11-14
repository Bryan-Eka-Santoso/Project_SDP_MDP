const service = require('./leave.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');
async function children(req, res) {
  sendSuccess(res, await service.children(req.user.id));
}
async function list(req, res) {
  const result = await service.list(req.user.id, req.validated.query);
  sendPaginated(res, result.data, result.meta);
}
async function create(req, res) {
  sendSuccess(res, await service.create(req.user.id, req.validated.body), 201);
}
module.exports = { children, list, create };
