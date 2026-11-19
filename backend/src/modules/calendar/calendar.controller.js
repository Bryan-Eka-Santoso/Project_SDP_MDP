const calendarService = require('./calendar.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function listEvents(req, res) {
  const { events, total } = await calendarService.listEvents(req.validated.query);
  const { page, limit } = req.validated.query;
  sendPaginated(res, events, { page, limit, total });
}

async function getEventById(req, res) {
  const result = await calendarService.getEventById(req.validated.params.id);
  sendSuccess(res, result);
}

async function createEvent(req, res) {
  const result = await calendarService.createEvent(req.validated.body);
  sendSuccess(res, result, 201);
}

async function updateEvent(req, res) {
  const result = await calendarService.updateEvent(
    req.validated.params.id,
    req.validated.body,
  );
  sendSuccess(res, result);
}

async function deleteEvent(req, res) {
  await calendarService.deleteEvent(req.validated.params.id);
  res.status(204).send();
}

module.exports = {
  listEvents,
  getEventById,
  createEvent,
  updateEvent,
  deleteEvent,
};
