const announcementsService = require('./announcements.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function uploadAttachment(req, res) {
  const baseUrl = `${req.protocol}://${req.get('host')}`;
  const result = await announcementsService.uploadAttachment(req.file, { baseUrl });
  sendSuccess(res, result, 201);
}

async function listAnnouncements(req, res) {
  const { announcements, total } = await announcementsService.listAnnouncements(
    req.user,
    req.validated.query,
  );
  const { page, limit } = req.validated.query;
  sendPaginated(res, announcements, { page, limit, total });
}

async function getAnnouncementById(req, res) {
  const result = await announcementsService.getAnnouncementById(
    req.user,
    req.validated.params.id,
  );
  sendSuccess(res, result);
}

async function createAnnouncement(req, res) {
  const result = await announcementsService.createAnnouncement(req.user, req.validated.body);
  sendSuccess(res, result, 201);
}

async function updateAnnouncement(req, res) {
  const result = await announcementsService.updateAnnouncement(
    req.user,
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function deleteAnnouncement(req, res) {
  await announcementsService.deleteAnnouncement(req.user, req.validated.params.id);
  res.status(204).send();
}

module.exports = {
  uploadAttachment,
  listAnnouncements,
  getAnnouncementById,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
};
