const electiveService = require('./elective.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function listSubjectPackages(req, res) {
  const { packages, total } = await electiveService.listSubjectPackages(req.validated.query);
  const { page, limit } = req.validated.query;
  sendPaginated(res, packages, { page, limit, total });
}

async function getSubjectPackageById(req, res) {
  const result = await electiveService.getSubjectPackageById(req.validated.params.id);
  sendSuccess(res, result);
}

async function createSubjectPackage(req, res) {
  const result = await electiveService.createSubjectPackage(req.validated.body);
  sendSuccess(res, result, 201);
}

async function updateSubjectPackage(req, res) {
  const result = await electiveService.updateSubjectPackage(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function deleteSubjectPackage(req, res) {
  await electiveService.deleteSubjectPackage(req.validated.params.id);
  res.status(204).send();
}

module.exports = {
  listSubjectPackages,
  getSubjectPackageById,
  createSubjectPackage,
  updateSubjectPackage,
  deleteSubjectPackage,
};
