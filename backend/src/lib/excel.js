// Helper baca file Excel untuk semua impor (docs/BUSINESS_RULES.md 2.3). Modul cukup menyebutkan
// kolomnya; pembacaan sel, pelaporan kesalahan, dan batas ukuran dibuat sama untuk semua impor.
const ExcelJS = require('exceljs');
const AppError = require('../utils/AppError');

// Batas baris data per file. Daftar kesalahan juga dibatasi agar respons tetap kecil saat hampir
// semua baris salah (mis. file yang sama diunggah dua kali).
const MAX_ROWS = 5000;
const MAX_REPORTED_ERRORS = 200;

// Lembar tambahan yang ada di template resmi dan berkas contoh (petunjuk/penjelasan). Isinya tidak
// dibaca; lembar lain di luar daftar ini ditolak.
const INFO_SHEETS = new Set(['petunjuk', 'tentang berkas ini']);

const collapseSpaces = (text) => text.replace(/\s+/g, ' ').trim();

// 1 -> A, 27 -> AA (huruf kolom seperti yang dilihat pengguna di Excel).
function columnLetter(number) {
  let letters = '';
  for (let n = number; n > 0; n = Math.floor((n - 1) / 26)) {
    letters = String.fromCharCode(65 + ((n - 1) % 26)) + letters;
  }
  return letters;
}

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

// Periksa struktur file terhadap template: lembar tambahan, header (tidak teks, ganda, tidak dikenal,
// hilang). Semua temuan dikumpulkan sekaligus agar pengguna bisa memperbaikinya dalam satu kali coba.
// Mengembalikan `{ errors, positions, headerColumns }`.
function inspectStructure(workbook, sheet, columns) {
  const errors = [];

  for (const extra of workbook.worksheets.slice(1)) {
    if (INFO_SHEETS.has(collapseSpaces(extra.name).toLowerCase())) continue;
    errors.push({
      row: 1,
      column: null,
      message: `Lembar "${extra.name}" tidak ada di template. Hapus lembar itu; file hanya boleh berisi lembar data dan lembar petunjuk.`,
    });
  }

  const headerColumns = new Set(); // semua kolom yang punya isi di baris 1
  const found = new Map(); // header ternormalisasi -> { label, columnNumbers }
  sheet.getRow(1).eachCell({ includeEmpty: false }, (cell, columnNumber) => {
    const header = plainValue(cell.value);
    if (header === null) return;
    headerColumns.add(columnNumber);
    if (typeof header !== 'string') {
      errors.push({ row: 1, column: null, message: `Header di kolom ${columnLetter(columnNumber)} bukan teks.` });
      return;
    }
    const key = header.toLowerCase();
    if (!found.has(key)) found.set(key, { label: header, columnNumbers: [] });
    found.get(key).columnNumbers.push(columnNumber);
  });

  const expected = new Set(columns.map((column) => collapseSpaces(column.header).toLowerCase()));
  for (const [key, { label, columnNumbers }] of found) {
    const letters = columnNumbers.map(columnLetter);
    if (columnNumbers.length > 1) {
      errors.push({
        row: 1,
        column: label,
        message: `Header "${label}" muncul lebih dari sekali (kolom ${letters.join(' dan ')}). Setiap kolom hanya boleh satu.`,
      });
    } else if (!expected.has(key)) {
      errors.push({
        row: 1,
        column: label,
        message:
          `Kolom "${label}" (kolom ${letters[0]}) tidak ada di template. Hapus kolom itu atau pakai template terbaru. ` +
          `Kolom yang dikenal: ${columns.map((column) => column.header).join(', ')}.`,
      });
    }
  }

  const positions = columns.map((column) => {
    const hit = found.get(collapseSpaces(column.header).toLowerCase());
    return hit && hit.columnNumbers.length === 1 ? hit.columnNumbers[0] : undefined;
  });
  const foundLabels = [...found.values()].map((item) => item.label);
  columns.forEach((column) => {
    if (column.optional || found.has(collapseSpaces(column.header).toLowerCase())) return;
    const where =
      foundLabels.length > 0
        ? `Header di file: ${foundLabels.join(', ')}.`
        : 'Baris pertama kosong; header harus berada di baris 1.';
    errors.push({
      row: 1,
      column: column.header,
      message: `Kolom "${column.header}" tidak ditemukan di baris pertama (header). ${where}`,
    });
  });

  return { errors, positions, headerColumns };
}

/**
 * Baca lembar pertama file .xlsx; baris 1 adalah header.
 *
 * `columns`: [{ key, header, optional? }]. Kolom dicari lewat teks header (huruf besar/kecil dan
 * spasi berlebih diabaikan); urutan kolom di file bebas. Struktur harus sama dengan template:
 * kolom di luar `columns`, header ganda, header bukan teks, data di kolom tanpa header, dan lembar
 * selain lembar petunjuk ditolak (422 IMPORT_INVALID) dengan pesan yang menyebut penyebabnya. Kolom
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

  const { errors, positions, headerColumns } = inspectStructure(workbook, sheet, columns);
  if (errors.length > 0) throw importInvalid(errors);

  const rows = [];
  const strayColumns = new Map(); // kolom tanpa header yang berisi data -> baris pertama yang berisi
  sheet.eachRow({ includeEmpty: false }, (excelRow, rowNumber) => {
    if (rowNumber === 1) return;
    excelRow.eachCell({ includeEmpty: false }, (cell, columnNumber) => {
      if (headerColumns.has(columnNumber) || strayColumns.has(columnNumber)) return;
      if (plainValue(cell.value) !== null) strayColumns.set(columnNumber, rowNumber);
    });

    const values = {};
    let blank = true;
    columns.forEach((column, index) => {
      const value = positions[index] === undefined ? null : plainValue(excelRow.getCell(positions[index]).value);
      values[column.key] = value;
      if (value !== null) blank = false;
    });
    if (!blank) rows.push({ row: rowNumber, values });
  });

  if (strayColumns.size > 0) {
    throw importInvalid(
      [...strayColumns].map(([columnNumber, rowNumber]) => ({
        row: rowNumber,
        column: null,
        message: `Kolom ${columnLetter(columnNumber)} berisi data tetapi tidak punya header di baris 1. Hapus isinya atau gunakan template yang benar.`,
      })),
    );
  }

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
