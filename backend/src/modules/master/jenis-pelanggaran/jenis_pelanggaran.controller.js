const service = require("./jenis_pelanggaran.service");

const { sendSuccess, sendPaginated } = require("../../../utils/response");

async function list(req, res) {
  const query = req.validated.query;

  const { items, total } = await service.list(query);

  return sendPaginated(res, items, {
    page: query.page,
    limit: query.limit,
    total,
  });
}

async function getById(req, res) {
  const result = await service.getById(req.validated.params.id);

  return sendSuccess(res, result);
}

async function create(req, res) {
  const result = await service.create(req.validated.body);

  return sendSuccess(res, result, 201);
}

async function update(req, res) {
  const result = await service.update(
    req.validated.params.id,
    req.validated.body,
  );

  return sendSuccess(res, result);
}

async function remove(req, res) {
  await service.remove(req.validated.params.id);

  return res.status(204).send();
}

module.exports = {
  list,
  getById,
  create,
  update,
  remove,
};
