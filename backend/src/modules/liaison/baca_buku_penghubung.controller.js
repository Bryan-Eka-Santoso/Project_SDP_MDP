const service = require("./baca_buku_penghubung.service");
const { sendSuccess } = require("../../utils/response");

async function daftarSiswa(req, res) {
  const result = await service.daftarSiswa(req.user);
  return sendSuccess(res, result);
}

async function riwayat(req, res) {
  const studentId = req.validated.params.studentId;

  const result = await service.riwayat(
    req.user,
    studentId
  );

  return sendSuccess(res, result);
}

module.exports = {
  daftarSiswa,
  riwayat
};