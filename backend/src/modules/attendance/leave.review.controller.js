const service = require('./leave.review.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function pending(req, res) {
  const { data, meta } = await service.pending(req.user.id, req.validated.query);
  return sendPaginated(res, data, meta);
}
async function decide(req, res) {
  return sendSuccess(res, await service.decide(req.user.id,
    req.validated.params.requestId, req.validated.body.decision));
}
module.exports = { pending, decide };
