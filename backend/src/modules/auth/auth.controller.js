const authService = require('./auth.service');
const { sendSuccess } = require('../../utils/response');

async function login(req, res) {
  sendSuccess(res, await authService.login(req.validated.body));
}

async function refresh(req, res) {
  sendSuccess(res, await authService.refresh(req.validated.body));
}

async function logout(req, res) {
  await authService.logout(req.validated.body);
  sendSuccess(res, null);
}

async function me(req, res) {
  sendSuccess(res, await authService.getProfile(req.user.id));
}

async function changePassword(req, res) {
  sendSuccess(res, await authService.changePassword(req.user.id, req.validated.body));
}

module.exports = { login, refresh, logout, me, changePassword };
