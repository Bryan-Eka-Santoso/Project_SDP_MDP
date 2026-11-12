const studentsService = require('./students.service');
const { sendSuccess, sendPaginated } = require('../../../utils/response');

// ==========================================
// SISWA CONTROLLER
// ==========================================

async function listStudents(req, res) {
  const { students, total } = await studentsService.listStudents(
    req.validated.query,
  );
  const { page, limit } = req.validated.query;
  sendPaginated(res, students, { page, limit, total });
}

async function getStudentById(req, res) {
  const result = await studentsService.getStudentById(req.validated.params.id);
  sendSuccess(res, result);
}

async function createStudent(req, res) {
  const result = await studentsService.createStudent(req.validated.body);
  sendSuccess(res, result, 201);
}

async function updateStudent(req, res) {
  const result = await studentsService.updateStudent(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function linkGuardian(req, res) {
  const result = await studentsService.linkGuardian(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result, 201);
}

async function unlinkGuardian(req, res) {
  await studentsService.unlinkGuardian(
    req.validated.params.id,
    req.validated.params.guardianId,
  );
  res.status(204).send();
}

async function deleteStudent(req, res) {
  await studentsService.deleteStudent(req.validated.params.id);
  res.status(204).send();
}

// ==========================================
// WALI MURID CONTROLLER
// ==========================================

async function listGuardians(req, res) {
  const { guardians, total } = await studentsService.listGuardians(
    req.validated.query,
  );
  const { page, limit } = req.validated.query;
  sendPaginated(res, guardians, { page, limit, total });
}

async function getGuardianById(req, res) {
  const result = await studentsService.getGuardianById(req.validated.params.id);
  sendSuccess(res, result);
}

async function createGuardian(req, res) {
  const result = await studentsService.createGuardian(req.validated.body);
  sendSuccess(res, result, 201);
}

async function updateGuardian(req, res) {
  const result = await studentsService.updateGuardian(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function deleteGuardian(req, res) {
  await studentsService.deleteGuardian(req.validated.params.id);
  res.status(204).send();
}

module.exports = {
  listStudents,
  getStudentById,
  createStudent,
  updateStudent,
  linkGuardian,
  unlinkGuardian,
  deleteStudent,
  listGuardians,
  getGuardianById,
  createGuardian,
  updateGuardian,
  deleteGuardian,
};
