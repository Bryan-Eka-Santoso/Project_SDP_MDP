const service = require("./riwayat_peminjaman.service");

const { sendPaginated } = require("../../utils/response");

async function daftar(req, res) {
  const query = req.validated.query;

  const result = await service.daftar(req.user, query);

  return sendPaginated(res, result.items, {
    page: query.page,
    limit: query.limit,
    total: result.total,
  });
}

module.exports = {
  daftar,
};
