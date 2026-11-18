const service = require("./profil_siswa.service");
const { sendSuccess } = require("../../utils/response");

async function daftarSiswa(req, res) {
  const result = await service.daftarSiswa(req.user.id);

  return sendSuccess(res, result);
}

async function detailSiswa(req, res) {
  const result = await service.detailSiswa(
    req.user.id,
    req.validated.params.studentId,
  );

  return sendSuccess(res, result);
}

module.exports = {
  daftarSiswa,
  detailSiswa,
};
