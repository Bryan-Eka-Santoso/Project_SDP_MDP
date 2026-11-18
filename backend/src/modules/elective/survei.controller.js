const service = require("./survei.service");
const { sendSuccess } = require("../../utils/response");

async function lookups(req, res) {
  return sendSuccess(res, await service.lookups());
}

async function list(req, res) {
  return sendSuccess(res, await service.list());
}

async function create(req, res) {
  return sendSuccess(res, await service.create(req.validated.body), 201);
}

async function publish(req, res) {
  return sendSuccess(res, await service.publish(req.validated.params.id));
}

async function close(req, res) {
  return sendSuccess(res, await service.close(req.validated.params.id));
}

async function recap(req, res) {
  return sendSuccess(res, await service.recap(req.validated.params.id));
}

module.exports = {
  lookups,
  list,
  create,
  publish,
  close,
  recap,
};
