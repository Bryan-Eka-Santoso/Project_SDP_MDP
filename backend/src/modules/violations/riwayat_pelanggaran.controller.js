const service = require("./riwayat_pelanggaran.service");
const { sendSuccess } = require("../../utils/response");

// GET daftar siswa berdasarkan role.
async function daftarSiswa(req, res) {
  const result = await service.daftarSiswa(req.user);

  return sendSuccess(res, result);
}

// GET akumulasi dan riwayat siswa.
async function getRiwayat(req, res) {
  const result = await service.getRiwayat(
    req.user,
    req.validated.params.studentId,
  );

  return sendSuccess(res, result);
}

module.exports = {
  daftarSiswa,
  getRiwayat,
};
