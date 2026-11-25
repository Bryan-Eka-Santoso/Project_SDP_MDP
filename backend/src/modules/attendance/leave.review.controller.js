const service = require('./leave.review.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function pending(req, res) {
  const { data, meta } = await service.pending(req.user.id, req.validated.query);
  return sendPaginated(res, data, meta);
}
async function decide(req, res) {
  return sendSuccess(res, await service.decide(
    req.user.id,
    req.validated.params.requestId,
    req.validated.body.decision,
    req.validated.body.rejectionReason,
  ));
}
async function downloadAttachment(req, res) {
  const { path, mime } = await service.findAttachmentForHomeroom(
    req.user.id, req.validated.params.requestId
  );
  res.set('Cache-Control', 'private, no-store');
  res.set('X-Content-Type-Options', 'nosniff');
  res.set('Content-Disposition', 'inline');
  res.type(mime);
  res.sendFile(path, (error) => {
    if (error && !res.headersSent) res.status(error.statusCode || 404).end();
  });
}
module.exports = { pending, decide, downloadAttachment };
