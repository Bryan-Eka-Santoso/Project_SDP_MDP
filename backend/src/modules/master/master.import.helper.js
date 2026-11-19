// Helper validasi dan hashing bersama untuk impor Excel data master (siswa & guru).
const bcrypt = require('bcrypt');
const { z } = require('zod');
const { cellText } = require('../../lib/excel');

const BCRYPT_ROUNDS = 10;
const DEFAULT_IMPORT_PASSWORD = '12345678';
const EMAIL_SCHEMA = z.email();

function readTextCell(value, column, { required = false, maxLength, row, errors }) {
  const cell = cellText(value);
  if (cell.error) {
    errors.push({ row, column, message: cell.error });
    return undefined;
  }
  if (cell.text === null || cell.text === '') {
    if (required) {
      errors.push({ row, column, message: 'Wajib diisi.' });
      return undefined;
    }
    return null;
  }
  if (maxLength && cell.text.length > maxLength) {
    errors.push({ row, column, message: `Maksimal ${maxLength} karakter.` });
    return undefined;
  }
  return cell.text;
}

function validateEmailCell(value, column, { row, errors }) {
  const raw = readTextCell(value, column, { required: true, maxLength: 100, row, errors });
  if (raw === undefined) return undefined;
  const email = raw.toLowerCase();
  if (!EMAIL_SCHEMA.safeParse(email).success) {
    errors.push({ row, column, message: 'Format email tidak valid.' });
    return undefined;
  }
  return email;
}

function validatePasswordCell(value, column, { row, errors }) {
  const raw = readTextCell(value, column, { required: false, maxLength: 100, row, errors });
  if (raw === undefined) return undefined;
  if (raw === null) return DEFAULT_IMPORT_PASSWORD;
  if (raw.length < 8) {
    errors.push({ row, column, message: 'Password minimal 8 karakter.' });
    return undefined;
  }
  return raw;
}

function checkUniqueness(rows, { valueOf, keyOf = (value) => value.toLowerCase(), column, label, existingKeys, errors }) {
  const seenInFile = new Map();
  for (const item of rows) {
    const value = valueOf(item);
    if (!value) continue;
    const key = keyOf(value);
    if (existingKeys.has(key)) {
      errors.push({ row: item.row, column, message: `${label} "${value}" sudah terdaftar.` });
      continue;
    }
    const firstRow = seenInFile.get(key);
    if (firstRow !== undefined) {
      errors.push({
        row: item.row,
        column,
        message: `${label} "${value}" duplikat di file (sudah dipakai di baris ${firstRow}).`,
      });
      continue;
    }
    seenInFile.set(key, item.row);
  }
}

// Password default dipakai massal saat kolom kosong; hash per nilai unik dalam satu batch
// agar impor ratusan baris tidak melewati batas waktu request.
async function hashBatchPasswords(passwords) {
  const unique = [...new Set(passwords)];
  const entries = await Promise.all(
    unique.map(async (password) => [password, await bcrypt.hash(password, BCRYPT_ROUNDS)]),
  );
  return new Map(entries);
}

module.exports = {
  DEFAULT_IMPORT_PASSWORD,
  readTextCell,
  validateEmailCell,
  validatePasswordCell,
  checkUniqueness,
  hashBatchPasswords,
};
