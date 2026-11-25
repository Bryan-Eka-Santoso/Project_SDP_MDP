const service = require("./pilih_paket.service");
const { sendSuccess } = require("../../utils/response");

async function daftarPaket(req, res) {
  const result = await service.daftarPaket(req.user.id);
  return sendSuccess(res, result);
}

async function pilihPaket(req, res) {
  const result = await service.pilihPaket(
    req.user.id,
    req.validated.body.packageId,
  );

  return sendSuccess(res, result);
}

module.exports = {
  daftarPaket,
  pilihPaket,
};
