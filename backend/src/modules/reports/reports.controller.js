const reportsService = require('./reports.service');
const { sendPaginated } = require('../../utils/response');

const XLSX_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

async function getReport(req, res) {
  const query = req.validated.query;
  const { items, total } = await reportsService.getReport(req.params.type, query);
  sendPaginated(res, items, { page: query.page, limit: query.limit, total });
}

// F-LAP-11. Klien Android menyimpan berkas lewat CreateDocument.
async function exportReport(req, res) {
  const { filename, buffer } = await reportsService.exportReport(req.params.type, req.validated.query);
  res.set({
    'Content-Type': XLSX_TYPE,
    'Content-Disposition': `attachment; filename="${filename}"`,
    'Content-Length': buffer.length,
  });
  res.end(buffer);
}

module.exports = { getReport, exportReport };
