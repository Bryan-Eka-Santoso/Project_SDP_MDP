const classesService = require('./classes.service');
const { sendSuccess, sendPaginated } = require('../../../utils/response');

async function list(req, res) {
  const { classes, total } = await classesService.listClasses(
    req.validated.query,
  );
  const { page, limit } = req.validated.query;
  sendPaginated(res, classes, { page, limit, total });
}

async function getById(req, res) {
  const result = await classesService.getClassById(req.validated.params.id);
  sendSuccess(res, result);
}

async function create(req, res) {
  const result = await classesService.createClass(req.validated.body);
  sendSuccess(res, result, 201);
}

async function update(req, res) {
  const result = await classesService.updateClass(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function setHomeroomTeacher(req, res) {
  const result = await classesService.setHomeroomTeacher(
    req.validated.params.id,
    req.validated.body.homeroomTeacherId,
  );
  sendSuccess(res, result);
}

async function remove(req, res) {
  await classesService.deleteClass(req.validated.params.id);
  res.status(204).send();
}

async function listMembers(req, res) {
  const result = await classesService.listClassMembers(req.validated.params.id);
  sendSuccess(res, result);
}

async function addMembers(req, res) {
  const result = await classesService.addClassMembers(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result, 201);
}

async function moveMember(req, res) {
  const result = await classesService.moveClassMember(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function removeMember(req, res) {
  await classesService.removeClassMember(
    req.validated.params.id,
    req.validated.params.studentId,
  );
  res.status(204).send();
}

module.exports = {
  list,
  getById,
  create,
  update,
  setHomeroomTeacher,
  remove,
  listMembers,
  addMembers,
  moveMember,
  removeMember,
};
