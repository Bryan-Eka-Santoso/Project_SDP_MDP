const service = require("./pelanggaran.service");

const { sendSuccess } = require("../../utils/response");

async function daftarSiswa(req, res) {
  const result = await service.daftarSiswa(req.user.id);
  return sendSuccess(res, result);
}

async function daftarJenis(req, res) {
  const result = await service.daftarJenis();
  return sendSuccess(res, result);
}

async function simpan(req, res) {
  const result = await service.simpan(req.user.id, req.validated.body);

  return sendSuccess(res, result, 201);
}

module.exports = {
  daftarSiswa,
  daftarJenis,
  simpan,
};
