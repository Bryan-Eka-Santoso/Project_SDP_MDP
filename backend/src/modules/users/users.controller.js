const usersService = require('./users.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function list(req, res) {
  const { users, total } = await usersService.listUsers(req.validated.query);
  const { page, limit } = req.validated.query;
  sendPaginated(res, users, { page, limit, total });
}

async function getById(req, res) {
  const user = await usersService.getUserById(req.validated.params.id);
  sendSuccess(res, user);
}

async function create(req, res) {
  const user = await usersService.createUser(req.validated.body);
  sendSuccess(res, user, 201);
}

async function update(req, res) {
  const user = await usersService.updateUser(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, user);
}

async function updateRole(req, res) {
  const user = await usersService.updateRole(
    req.user.id,
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, user);
}

async function updateStatus(req, res) {
  const user = await usersService.updateStatus(
    req.user.id,
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, user);
}

async function resetPassword(req, res) {
  const result = await usersService.resetPassword(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function remove(req, res) {
  await usersService.deleteUser(req.user.id, req.validated.params.id);
  res.status(204).send();
}

module.exports = {
  list,
  getById,
  create,
  update,
  updateRole,
  updateStatus,
  resetPassword,
  remove,
};
