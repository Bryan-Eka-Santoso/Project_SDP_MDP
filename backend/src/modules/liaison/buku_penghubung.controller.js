const service = require("./buku_penghubung.service");
const { sendSuccess } = require("../../utils/response");

async function daftarKelas(req, res) {
  const result = await service.daftarKelas(req.user.id);

  return sendSuccess(res, result);
}

async function daftarSiswa(req, res) {
  const result = await service.daftarSiswa(
    req.user.id,
    req.validated.params.classId,
  );

  return sendSuccess(res, result);
}

async function simpan(req, res) {
  const result = await service.simpan(req.user.id, req.validated.body);

  return sendSuccess(res, result, 201);
}

module.exports = {
  daftarKelas,
  daftarSiswa,
  simpan,
};
