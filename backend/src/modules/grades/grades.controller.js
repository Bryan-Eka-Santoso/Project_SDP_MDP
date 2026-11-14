const service = require('./grades.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function listComponents(req, res) {
  sendSuccess(res, await service.listComponents(req.user.id, req.validated.params.assignmentId));
}
async function createComponent(req, res) {
  sendSuccess(res, await service.createComponent(req.user.id, req.validated.params.assignmentId, req.validated.body), 201);
}
async function updateComponent(req, res) {
  sendSuccess(res, await service.updateComponent(req.user.id, req.validated.params.id, req.validated.body));
}
async function saveScore(req, res) {
  sendSuccess(res, await service.saveScore(req.user.id, req.validated.body));
}
async function listScores(req, res) {
  const query = req.validated.query;
  const result = await service.listScores(req.user.id, query);
  sendPaginated(res, result.data, { page: query.page, limit: query.limit, total: result.total });
}
module.exports = { listComponents, createComponent, updateComponent, saveScore, listScores };
