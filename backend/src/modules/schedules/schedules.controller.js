const schedulesService = require('./schedules.service');
const changesService = require('./schedules.changes.service');
const importService = require('./schedules.import.service');
const viewService = require('./schedules.view.service');
const tasksService = require('./schedules.tasks.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function listSlots(req, res) {
  const query = req.validated.query;
  const { items, total } = await schedulesService.listSlots(query);
  sendPaginated(res, items, { page: query.page, limit: query.limit, total });
}

async function getSlot(req, res) {
  sendSuccess(res, await schedulesService.getSlot(req.validated.params.id));
}

async function createSlot(req, res) {
  sendSuccess(res, await schedulesService.createSlot(req.validated.body), 201);
}

async function updateSlot(req, res) {
  sendSuccess(res, await schedulesService.updateSlot(req.validated.params.id, req.validated.body));
}

async function deleteSlot(req, res) {
  await schedulesService.deleteSlot(req.validated.params.id);
  res.status(204).end();
}

async function listChanges(req, res) {
  const query = req.validated.query;
  const { items, total } = await changesService.listChanges(query);
  sendPaginated(res, items, { page: query.page, limit: query.limit, total });
}

async function getChange(req, res) {
  sendSuccess(res, await changesService.getChange(req.validated.params.id));
}

// 201 jika perubahan baru, 200 jika memperbarui perubahan di tanggal yang sama.
async function saveChange(req, res) {
  const { change, created } = await changesService.saveChange(req.validated.body);
  sendSuccess(res, change, created ? 201 : 200);
}

async function deleteChange(req, res) {
  await changesService.deleteChange(req.validated.params.id);
  res.status(204).end();
}

async function importSlots(req, res) {
  const { semesterId } = req.validated.body;
  sendSuccess(res, await importService.importSlots({ buffer: req.file.buffer, semesterId }), 201);
}

// F-JDW-05. Jadwal sesuai role beserta perubahannya pada rentang tanggal.
async function viewSchedule(req, res) {
  sendSuccess(res, await viewService.viewSchedule(req.user, req.validated.query));
}

// F-JDW-08 (guru) dan F-JDW-09 (siswa).
async function listTasks(req, res) {
  const query = req.validated.query;
  const { items, total } = await tasksService.listTasks(req.user, query);
  sendPaginated(res, items, { page: query.page, limit: query.limit, total });
}

async function getTask(req, res) {
  sendSuccess(res, await tasksService.getTask(req.user, req.validated.params.id));
}

async function createTask(req, res) {
  sendSuccess(res, await tasksService.createTask(req.user, req.validated.body), 201);
}

async function updateTask(req, res) {
  sendSuccess(res, await tasksService.updateTask(req.user, req.validated.params.id, req.validated.body));
}

async function deleteTask(req, res) {
  await tasksService.deleteTask(req.user, req.validated.params.id);
  res.status(204).end();
}

async function listMyTeachingAssignments(req, res) {
  sendSuccess(res, await tasksService.listMyTeachingAssignments(req.user));
}

module.exports = {
  listMyTeachingAssignments,
  listTasks,
  getTask,
  createTask,
  updateTask,
  deleteTask,
  viewSchedule,
  listSlots,
  getSlot,
  createSlot,
  updateSlot,
  deleteSlot,
  listChanges,
  getChange,
  saveChange,
  deleteChange,
  importSlots,
};
