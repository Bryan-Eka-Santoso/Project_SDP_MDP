const service = require('./attendance.read.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function mySummary(req, res) {
  sendSuccess(res, await service.ownSummary(req.user, req.validated.query));
}
async function studentSummary(req, res) {
  sendSuccess(res, await service.studentSummary(
    req.user, req.validated.params.studentId, req.validated.query,
  ));
}
async function classSummary(req, res) {
  sendSuccess(res, await service.teacherClassSummary(
    req.user, req.validated.params.assignmentId, req.validated.query,
  ));
}
async function adminJournals(req, res) {
  const query = req.validated.query;
  const { rows, total } = await service.adminJournals(query);
  sendPaginated(res, rows, { page: query.page, limit: query.limit, total });
}
module.exports = { mySummary, studentSummary, classSummary, adminJournals };
