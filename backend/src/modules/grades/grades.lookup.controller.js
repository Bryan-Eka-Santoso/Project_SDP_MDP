const service = require('./grades.lookup.service');
const { sendSuccess } = require('../../utils/response');

async function assignments(req, res) {
  sendSuccess(res, await service.assignments(req.user.id));
}
async function students(req, res) {
  sendSuccess(res, await service.students(req.user.id, req.validated.params.assignmentId));
}
module.exports = { assignments, students };
