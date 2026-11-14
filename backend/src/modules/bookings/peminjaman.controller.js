const service = require("./peminjaman.service");

const { sendSuccess, sendPaginated } = require("../../utils/response");

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

async function approve(req, res) {
  const result = await service.approve(req.validated.params.id, req.user.id);

  return sendSuccess(res, result);
}

async function reject(req, res) {
  const result = await service.reject(req.validated.params.id, req.user.id);

  return sendSuccess(res, result);
}

module.exports = {
  list,
  getById,
  approve,
  reject,
};
