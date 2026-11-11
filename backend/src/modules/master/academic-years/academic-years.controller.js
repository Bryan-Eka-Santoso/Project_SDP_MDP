const academicYearsService = require('./academic-years.service');
const { sendSuccess, sendPaginated } = require('../../../utils/response');

// ==========================================
// TAHUN AJARAN CONTROLLER
// ==========================================

async function listAcademicYears(req, res) {
  const { academicYears, total } = await academicYearsService.listAcademicYears(
    req.validated.query,
  );
  const { page, limit } = req.validated.query;
  sendPaginated(res, academicYears, { page, limit, total });
}

async function getActiveAcademicYear(req, res) {
  const result = await academicYearsService.getActiveAcademicYear();
  sendSuccess(res, result);
}

async function getAcademicYearById(req, res) {
  const result = await academicYearsService.getAcademicYearById(
    req.validated.params.id,
  );
  sendSuccess(res, result);
}

async function createAcademicYear(req, res) {
  const result = await academicYearsService.createAcademicYear(
    req.validated.body,
  );
  sendSuccess(res, result, 201);
}

async function updateAcademicYear(req, res) {
  const result = await academicYearsService.updateAcademicYear(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function setActiveAcademicYear(req, res) {
  const result = await academicYearsService.setActiveAcademicYear(
    req.validated.params.id,
  );
  sendSuccess(res, result);
}

async function deleteAcademicYear(req, res) {
  await academicYearsService.deleteAcademicYear(req.validated.params.id);
  res.status(204).send();
}

// ==========================================
// SEMESTER CONTROLLER
// ==========================================

async function listSemesters(req, res) {
  const { semesters, total } = await academicYearsService.listSemesters(
    req.validated.query,
  );
  const { page, limit } = req.validated.query;
  sendPaginated(res, semesters, { page, limit, total });
}

async function getActiveSemester(req, res) {
  const result = await academicYearsService.getActiveSemester();
  sendSuccess(res, result);
}

async function getSemesterById(req, res) {
  const result = await academicYearsService.getSemesterById(
    req.validated.params.id,
  );
  sendSuccess(res, result);
}

async function createSemester(req, res) {
  const result = await academicYearsService.createSemester(req.validated.body);
  sendSuccess(res, result, 201);
}

async function updateSemester(req, res) {
  const result = await academicYearsService.updateSemester(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function setActiveSemester(req, res) {
  const result = await academicYearsService.setActiveSemester(
    req.validated.params.id,
  );
  sendSuccess(res, result);
}

async function deleteSemester(req, res) {
  await academicYearsService.deleteSemester(req.validated.params.id);
  res.status(204).send();
}

module.exports = {
  listAcademicYears,
  getActiveAcademicYear,
  getAcademicYearById,
  createAcademicYear,
  updateAcademicYear,
  setActiveAcademicYear,
  deleteAcademicYear,
  listSemesters,
  getActiveSemester,
  getSemesterById,
  createSemester,
  updateSemester,
  setActiveSemester,
  deleteSemester,
};
