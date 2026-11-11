const billsService = require('./bills.service');
const importService = require('./bills.import.service');
const { sendSuccess, sendPaginated } = require('../../utils/response');

async function listStudentBills(req, res) {
  const { studentId } = req.validated.params;
  const query = req.validated.query;
  const { items, total } = await billsService.listStudentBills(req.user, studentId, query);
  sendPaginated(res, items, { page: query.page, limit: query.limit, total });
}

async function listBills(req, res) {
  const query = req.validated.query;
  const { items, total } = await billsService.listBills(req.user, query);
  sendPaginated(res, items, { page: query.page, limit: query.limit, total });
}

async function importBills(req, res) {
  sendSuccess(res, await importService.importBills({ buffer: req.file.buffer }), 201);
}

module.exports = { listStudentBills, listBills, importBills };
