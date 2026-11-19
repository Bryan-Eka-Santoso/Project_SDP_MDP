const service = require('./grades.progress.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function create(req, res) {
  sendSuccess(res, await service.create(req.user.id, req.validated.body), 201);
}
async function update(req, res) {
  sendSuccess(res, await service.update(req.user.id, req.validated.params.noteId, req.validated.body));
}
async function byAssignment(req, res) {
  const { page, limit } = req.validated.query;
  const result = await service.listByAssignment(req.user.id, req.validated.params.assignmentId, req.validated.query);
  sendPaginated(res, result.data, { page, limit, total: result.total });
}
async function byStudent(req, res) {
  const { page, limit } = req.validated.query;
  const result = await service.listByStudent(req.user, req.validated.params.studentId, req.validated.query);
  sendPaginated(res, result.data, { page, limit, total: result.total });
}
async function myNotes(req, res) {
  const { page, limit } = req.validated.query;
  const result = await service.listOwn(req.user, req.validated.query);
  sendPaginated(res, result.data, { page, limit, total: result.total });
}

module.exports = { create, update, byAssignment, byStudent, myNotes };
