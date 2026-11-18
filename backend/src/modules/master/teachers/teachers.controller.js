const teachersService = require('./teachers.service');
const teachersImportService = require('./teachers.import.service');
const { sendSuccess, sendPaginated } = require('../../../utils/response');

async function list(req, res) {
  const { teachers, total } = await teachersService.listTeachers(
    req.validated.query,
  );
  const { page, limit } = req.validated.query;
  sendPaginated(res, teachers, { page, limit, total });
}

async function getById(req, res) {
  const teacher = await teachersService.getTeacherById(req.validated.params.id);
  sendSuccess(res, teacher);
}

async function create(req, res) {
  const teacher = await teachersService.createTeacher(req.validated.body);
  sendSuccess(res, teacher, 201);
}

async function update(req, res) {
  const teacher = await teachersService.updateTeacher(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, teacher);
}

async function remove(req, res) {
  await teachersService.deleteTeacher(req.validated.params.id);
  res.status(204).send();
}

async function importTeachers(req, res) {
  const result = await teachersImportService.importTeachers({ buffer: req.file.buffer });
  sendSuccess(res, result, 201);
}

module.exports = {
  list,
  getById,
  create,
  update,
  remove,
  importTeachers,
};
