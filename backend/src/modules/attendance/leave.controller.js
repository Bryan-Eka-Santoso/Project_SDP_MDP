const service = require('./leave.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');
async function children(req, res) {
  sendSuccess(res, await service.children(req.user.id));
}
async function list(req, res) {
  const result = await service.list(req.user.id, req.validated.query);
  sendPaginated(res, result.data, result.meta);
}
async function create(req, res) {
  sendSuccess(res, await service.create(req.user.id, req.validated.body, req.file), 201);
}

async function downloadAttachment(req, res) {
  const info = await service.findAttachment(req.user.id, Number(req.validated.params.id));
  res.type(info.contentType);
  res.set('Content-Disposition', 'inline');
  res.sendFile(info.path, (err) => {
    if (err && !res.headersSent) res.status(err.statusCode || 404).end();
  });
}
module.exports = { children, list, create, downloadAttachment };
