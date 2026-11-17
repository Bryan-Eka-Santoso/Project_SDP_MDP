const service = require("./ajukan_peminjaman.service");
const { sendSuccess } = require("../../utils/response");

async function daftarFasilitas(req, res) {
  const result = await service.daftarFasilitas();
  return sendSuccess(res, result);
}

async function ajukan(req, res) {
  const result = await service.ajukan(req.user.id, req.validated.body);

  return sendSuccess(res, result, 201);
}

module.exports = {
  daftarFasilitas,
  ajukan,
};
