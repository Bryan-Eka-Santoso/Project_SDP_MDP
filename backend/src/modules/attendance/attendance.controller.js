const service = require('./attendance.service');
const { sendSuccess } = require('../../utils/response');

async function listSlots(req, res) {
  sendSuccess(res, await service.listSlots(req.user.id));
}
async function listStudents(req, res) {
  sendSuccess(res, await service.listStudents(req.user.id, req.validated.params.slotId));
}
async function getSession(req, res) {
  sendSuccess(res, await service.getSession(
    req.user.id, req.validated.params.slotId, req.validated.query.date,
  ));
}
async function saveSession(req, res) {
  sendSuccess(res, await service.saveSession(
    req.user.id, req.validated.params.slotId, req.validated.body,
  ));
}

module.exports = { listSlots, listStudents, getSession, saveSession };
