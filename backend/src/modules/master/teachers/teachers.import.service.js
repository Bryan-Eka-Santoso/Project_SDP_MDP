// Impor data guru dari Excel (F-MST-05).
// Semua baris divalidasi lebih dulu; jika ada satu saja kesalahan, tidak ada data yang disimpan.
const prisma = require('../../../lib/prisma');
const { readSheet, importInvalid } = require('../../../lib/excel');
const {
  readTextCell,
  validateEmailCell,
  validatePasswordCell,
  checkUniqueness,
  hashBatchPasswords,
} = require('../master.import.helper');

// Nama header sama dengan docs/import-templates/guru.xlsx.
const COLUMNS = [
  { key: 'name', header: 'Nama' },
  { key: 'employeeNumber', header: 'NIP' },
  { key: 'email', header: 'Email' },
  { key: 'password', header: 'Password', optional: true },
];
const HEADER = Object.fromEntries(COLUMNS.map(({ key, header }) => [key, header]));

function parseRows(rows, errors) {
  return rows.map(({ row, values }) => {
    const name = readTextCell(values.name, HEADER.name, { required: true, maxLength: 100, row, errors });
    const employeeNumber = readTextCell(values.employeeNumber, HEADER.employeeNumber, {
      required: true,
      maxLength: 30,
      row,
      errors,
    });
    const email = validateEmailCell(values.email, HEADER.email, { row, errors });
    const password = validatePasswordCell(values.password, HEADER.password, { row, errors });

    return { row, name, employeeNumber, email, password };
  });
}

async function validateImport(buffer) {
  const rows = await readSheet(buffer, { columns: COLUMNS });
  const errors = [];
  const parsedRows = parseRows(rows, errors);

  const employeeNumbers = [...new Set(parsedRows.map((item) => item.employeeNumber).filter(Boolean))];
  const emailValues = [...new Set(parsedRows.map((item) => item.email).filter(Boolean))];

  const [existingTeachers, existingUsers] = await Promise.all([
    employeeNumbers.length > 0
      ? prisma.teacher.findMany({
          where: { employeeNumber: { in: employeeNumbers } },
          select: { employeeNumber: true },
        })
      : [],
    emailValues.length > 0
      ? prisma.user.findMany({ where: { email: { in: emailValues } }, select: { email: true } })
      : [],
  ]);

  checkUniqueness(parsedRows, {
    valueOf: (item) => item.employeeNumber,
    column: HEADER.employeeNumber,
    label: 'NIP',
    existingKeys: new Set(existingTeachers.map((t) => t.employeeNumber.toLowerCase())),
    errors,
  });
  checkUniqueness(parsedRows, {
    valueOf: (item) => item.email,
    column: HEADER.email,
    label: 'Email',
    existingKeys: new Set(existingUsers.map((u) => u.email.toLowerCase())),
    errors,
  });

  if (errors.length > 0) throw importInvalid(errors);
  return parsedRows;
}

async function importTeachers({ buffer }) {
  const candidates = await validateImport(buffer);
  const hashes = await hashBatchPasswords(candidates.map((item) => item.password));

  await prisma.$transaction(
    async (tx) => {
      for (const candidate of candidates) {
        const user = await tx.user.create({
          data: {
            name: candidate.name,
            email: candidate.email,
            passwordHash: hashes.get(candidate.password),
            role: 'TEACHER',
            status: 'ACTIVE',
          },
        });
        await tx.teacher.create({
          data: {
            userId: user.id,
            employeeNumber: candidate.employeeNumber,
            isHomeroomTeacher: false,
          },
        });
      }
    },
    { timeout: 30000 },
  );

  return { imported: candidates.length };
}

module.exports = { importTeachers, COLUMNS };
