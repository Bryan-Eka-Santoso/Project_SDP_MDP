const notificationsService = require('./notifications.service');
const { sendSuccess } = require('../../utils/response');

async function registerDeviceToken(req, res) {
  await notificationsService.registerDeviceToken(req.user.id, req.validated.body.token);
  sendSuccess(res, null, 201);
}

module.exports = { registerDeviceToken };
