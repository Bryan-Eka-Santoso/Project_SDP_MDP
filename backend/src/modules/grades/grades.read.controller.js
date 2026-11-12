const service = require('./grades.read.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function myScores(req, res) {
  sendSuccess(res, await service.ownStudentScores(req.user));
}
async function studentScores(req, res) {
  sendSuccess(res, await service.studentScores(req.user, req.validated.params.studentId));
}
async function classSummary(req, res) {
  const { assignmentId } = req.validated.params;
  const { page, limit } = req.validated.query;
  const result = await service.classSummary(req.user, assignmentId, page, limit);
  // Judul kelas di header Android diperoleh dari daftar penugasan yang sudah ada.
  sendPaginated(res, result.data, { page, limit, total: result.total });
}
module.exports = { myScores, studentScores, classSummary };
