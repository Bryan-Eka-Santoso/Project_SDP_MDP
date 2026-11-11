// Helper baca file Excel untuk semua impor (docs/BUSINESS_RULES.md 2.3). Modul cukup menyebutkan
// kolomnya; pembacaan sel, pelaporan kesalahan, dan batas ukuran dibuat sama untuk semua impor.
const ExcelJS = require('exceljs');
const AppError = require('../utils/AppError');

// Batas baris data per file. Daftar kesalahan juga dibatasi agar respons tetap kecil saat hampir
// semua baris salah (mis. file yang sama diunggah dua kali).
const MAX_ROWS = 5000;
const MAX_REPORTED_ERRORS = 200;

const collapseSpaces = (text) => text.replace(/\s+/g, ' ').trim();

// Nilai sel ExcelJS bisa berupa objek (formula, teks berformat, hyperlink, error). Ubah menjadi
// nilai polos: string (sudah dirapikan) | number | boolean | Date | null.
function plainValue(value) {
  if (value === null || value === undefined) return null;
  if (value instanceof Date) return value;
  if (typeof value === 'object') {
    if ('error' in value) return String(value.error); // mis. "#REF!"
    if ('result' in value) return plainValue(value.result); // formula: pakai hasil hitungnya
    if ('richText' in value) return plainValue(value.richText.map((part) => part.text).join(''));
    if ('text' in value) return plainValue(value.text); // hyperlink
    return null;
  }
  if (typeof value === 'string') {
    const text = collapseSpaces(value);
    return text === '' ? null : text;
  }
  return value;
}

/**
 * Bentuk `422 IMPORT_INVALID` dari daftar `{ row, column, message }` (`column` null jika
 * kesalahannya menyangkut seluruh baris atau file). Dipakai dengan `throw importInvalid(...)`.
 */
function importInvalid(errors) {
  // sort() stabil: urutan kolom dalam satu baris tetap seperti saat dicatat.
  const sorted = [...errors].sort((a, b) => a.row - b.row);
  const shown = sorted.slice(0, MAX_REPORTED_ERRORS);
  const rowCount = new Set(sorted.map((error) => error.row)).size;

  let message = `Impor dibatalkan: ${sorted.length} kesalahan di ${rowCount} baris. Tidak ada data yang disimpan.`;
  if (shown.length < sorted.length) message += ` Hanya ${shown.length} kesalahan pertama yang ditampilkan.`;
  return new AppError(422, 'IMPORT_INVALID', message, shown);
}

/**
 * Baca sel yang seharusnya teks (kode, NIP). Mengembalikan `{ text }` (null jika kosong) atau
 * `{ error }`. Angka utuh sampai 15 digit dijadikan teks; angka yang lebih panjang sudah dibulatkan
 * oleh Excel sehingga tidak bisa dipulihkan (mis. NIP 18 digit yang diketik tanpa format Teks).
 */
function cellText(value) {
  if (value === null || typeof value === 'string') return { text: value };
  if (typeof value === 'number') {
    if (Number.isInteger(value) && Math.abs(value) < 1e15) return { text: String(value) };
    return { error: 'Angka terlalu panjang dan sudah dibulatkan Excel. Ubah format kolom menjadi Teks lalu ketik ulang.' };
  }
  return { error: 'Isi sel harus berupa teks.' };
}

/**
 * Baca lembar pertama file .xlsx; baris 1 adalah header.
 *
 * `columns`: [{ key, header, optional? }]. Kolom dicari lewat teks header (huruf besar/kecil dan
 * spasi berlebih diabaikan); urutan kolom di file bebas dan kolom tambahan diabaikan. Kolom
 * `optional` yang tidak ada di file dianggap kosong (null) pada setiap baris.
 *
 * Mengembalikan [{ row, values }]: `row` = nomor baris di Excel (header = 1), `values[key]` =
 * string | number | boolean | Date | null. Baris yang kosong di semua kolom dilewati.
 */
async function readSheet(buffer, { columns, maxRows = MAX_ROWS }) {
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(buffer);
  } catch {
    throw new AppError(415, 'UNSUPPORTED_FILE_TYPE', 'File tidak dapat dibaca. Gunakan file Excel (.xlsx) yang valid.');
  }

  const sheet = workbook.worksheets[0];
  if (!sheet) throw importInvalid([{ row: 1, column: null, message: 'File tidak memiliki lembar kerja.' }]);

  const columnByHeader = new Map();
  sheet.getRow(1).eachCell({ includeEmpty: false }, (cell, columnNumber) => {
    const header = plainValue(cell.value);
    if (typeof header !== 'string') return;
    const key = header.toLowerCase();
    if (!columnByHeader.has(key)) columnByHeader.set(key, columnNumber);
  });

  const positions = columns.map((column) => columnByHeader.get(collapseSpaces(column.header).toLowerCase()));
  const missing = columns.filter((column, index) => !column.optional && positions[index] === undefined);
  if (missing.length > 0) {
    throw importInvalid(
      missing.map((column) => ({
        row: 1,
        column: column.header,
        message: `Kolom "${column.header}" tidak ditemukan di baris pertama (header).`,
      })),
    );
  }

  const rows = [];
  sheet.eachRow({ includeEmpty: false }, (excelRow, rowNumber) => {
    if (rowNumber === 1) return;
    const values = {};
    let blank = true;
    columns.forEach((column, index) => {
      const value = positions[index] === undefined ? null : plainValue(excelRow.getCell(positions[index]).value);
      values[column.key] = value;
      if (value !== null) blank = false;
    });
    if (!blank) rows.push({ row: rowNumber, values });
  });

  if (rows.length === 0) throw importInvalid([{ row: 2, column: null, message: 'File tidak berisi baris data.' }]);
  if (rows.length > maxRows) {
    throw importInvalid([
      { row: rows[maxRows].row, column: null, message: `Jumlah baris data melebihi batas ${maxRows} baris per file.` },
    ]);
  }
  return rows;
}

/**
 * Tulis satu lembar Excel (ekspor laporan, F-LAP-11) dan kembalikan Buffer .xlsx.
 * `columns` = [{ header, width? }], `rows` = array sel per baris. Sel string selalu ditulis
 * sebagai teks oleh ExcelJS (bukan rumus), jadi isi seperti "=1+1" aman.
 */
async function writeXlsx({ sheetName, columns, rows }) {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet(sheetName);
  sheet.columns = columns.map((column) => ({ header: column.header, width: column.width ?? 18 }));
  sheet.getRow(1).font = { bold: true };
  sheet.views = [{ state: 'frozen', ySplit: 1 }];
  for (const row of rows) sheet.addRow(row);
  return Buffer.from(await workbook.xlsx.writeBuffer());
}

module.exports = { readSheet, importInvalid, cellText, writeXlsx, MAX_ROWS, MAX_REPORTED_ERRORS };
