const teachingAssignmentsService = require('./teaching-assignments.service');
const { sendSuccess, sendPaginated } = require('../../../utils/response');

async function list(req, res) {
  const { teachingAssignments, total } =
    await teachingAssignmentsService.listTeachingAssignments(req.validated.query);
  const { page, limit } = req.validated.query;
  sendPaginated(res, teachingAssignments, { page, limit, total });
}

async function getById(req, res) {
  const result = await teachingAssignmentsService.getTeachingAssignmentById(
    req.validated.params.id,
  );
  sendSuccess(res, result);
}

async function create(req, res) {
  const result = await teachingAssignmentsService.createTeachingAssignment(
    req.validated.body,
  );
  sendSuccess(res, result, 201);
}

async function update(req, res) {
  const result = await teachingAssignmentsService.updateTeachingAssignment(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function remove(req, res) {
  await teachingAssignmentsService.deleteTeachingAssignment(
    req.validated.params.id,
  );
  res.status(204).send();
}

module.exports = {
  list,
  getById,
  create,
  update,
  remove,
};
