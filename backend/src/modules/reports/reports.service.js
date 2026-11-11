// Laporan admin (F-LAP-*) dan ekspornya ke Excel (F-LAP-11). Tiap jenis laporan didaftarkan di
// REPORTS dengan filter, kolom ekspor, dan fungsi `load`; ekspor memakai data dan filter yang
// sama dengan tampilan sehingga isi berkas = yang sedang ditampilkan.
const AppError = require('../../utils/AppError');
const { todayLocal } = require('../../utils/date');
const { toPrismaPagination, paginationQuery } = require('../../utils/pagination');
const { writeXlsx } = require('../../lib/excel');
const scheduleChanges = require('./reports.scheduleChanges');
const teachingLoad = require('./reports.teachingLoad');

// Kunci = segmen path `/reports/:type`.
const REPORTS = Object.freeze({
  'schedule-changes': scheduleChanges,
  'teaching-load': teachingLoad,
});

// Ekspor lebih besar dari ini ditolak agar server tidak kehabisan memori; persempit filter.
const MAX_EXPORT_ROWS = 20000;

function findReport(type) {
  if (!Object.hasOwn(REPORTS, type)) {
    throw new AppError(404, 'NOT_FOUND', 'Jenis laporan tidak ditemukan.');
  }
  return REPORTS[type];
}

// Schema query untuk middleware `validate`: tampilan berpaginasi, ekspor tanpa paginasi.
function querySchema(type, mode) {
  const report = findReport(type);
  return report.makeQuery(mode === 'export' ? {} : paginationQuery.shape);
}

async function getReport(type, query) {
  const report = findReport(type);
  const { items, total } = await report.load(query, toPrismaPagination(query));
  return { items, total };
}

async function exportReport(type, query) {
  const report = findReport(type);
  const { items } = await report.load(query, { skip: 0, take: MAX_EXPORT_ROWS + 1 });
  if (items.length > MAX_EXPORT_ROWS) {
    throw new AppError(
      422,
      'EXPORT_TOO_LARGE',
      `Data melebihi ${MAX_EXPORT_ROWS} baris. Persempit filter lalu coba lagi.`,
    );
  }

  const buffer = await writeXlsx({
    sheetName: report.title,
    columns: report.columns,
    rows: items.map(report.toCells),
  });
  return { filename: `laporan-${type}-${todayLocal()}.xlsx`, buffer };
}

module.exports = { getReport, exportReport, querySchema };
