
const service = require("./isi_survei.service");
const { sendSuccess } = require("../../utils/response");

async function list(req, res) {
  return sendSuccess(
    res,
    await service.list(req.user.id)
  );
}

async function detail(req, res) {
  return sendSuccess(
    res,
    await service.detail(
      req.user.id,
      req.validated.params.id
    )
  );
}

async function submit(req, res) {
  return sendSuccess(
    res,
    await service.submit(
      req.user.id,
      req.validated.params.id,
      req.validated.body
    )
  );
}

module.exports = {
  list,
  detail,
  submit
};
